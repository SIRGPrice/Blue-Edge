/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Behavioural tag authored in the desktop asset-region editor for TASK_SPRITE entries and
 * consumed by the scene renderer to decide which "work" animation the character plays when it
 * reaches a task:
 *   â€¢ [PLANT]  â†’ continuous watering animation.
 *   â€¢ [SOLID]  â†’ continuous picking/mining animation.
 *   â€¢ [NORMAL] â†’ no stationary animation; the character orbits around the object.
 */
@Serializable
enum class AssetType {
  @SerialName("plant")  PLANT,
  @SerialName("solid")  SOLID,
  @SerialName("normal") NORMAL,
}

/**
 * Catalog of selectable *1-cell* sprites surfaced in the in-app gallery. Every entry maps to a
 * `(assetPath, col, row)` inside a vendor tileset. The gallery lets the user freely re-skin
 * every visual element of the StoCATstic scene: starting mailbox, workflow path overlay,
 * and each task â€” as long as the sprite fits a single world cell.
 */

/** Generic gallery sprite entry. Mirrors [TaskSpriteRegistry.Entry] but carries a user label. */
data class GalleryEntry(
  val id: String,
  val label: String,
  val assetPath: String,
  val col: Int,
  val row: Int,
  val colSpan: Int = 1,
  val rowSpan: Int = 1,
  /**
   * Behavioural tag used by the scene to pick the "work" animation when the character arrives
   * at a task backed by this entry. `null` means "inherit a sensible default from the id"
   * (see [defaultAssetTypeFor]) â€” keeps backwards compatibility with pre-tagged JSON.
   */
  val assetType: AssetType? = null,
)

/**
 * Fallback classification used whenever a [GalleryEntry] is loaded without an explicit
 * [AssetType] (typical for the curated fallback lists or older `asset_regions.json` files).
 * Keeps the scene behaviour deterministic even before assets are retagged in the editor.
 */
fun defaultAssetTypeFor(id: String): AssetType = when {
  id.startsWith("crop_") || id == "pot" || id.startsWith("nat_07") ||
    id.startsWith("nat_08") || id.startsWith("nat_09") || id == "flowers" -> AssetType.PLANT
  id.startsWith("nat_04") || id.startsWith("nat_05") || id.startsWith("nat_06") ||
    id == "rock" || id == "stones" || id == "barrel" || id == "haystack" ||
    id.startsWith("ext_02") || id.startsWith("ext_03") || id.startsWith("bn_") -> AssetType.SOLID
  else -> AssetType.NORMAL
}

/** Resolves the entry's [AssetType], falling back to [defaultAssetTypeFor] when untagged. */
val GalleryEntry.resolvedAssetType: AssetType
  get() = assetType ?: defaultAssetTypeFor(id)

/** Character sheets. Each entry references the full set of animation strips. */
data class CharacterEntry(
  val id: String,
  val label: String,
  val idle: String,
  val run: String,
  val hoe: String,
  val scythe: String,
  val water: String,
  val sword: String,
  val hurt: String,
  /** Column counts per strip â€” all sheets share 4 rows (direction). */
  val idleCols: Int = 5,
  val runCols: Int = 8,
  val hoeCols: Int = 9,
  val scytheCols: Int = 9,
  val waterCols: Int = 9,
  val swordCols: Int = 9,
  val hurtCols: Int = 2,
) {
  /** First frame of idle, cropped as preview. */
  val previewCols = idleCols
}

/** Available characters the user can pick as the runner of every flow. */
object CharacterCatalog {
  private const val ROOT = "stocatstic/Characters"

  val BUNNY = CharacterEntry(
    id = "bunny", label = "Conejo",
    idle = "$ROOT/BUNNY/IDLE/Bunny_Idle.png",
    run  = "$ROOT/BUNNY/RUN/Bunny_Run.png",
    hoe  = "$ROOT/BUNNY/HOE/Bunny_Hoe.png",
    scythe = "$ROOT/BUNNY/SCYTHE/Bunny_Scythe.png",
    water  = "$ROOT/BUNNY/WATERING CAN/Bunny_WateringCan.png",
    sword  = "$ROOT/BUNNY/SWORD/Bunny_Sword.png",
    hurt   = "$ROOT/BUNNY/HURT/Bunny_Hurt.png",
  )
  val DUCK = CharacterEntry(
    id = "duck", label = "Pato",
    idle = "$ROOT/DUCK/IDLE/Duck_Idle.png",
    run  = "$ROOT/DUCK/RUN/Duck_Run.png",
    hoe  = "$ROOT/DUCK/HOE/Duck_Hoe.png",
    scythe = "$ROOT/DUCK/SCYTHE/Duck_Scythe.png",
    water  = "$ROOT/DUCK/WATERING CAN/Duck_WateringCan.png",
    sword  = "$ROOT/DUCK/SWORD/Sword_Duck.png",
    hurt   = "$ROOT/DUCK/HURT/Duck_Hurt.png",
  )
  val LION = CharacterEntry(
    id = "lion", label = "LeÃ³n",
    idle = "$ROOT/LION/IDLE/Idle lion.png",
    run  = "$ROOT/LION/RUN/Run lion.png",
    hoe  = "$ROOT/LION/HOE/Hoe lion.png",
    scythe = "$ROOT/LION/SCYTHE/Scythe lion.png",
    water  = "$ROOT/LION/WATERING CAN/Watering can lion.png",
    sword  = "$ROOT/LION/SWORD/Sword lion.png",
    hurt   = "$ROOT/LION/HURT/Hurt lion.png",
  )
  val MONKEY = CharacterEntry(
    id = "monkey", label = "Mono",
    idle = "$ROOT/MONKEY/IDLE/Idle monkey.png",
    run  = "$ROOT/MONKEY/RUN/Run monkey.png",
    hoe  = "$ROOT/MONKEY/HOE/Monkey_Hoe.png",
    scythe = "$ROOT/MONKEY/SCYTHE/Scythe monkey.png",
    water  = "$ROOT/MONKEY/WATERING CAN/Monkey_WateringCan.png",
    sword  = "$ROOT/MONKEY/SWORD/Sword monkey.png",
    hurt   = "$ROOT/MONKEY/HURT/Hurt monkey.png",
  )

  private val FALLBACK_ALL: List<CharacterEntry> = listOf(BUNNY, DUCK, LION, MONKEY)
  val ALL: List<CharacterEntry> = CharacterCatalogGen.ALL.ifEmpty { FALLBACK_ALL }
  val DEFAULT: CharacterEntry = ALL.firstOrNull() ?: BUNNY
  fun byId(id: String): CharacterEntry = ALL.firstOrNull { it.id == id } ?: DEFAULT
}

/**
 * Path-overlay catalog. ONLY a handful of curated 1-cell tiles that actually look like a
 * walkable trail (leaves, flower petals, stones, sand). The user picks one in the gallery and
 * every path cell of every flow renders with that tile.
 */
object PathCatalog {
  private val FALLBACK_ENTRIES: List<GalleryEntry> = listOf(
    GalleryEntry("leaves",   "Hojas",    SpritePaths.TILE_NATURE,   22, 0),
    GalleryEntry("flowers",  "PÃ©talos",  SpritePaths.TILE_NATURE,    4, 2),
    GalleryEntry("stones",   "Piedras",  SpritePaths.TILE_NATURE,    0, 4),
    GalleryEntry("dirt",     "Tierra",   SpritePaths.TILE_GRASS_AUTOTILE, 16, 2),
    GalleryEntry("sand",     "Arena",    SpritePaths.TILE_GRASS_AUTOTILE, 19, 2),
    GalleryEntry("crop_row", "Surco",    SpritePaths.TILE_CROPS,     0, 0),
  )
  val ENTRIES: List<GalleryEntry> = PathCatalogGen.ENTRIES.ifEmpty { FALLBACK_ENTRIES }
  val DEFAULT: GalleryEntry = ENTRIES.firstOrNull() ?: FALLBACK_ENTRIES.first()
  fun byId(id: String): GalleryEntry = ENTRIES.firstOrNull { it.id == id } ?: DEFAULT
}

/**
 * Catalog of sprites that can mark the ROOT (initial) task of a flow. All 1-cell so they
 * snap to the grid cleanly.
 */
object RootCatalog {
  private val FALLBACK_ENTRIES: List<GalleryEntry> = listOf(
    GalleryEntry("mailbox",  "BuzÃ³n",       SpritePaths.TILE_EXTERIOR, 12, 4),
    GalleryEntry("sign",     "Cartel",      SpritePaths.TILE_EXTERIOR,  4, 4),
    GalleryEntry("lantern",  "Farol",       SpritePaths.TILE_EXTERIOR,  8, 2),
    GalleryEntry("well",     "Pozo",        SpritePaths.TILE_EXTERIOR,  0, 6),
    GalleryEntry("pot",      "Maceta",      SpritePaths.TILE_CROPS,     9, 0),
    GalleryEntry("barrel",   "Barril",      SpritePaths.TILE_EXTERIOR,  4, 6),
    GalleryEntry("haystack", "Paja",        SpritePaths.TILE_BARN,      0, 0),
    GalleryEntry("rock",     "Roca",        SpritePaths.TILE_NATURE,    0, 4),
  )
  val ENTRIES: List<GalleryEntry> = RootCatalogGen.ENTRIES.ifEmpty { FALLBACK_ENTRIES }
  val DEFAULT: GalleryEntry = ENTRIES.firstOrNull() ?: FALLBACK_ENTRIES.first()
  fun byId(id: String): GalleryEntry = ENTRIES.firstOrNull { it.id == id } ?: DEFAULT
}

/**
 * Broad catalog of 1-cell sprites that can be assigned to any task. The user pairs each
 * capabilityId with one of these through the gallery. Kept intentionally large so every new
 * capability can find a fitting visual.
 */
object TaskSpriteCatalog {
  private val FALLBACK_ENTRIES: List<GalleryEntry> = listOf(
    // Crops / plants ---------------------------------------------------------------------------
    GalleryEntry("crop_00", "Brote",          SpritePaths.TILE_CROPS,  0, 5),
    GalleryEntry("crop_01", "Planta",         SpritePaths.TILE_CROPS,  3, 5),
    GalleryEntry("crop_02", "Flor roja",      SpritePaths.TILE_CROPS,  6, 5),
    GalleryEntry("crop_03", "Flor amarilla",  SpritePaths.TILE_CROPS,  9, 5),
    GalleryEntry("crop_04", "Flor azul",      SpritePaths.TILE_CROPS, 12, 5),
    GalleryEntry("crop_05", "Flor violeta",   SpritePaths.TILE_CROPS, 15, 5),
    GalleryEntry("crop_06", "Trigo",          SpritePaths.TILE_CROPS, 18, 5),
    GalleryEntry("crop_07", "MaÃ­z",           SpritePaths.TILE_CROPS, 21, 5),
    GalleryEntry("crop_08", "Tomate",         SpritePaths.TILE_CROPS,  0,11),
    GalleryEntry("crop_09", "Calabaza",       SpritePaths.TILE_CROPS,  3,11),
    GalleryEntry("crop_10", "Zanahoria",      SpritePaths.TILE_CROPS,  6,11),
    GalleryEntry("crop_11", "Patata",         SpritePaths.TILE_CROPS,  9,11),
    GalleryEntry("crop_12", "Cebolla",        SpritePaths.TILE_CROPS, 12,11),
    GalleryEntry("crop_13", "Berenjena",      SpritePaths.TILE_CROPS, 15,11),
    GalleryEntry("crop_14", "Pepino",         SpritePaths.TILE_CROPS, 18,11),
    GalleryEntry("crop_15", "Setas",          SpritePaths.TILE_CROPS, 21,11),
    // Nature small props -----------------------------------------------------------------------
    GalleryEntry("nat_00",  "Arbusto",        SpritePaths.TILE_NATURE, 0, 0),
    GalleryEntry("nat_01",  "Arbusto 2",      SpritePaths.TILE_NATURE, 2, 0),
    GalleryEntry("nat_02",  "Mata",           SpritePaths.TILE_NATURE, 4, 0),
    GalleryEntry("nat_03",  "Matojo",         SpritePaths.TILE_NATURE, 6, 0),
    GalleryEntry("nat_04",  "Tronco",         SpritePaths.TILE_NATURE, 0, 4),
    GalleryEntry("nat_05",  "TocÃ³n",          SpritePaths.TILE_NATURE, 2, 4),
    GalleryEntry("nat_06",  "Rocas",          SpritePaths.TILE_NATURE, 4, 4),
    GalleryEntry("nat_07",  "Flor silvestre", SpritePaths.TILE_NATURE, 0, 2),
    GalleryEntry("nat_08",  "Flor roja",      SpritePaths.TILE_NATURE, 2, 2),
    GalleryEntry("nat_09",  "Flor amarilla",  SpritePaths.TILE_NATURE, 4, 2),
    // Exterior / man-made ----------------------------------------------------------------------
    GalleryEntry("ext_00",  "Farol",          SpritePaths.TILE_EXTERIOR,  8, 2),
    GalleryEntry("ext_01",  "Cartel",         SpritePaths.TILE_EXTERIOR,  4, 4),
    GalleryEntry("ext_02",  "Barril",         SpritePaths.TILE_EXTERIOR,  4, 6),
    GalleryEntry("ext_03",  "Caja",           SpritePaths.TILE_EXTERIOR,  0, 4),
    GalleryEntry("ext_04",  "Pozo",           SpritePaths.TILE_EXTERIOR,  0, 6),
    GalleryEntry("ext_05",  "BuzÃ³n",          SpritePaths.TILE_EXTERIOR, 12, 4),
    GalleryEntry("ext_06",  "Campana",        SpritePaths.TILE_EXTERIOR, 16, 4),
    GalleryEntry("ext_07",  "Tambor",         SpritePaths.TILE_EXTERIOR, 20, 4),
    // House interior ---------------------------------------------------------------------------
    GalleryEntry("hs_00",   "Vela",           SpritePaths.TILE_HOUSE,  0, 0),
    GalleryEntry("hs_01",   "Libro",          SpritePaths.TILE_HOUSE,  2, 0),
    GalleryEntry("hs_02",   "Reloj",          SpritePaths.TILE_HOUSE,  4, 0),
    GalleryEntry("hs_03",   "Cuadro",         SpritePaths.TILE_HOUSE,  6, 0),
    GalleryEntry("hs_04",   "Cofre",          SpritePaths.TILE_HOUSE,  0, 2),
    GalleryEntry("hs_05",   "LÃ¡mpara",        SpritePaths.TILE_HOUSE,  2, 2),
    // Barn -------------------------------------------------------------------------------------
    GalleryEntry("bn_00",   "Heno",           SpritePaths.TILE_BARN,   0, 0),
    GalleryEntry("bn_01",   "BidÃ³n",          SpritePaths.TILE_BARN,   2, 0),
    GalleryEntry("bn_02",   "Saco",           SpritePaths.TILE_BARN,   4, 0),
  )
  val ENTRIES: List<GalleryEntry> = TaskSpriteCatalogGen.ENTRIES.ifEmpty { FALLBACK_ENTRIES }
  val DEFAULT: GalleryEntry = ENTRIES.firstOrNull() ?: FALLBACK_ENTRIES.first()
  fun byId(id: String): GalleryEntry = ENTRIES.firstOrNull { it.id == id } ?: DEFAULT
}

