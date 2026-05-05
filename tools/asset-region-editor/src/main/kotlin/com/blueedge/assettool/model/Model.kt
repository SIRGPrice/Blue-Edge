/*
 * Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge
 *
 * Licensed under the Blue Edge Custom License 1.0.
 * You may not use this file except in compliance with that license.
 * GitHub may host, cache, display, and facilitate collaboration on this file
 * as required by the GitHub Terms of Service.
 * See the repository root: LICENSE.md
 */
package com.blueedge.assettool.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Category {
  @SerialName("character")     CHARACTER,
  @SerialName("root")          ROOT,
  @SerialName("path")          PATH,
  @SerialName("task_sprite")   TASK_SPRITE,
  @SerialName("enemy")         ENEMY,
  @SerialName("animal")        ANIMAL,
  @SerialName("ui")            UI,
}

/** Character-only: which of the 7 animation strips this PNG represents. */
@Serializable
enum class CharacterSlot {
  @SerialName("idle")   IDLE,
  @SerialName("run")    RUN,
  @SerialName("hoe")    HOE,
  @SerialName("scythe") SCYTHE,
  @SerialName("water")  WATER,
  @SerialName("sword")  SWORD,
  @SerialName("hurt")   HURT,
}

/**
 * Behavioural tag assigned ONLY to TASK_SPRITE entries to let the scene pick the right
 * "work" animation when the character reaches the task. Mirrors the app-side enum under the
 * same @SerialName values so JSON round-trips transparently.
 *   • [PLANT]  → continuous watering animation.
 *   • [SOLID]  → continuous picking / mining animation.
 *   • [NORMAL] → cat orbits around the prop (no stationary work animation).
 */
@Serializable
enum class AssetType {
  @SerialName("plant")  PLANT,
  @SerialName("solid")  SOLID,
  @SerialName("normal") NORMAL,
}

/**
 * One selectable region.
 *
 *  - For simple 1-cell tiles (Path / Root / TaskSprite categories) only (col,row) matter.
 *  - For multi-cell tiles use [colSpan]/[rowSpan].
 *  - For irregular sprites use [bbox] in pixels (wins over col/row on codegen).
 *  - For Characters, [characterId] groups the 7 strips (same id = same character) and
 *    [characterSlot] says which strip; [col]/[row] are ignored and the whole PNG is used.
 */
@Serializable
data class RegionEntry(
  /** Path relative to app/src/main/assets/, e.g. "stocatstic/Tilesets/Nature_Tileset.png". */
  val assetPath: String,
  val category: Category,
  /** Unique identifier inside its category; referenced from Kotlin (e.g. "mailbox"). */
  val id: String,
  /** Human-readable label shown in the in-app gallery (Spanish). */
  val label: String,
  val col: Int = 0,
  val row: Int = 0,
  val colSpan: Int = 1,
  val rowSpan: Int = 1,
  val bbox: Bbox? = null,
  /** Character grouping (only when category = CHARACTER). */
  val characterId: String? = null,
  val characterSlot: CharacterSlot? = null,
  /** Behavioural tag — only relevant for task_sprite. `null` = untagged. */
  val assetType: AssetType? = null,
)

@Serializable
data class Bbox(val x: Int, val y: Int, val w: Int, val h: Int)

@Serializable
data class AssetRegionsFile(
  val version: Int = 1,
  /** Override grid cell size (px) per asset. Default = 16. */
  val cellSizeOverrides: Map<String, Int> = emptyMap(),
  val regions: List<RegionEntry> = emptyList(),
)

