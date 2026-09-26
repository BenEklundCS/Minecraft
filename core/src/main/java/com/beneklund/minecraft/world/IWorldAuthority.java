package com.beneklund.minecraft.world;

/**
 * The seam every domain read and write goes through. The server's implementation owns the world;
 * the client's reads its replica and sends writes to the server as edits.
 */
public interface IWorldAuthority extends IWorldView, IWorldMutator {}
