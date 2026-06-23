"""
Copies C418 music from your local Minecraft installation into
src/main/resources/music/c418/

Grabs: overworld tracks, menu music, music discs (cat, blocks, chirp, etc.)
You must own Minecraft Java Edition. Run from project root:
    python scripts/copy_c418_music.py
"""

import json
import shutil
import os
from pathlib import Path

MC_ASSETS   = Path(os.environ["APPDATA"]) / ".minecraft" / "assets"
INDEX_FILE  = MC_ASSETS / "indexes" / "3.json"
OBJECTS_DIR = MC_ASSETS / "objects"
DEST_DIR    = Path("src/main/resources/music/c418")

DEST_DIR.mkdir(parents=True, exist_ok=True)

with open(INDEX_FILE) as f:
    index = json.load(f)

copied = 0
for asset_path, meta in index["objects"].items():
    if not ("sounds/music" in asset_path or "sounds/records" in asset_path):
        continue
    hash_val = meta["hash"]
    src = OBJECTS_DIR / hash_val[:2] / hash_val
    # Preserve disc vs background distinction in filename
    if "records/" in asset_path:
        name = "disc_" + Path(asset_path).name
    else:
        name = Path(asset_path).name
    dest = DEST_DIR / name
    if src.exists() and not dest.exists():
        shutil.copy2(src, dest)
        print(f"  {name}")
        copied += 1

print(f"\nCopied {copied} tracks to {DEST_DIR}")
