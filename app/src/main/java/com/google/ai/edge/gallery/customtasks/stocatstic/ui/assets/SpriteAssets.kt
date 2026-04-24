/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads sprite PNGs from /assets/stocatstic/ with nearest-neighbor scaling (pixel-perfect).
 * Safe to call repeatedly — bitmaps are cached per path. Missing assets return null so the
 * caller can fall back to procedural drawing until the paid pack is installed.
 */
object SpriteAssets {
  private val cache = ConcurrentHashMap<String, ImageBitmap>()
  private val missing = ConcurrentHashMap.newKeySet<String>()

  fun load(ctx: Context, assetPath: String): ImageBitmap? {
    if (missing.contains(assetPath)) return null
    cache[assetPath]?.let { return it }
    val loaded = runCatching {
      ctx.assets.open(assetPath).use { stream ->
        val opts = BitmapFactory.Options().apply {
          inScaled = false
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
      }
    }.getOrNull()
    if (loaded != null) cache[assetPath] = loaded else missing.add(assetPath)
    return loaded
  }
}

/**
 * A sprite-sheet with [cols] frames across and [rows] directions down. Layout convention used by
 * Little Dreamyland: row 0 = down, 1 = left, 2 = right, 3 = up; each cell is 48×48.
 */
data class SpriteSheet(
  val image: ImageBitmap,
  val cols: Int,
  val rows: Int = 1,
) {
  val frameW: Int = image.width / cols
  val frameH: Int = image.height / rows
}

/** Draws frame [frame] of direction [dir] from [sheet] centered at [destCenter] with [destSize]. */
fun DrawScope.drawSpriteFrame(
  sheet: SpriteSheet,
  frame: Int,
  dir: Int = 0,
  destCenter: Offset,
  destSize: Size,
  flipX: Boolean = false,
) {
  val f = ((frame % sheet.cols) + sheet.cols) % sheet.cols
  val d = ((dir % sheet.rows) + sheet.rows) % sheet.rows
  val srcOffset = IntOffset(f * sheet.frameW, d * sheet.frameH)
  val srcSize = IntSize(sheet.frameW, sheet.frameH)
  val dstOffset = IntOffset(
    (destCenter.x - destSize.width / 2f).toInt(),
    (destCenter.y - destSize.height / 2f).toInt(),
  )
  val dstSize = IntSize(destSize.width.toInt(), destSize.height.toInt())
  if (flipX) {
    drawContext.canvas.save()
    drawContext.canvas.translate(destCenter.x, 0f)
    drawContext.canvas.scale(-1f, 1f)
    drawContext.canvas.translate(-destCenter.x, 0f)
  }
  drawImage(
    image = sheet.image,
    srcOffset = srcOffset, srcSize = srcSize,
    dstOffset = dstOffset, dstSize = dstSize,
    filterQuality = FilterQuality.None,
  )
  if (flipX) drawContext.canvas.restore()
}

/** Tiles a single cell of [sheet] across the world rectangle (x0,y0)-(x1,y1). */
fun DrawScope.drawTiledBackground(
  sheet: SpriteSheet,
  tileCol: Int,
  tileRow: Int,
  tileSizeWorld: Float,
  x0: Float, y0: Float, x1: Float, y1: Float,
) {
  val srcOffset = IntOffset(tileCol * sheet.frameW, tileRow * sheet.frameH)
  val srcSize = IntSize(sheet.frameW, sheet.frameH)
  val dstSize = IntSize(tileSizeWorld.toInt(), tileSizeWorld.toInt())
  val x0a = kotlin.math.floor(x0 / tileSizeWorld) * tileSizeWorld
  val y0a = kotlin.math.floor(y0 / tileSizeWorld) * tileSizeWorld
  var y = y0a
  while (y < y1) {
    var x = x0a
    while (x < x1) {
      drawImage(
        image = sheet.image,
        srcOffset = srcOffset, srcSize = srcSize,
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = dstSize,
        filterQuality = FilterQuality.None,
      )
      x += tileSizeWorld
    }
    y += tileSizeWorld
  }
}

/**
 * Asset paths bundled under `app/src/main/assets/stocatstic/`.
 *
 * ⚠️ Android assets are case-sensitive at runtime: these strings MUST match the actual
 * folder and file casing on disk (PascalCase / UPPERCASE after the vendor restructured the
 * pack). Update here if files are renamed.
 */
object SpritePaths {
  private const val ROOT = "stocatstic"

  // --- Tilesets -------------------------------------------------------------------------------
  /** Autotile for dirt paths only (NOT used as the flat grass anymore). */
  const val TILE_GRASS_AUTOTILE = "$ROOT/Tilesets/Autotile_Grass_and_Dirt_Path_Tileset.png"
  /** Flat, efficient grass tileset — source of the continuous ground layer. */
  const val TILE_FLOOR_DETAIL   = "$ROOT/Tilesets/Tileset_Floor_Detail.png"
  const val TILE_NATURE         = "$ROOT/Tilesets/Nature_Tileset.png"
  const val TILE_EXTERIOR       = "$ROOT/Tilesets/Exterior_Tileset.png"
  const val TILE_BARN           = "$ROOT/Tilesets/Barn_Tileset.png"
  const val TILE_CROPS          = "$ROOT/Tilesets/Crops_Tileset.png"
  const val TILE_HOUSE          = "$ROOT/Tilesets/House_Tileset.png"
  const val TILE_UI             = "$ROOT/Tilesets/UI_Tileset.png"

  // --- Object animations (single-row strips, 16×16 frames) ------------------------------------
  const val OBJ_CAMPFIRE = "$ROOT/Object Animation/Campfire.png"
  const val OBJ_FOUNTAIN = "$ROOT/Object Animation/Fountain.png"
  const val OBJ_CHEST    = "$ROOT/Object Animation/Chest.png"
  const val OBJ_BOAT     = "$ROOT/Object Animation/Boat.png"

  // --- UI -------------------------------------------------------------------------------------
  const val UI_SHEET = "$ROOT/Ui/ui.png"

  // --- Bunny (only character wired in right now; other folders BASE/DUCK/LION/MONKEY and
  //     Animals/ + Enemies/ are available on disk and can be wired later). -------------------
  private const val BUNNY = "$ROOT/Characters/BUNNY"
  const val BUNNY_IDLE   = "$BUNNY/IDLE/Bunny_Idle.png"
  const val BUNNY_RUN    = "$BUNNY/RUN/Bunny_Run.png"
  const val BUNNY_HOE    = "$BUNNY/HOE/Bunny_Hoe.png"
  const val BUNNY_SCYTHE = "$BUNNY/SCYTHE/Bunny_Scythe.png"
  const val BUNNY_WATER  = "$BUNNY/WATERING CAN/Bunny_WateringCan.png"
  const val BUNNY_SWORD  = "$BUNNY/SWORD/Bunny_Sword.png"
  const val BUNNY_HURT   = "$BUNNY/HURT/Bunny_Hurt.png"
  const val BUNNY_SHADOW = "$ROOT/Sprite_Shadow.png"
}

/** Column counts for each bunny sheet (all use 4 directional rows of 48×48 frames). */
object BunnySheets {
  const val ROWS = 4
  const val IDLE_COLS = 5
  const val RUN_COLS = 8
  const val HOE_COLS = 9
  const val SCYTHE_COLS = 9
  const val WATER_COLS = 9
  const val SWORD_COLS = 9
  const val HURT_COLS = 2
}

/**
 * Terrain / path tile coordinates.
 *
 *   • `GRASS_BASE` points at a pure-grass cell inside
 *     `Autotile_Grass_and_Dirt_Path_Tileset.png` (the Little Dreamyland autotile is the actual
 *     grass tileset; `Tileset_Floor_Detail.png` is wooden floor detail and can't be used as
 *     outdoor ground). The base grass is tiled flat across the world — no autotile logic.
 *   • `DIRT_VARIANTS` also live in the autotile sheet and are used by the workflow-path
 *     rasterizer to paint dirt sprites.
 *
 * If the user prefers a different grass variant, only the `GRASS_BASE` coordinates need to
 * change — the renderer picks it up on the next frame.
 */
object GrassAutotile {
  /**
   * Pure-grass tile inside Autotile_Grass_and_Dirt_Path_Tileset.png (Micha 47-tile layout,
   * fully-surrounded body cell). Tweak (col,row) if the vendor re-exports the sheet.
   */
  val GRASS_BASE: Pair<Int, Int> = 1 to 3

  /**
   * Kept for backwards compatibility; the renderer NO LONGER paints dirt below path cells.
   * Only the leaves overlay is drawn on top of the grass background.
   */
  val DIRT_BASE: Pair<Int, Int> = 16 to 2
  val DIRT_VARIANTS: List<Pair<Int, Int>> = listOf(DIRT_BASE)

  /**
   * "Leaves-on-ground" sprite drawn on every path cell — now the ONLY overlay representing
   * the workflow trail (grass shows through, no dirt underneath). Points at a small
   * scattered-leaves tile inside `Nature_Tileset.png`. Tweak (col,row) if you want a
   * different prop.
   */
  val PATH_LEAVES_TILE: Pair<Int, Int> = 22 to 0
  /** Sheet that hosts [PATH_LEAVES_TILE]. */
  const val PATH_LEAVES_ASSET = SpritePaths.TILE_NATURE
}

/**
 * Mailbox sprite used as the visual marker of the FIRST task of every workflow ("buzón").
 * All non-root tasks use small 1-cell flowers/plants from `Crops_Tileset.png` instead.
 * Tweak `MAILBOX_TILE` coordinates if the vendor re-exports the sheet.
 */
object MailboxSprite {
  const val ASSET = SpritePaths.TILE_EXTERIOR
  /** 16×16 sub-tile inside Exterior_Tileset.png. */
  val MAILBOX_TILE: Pair<Int, Int> = 12 to 4
}

/**
 * Catalog of world decorations placed on grass cells. Each entry references a rectangular
 * patch of a tileset measured in CELL units (1 CELL = 2 source 16×16 tiles because
 * `TILE_SIZE = 24` world px → `CELL = 48` world px = 2 tiles). Selection is deterministic per
 * world cell so scrolling back produces the same scene.
 *
 * Decorations never overlap workflow paths or task sprites — the terrain renderer skips any
 * cell flagged in `pathCells` or `nodeCells`.
 */
object DecorationCatalog {
  data class Entry(
    val assetPath: String,
    /** Source column in 16×16 tiles. */
    val col: Int,
    /** Source row in 16×16 tiles. */
    val row: Int,
  )

  /**
   * Minimum distance (in cells, Chebyshev) between any two random decorations, AND between
   * any decoration and the nearest workflow node or path cell. When a workflow moves closer
   * than this many cells to a decoration, the decoration vanishes automatically. This
   * number is also the block size used by the deterministic sampler — decorations are
   * candidate-picked at most once per [MIN_SPACING_CELLS] × [MIN_SPACING_CELLS] block.
   */
  const val MIN_SPACING_CELLS: Int = 10

  /**
   * Pool of small 1-cell decorations (plants, flowers, logs). NO multi-cell sprites, NO
   * animated strips — everything occupies exactly one CELL so it always quantises cleanly
   * to the world grid. Coordinates point at the `Nature_Tileset.png` 16×16 sub-grid;
   * tweak a pair to swap a prop.
   */
  val ENTRIES: List<Entry> = listOf(
    // Small plants / bushes (row 0–2 of the nature sheet).
    Entry(SpritePaths.TILE_NATURE, col = 0,  row = 0),
    Entry(SpritePaths.TILE_NATURE, col = 2,  row = 0),
    Entry(SpritePaths.TILE_NATURE, col = 4,  row = 0),
    Entry(SpritePaths.TILE_NATURE, col = 6,  row = 0),
    // Flowers (row 2).
    Entry(SpritePaths.TILE_NATURE, col = 0,  row = 2),
    Entry(SpritePaths.TILE_NATURE, col = 2,  row = 2),
    Entry(SpritePaths.TILE_NATURE, col = 4,  row = 2),
    // Logs / stumps (row 4–5).
    Entry(SpritePaths.TILE_NATURE, col = 0,  row = 4),
    Entry(SpritePaths.TILE_NATURE, col = 2,  row = 4),
    Entry(SpritePaths.TILE_NATURE, col = 4,  row = 4),
  )
}
