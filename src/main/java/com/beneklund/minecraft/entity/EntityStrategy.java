package com.beneklund.minecraft.entity;

// Strategy / AI hook for entity behaviour. One implementation per entity type
// (idle wanderer, hostile, scripted, etc.). Swap at runtime to change how an
// entity acts without touching Entity itself.
public interface EntityStrategy {}
