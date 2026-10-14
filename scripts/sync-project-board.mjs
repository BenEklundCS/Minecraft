#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const GRAPHQL = `
query($owner: String!, $number: Int!, $cursor: String) {
  user(login: $owner) {
    projectV2(number: $number) {
      id number title shortDescription url closed
      items(first: 100, after: $cursor) {
        pageInfo { hasNextPage endCursor }
        nodes {
          id type
          content {
            __typename
            ... on DraftIssue { title body }
            ... on Issue { number title body url state }
            ... on PullRequest { number title body url state }
          }
          fieldValues(first: 100) {
            nodes {
              __typename
              ... on ProjectV2ItemFieldSingleSelectValue {
                name field { ... on ProjectV2FieldCommon { name } }
              }
              ... on ProjectV2ItemFieldTextValue {
                text field { ... on ProjectV2FieldCommon { name } }
              }
              ... on ProjectV2ItemFieldMultiSelectValue {
                value field { ... on ProjectV2FieldCommon { name } }
              }
              ... on ProjectV2ItemFieldLabelValue {
                labels(first: 100) { nodes { name } }
                field { ... on ProjectV2FieldCommon { name } }
              }
              ... on ProjectV2ItemFieldNumberValue {
                number field { ... on ProjectV2FieldCommon { name } }
              }
              ... on ProjectV2ItemFieldDateValue {
                date field { ... on ProjectV2FieldCommon { name } }
              }
              ... on ProjectV2ItemFieldIterationValue {
                title startDate duration field { ... on ProjectV2FieldCommon { name } }
              }
              ... on ProjectV2ItemFieldMilestoneValue {
                milestone { title dueOn }
                field { ... on ProjectV2FieldCommon { name } }
              }
            }
          }
        }
      }
    }
  }
}
`;

const EMPTY_STATUSES = ['BACKLOG', 'OPEN', 'DEVELOPMENT', 'QA', 'DONE'];

function parseArgs(args) {
  const options = {};
  for (let i = 0; i < args.length; i++) {
    const key = args[i];
    if (key === '--stage' || key === '--hook') {
      options[key.slice(2)] = true;
    } else if (key === '--owner' || key === '--project') {
      if (!args[i + 1]) throw new Error(`Missing value for ${key}`);
      options[key.slice(2)] = args[++i];
    } else {
      throw new Error('Usage: node scripts/sync-project-board.mjs [--owner OWNER] [--project NUMBER] [--stage] [--hook]');
    }
  }
  return options;
}

function fieldValue(value) {
  if (value.name !== undefined) return value.name;
  if (value.text !== undefined) return value.text;
  if (value.number !== undefined) return value.number;
  if (value.date !== undefined) return value.date;
  if (value.value !== undefined) return value.value;
  if (value.labels !== undefined) return value.labels.nodes.map(label => label.name);
  if (value.title !== undefined) return { title: value.title, startDate: value.startDate, duration: value.duration };
  if (value.milestone !== undefined) return value.milestone;
  return null;
}

function normalizeItem(node) {
  const content = node.content ?? {};
  const fields = (node.fieldValues?.nodes ?? [])
    .filter(value => value.field?.name)
    .map(value => ({ name: value.field.name, type: value.__typename, value: fieldValue(value) }));
  const status = fields.find(field => field.name === 'Status')?.value ?? 'BACKLOG';
  return {
    id: node.id,
    type: node.type,
    title: content.title ?? '',
    body: content.body ?? null,
    number: content.number ?? null,
    url: content.url ?? null,
    state: content.state ?? null,
    status,
    fields,
  };
}

function ghJson(args, exec = execFileSync) {
  const output = exec('gh', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
  return JSON.parse(output);
}

export function fetchProject(owner, number, exec = execFileSync) {
  if (!owner || !Number.isInteger(number) || number < 1) {
    throw new Error('A GitHub user owner and positive project number are required.');
  }

  let cursor = null;
  let project;
  do {
    const args = ['api', 'graphql', '-f', `query=${GRAPHQL}`, '-F', `owner=${owner}`, '-F', `number=${number}`];
    if (cursor) args.push('-F', `cursor=${cursor}`);
    const response = ghJson(args, exec);
    if (response.errors?.length) throw new Error(response.errors.map(error => error.message).join('\n'));
    const current = response.data?.user?.projectV2;
    if (!current) throw new Error(`GitHub Project ${number} was not found for user ${owner}.`);
    if (!project) project = { ...current, items: [] };
    project.items.push(...current.items.nodes.map(normalizeItem));
    const page = current.items.pageInfo;
    if (page.hasNextPage && !page.endCursor) throw new Error('GitHub returned a paginated project without a cursor.');
    cursor = page.hasNextPage ? page.endCursor : null;
  } while (cursor);

  return project;
}

export function buildSnapshot(project, owner) {
  const statuses = Object.fromEntries(EMPTY_STATUSES.map(status => [status, []]));
  for (const item of project.items) {
    const status = item.status || 'BACKLOG';
    statuses[status] ??= [];
    statuses[status].push({ ...item, status });
  }
  return {
    owner,
    project: {
      id: project.id,
      number: project.number,
      title: project.title,
      shortDescription: project.shortDescription,
      url: project.url,
      closed: project.closed,
    },
    ...statuses,
  };
}

export function syncProjectSnapshot({ root = process.cwd(), owner, number, stage = false, isHook = false, exec = execFileSync } = {}) {
  const projectConfigPath = path.join(root, '.github/project-sync.json');
  const snapshotPath = path.join(root, '.github/project-snapshot.json');
  const config = fs.existsSync(projectConfigPath)
    ? JSON.parse(fs.readFileSync(projectConfigPath, 'utf8'))
    : {};
  const selectedOwner = owner ?? config.owner;
  const selectedNumber = number ?? config.number;

  if (!selectedOwner || selectedNumber == null || !Number.isInteger(Number(selectedNumber)) || Number(selectedNumber) < 1) {
    const message = 'Project sync is not configured; set owner and project number in .github/project-sync.json.';
    if (isHook) {
      console.warn(`[sync-project-board] ${message} Preserving the current snapshot.`);
      return false;
    }
    throw new Error(message);
  }

  let snapshot;
  try {
    snapshot = buildSnapshot(fetchProject(selectedOwner, Number(selectedNumber), exec), selectedOwner);
  } catch (error) {
    if (isHook) {
      console.warn(`[sync-project-board] Could not sync GitHub Project (${error.message}). Preserving the current snapshot.`);
      return false;
    }
    throw error;
  }

  const formatted = `${JSON.stringify(snapshot, null, 2)}\n`;
  const changed = !fs.existsSync(snapshotPath) || fs.readFileSync(snapshotPath, 'utf8') !== formatted;
  if (changed) {
    fs.mkdirSync(path.dirname(snapshotPath), { recursive: true });
    fs.writeFileSync(snapshotPath, formatted, 'utf8');
  }
  if (stage) exec('git', ['add', '.github/project-snapshot.json'], { cwd: root, stdio: 'pipe' });
  return changed;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  try {
    const options = parseArgs(process.argv.slice(2));
    const changed = syncProjectSnapshot({
      root,
      owner: options.owner,
      number: options.project ? Number(options.project) : undefined,
      stage: options.stage,
      isHook: options.hook,
    });
    if (changed) console.log('GitHub Project snapshot updated.');
  } catch (error) {
    console.error(`Failed to sync GitHub Project: ${error.message}`);
    process.exitCode = 1;
  }
}
