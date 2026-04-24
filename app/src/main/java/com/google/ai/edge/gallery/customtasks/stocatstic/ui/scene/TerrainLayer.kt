/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.DecorationCatalog
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.GrassAutotile
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.SpriteSheet

/**
 * Pixel-art terrain layer. One CELL = one tile = the minimum interaction unit.
 *
 *   â€¢ Solid grass-green [fallbackColor] is painted as the ground.
 *   â€¢ Every path cell gets the *same* dirt tile ([GrassAutotile.DIRT_BASE]) plus an overlay
 *     "leaves on the ground" sprite â€” no per-cell variants, so the workflow trail reads
 *     as one consistent strip.
 *   â€¢ Random decorations are sampled via [DecorationSampler] (blue-noise-ish, one per
 *     [DecorationCatalog.MIN_SPACING_CELLS]Ã—MIN_SPACING_CELLS block), skipped near flows or
 *     when the user has manually deleted them.
 */
fun DrawScope.drawContinuousTerrain(
  @Suppress("UNUSED_PARAMETER") floor: SpriteSheet?,
  dirt: SpriteSheet?,
  decorationResolver: (String) -> SpriteSheet?,
  pathCells: Set<Long>,
  nodeCells: Set<Long>,
  deletedDecorations: Set<Long>,
  cellSize: Float,
  @Suppress("UNUSED_PARAMETER") animationFrame: Int,
  x0: Float, y0: Float, x1: Float, y1: Float,
  fallbackColor: Color,
  /** Override for the per-cell path overlay sprite (see `PathCatalog`). */
  pathAsset: String = GrassAutotile.PATH_LEAVES_ASSET,
  pathCol: Int = GrassAutotile.PATH_LEAVES_TILE.first,
  pathRow: Int = GrassAutotile.PATH_LEAVES_TILE.second,
) {
  drawRect(
    color = fallbackColor,
    topLeft = Offset(x0, y0),
    size = Size((x1 - x0).coerceAtLeast(0f), (y1 - y0).coerceAtLeast(0f)),
  )

  val cx0 = kotlin.math.floor(x0 / cellSize).toInt()
  val cy0 = kotlin.math.floor(y0 / cellSize).toInt()
  val cx1 = kotlin.math.ceil(x1 / cellSize).toInt()
  val cy1 = kotlin.math.ceil(y1 / cellSize).toInt()
  val dstSize = IntSize(cellSize.toInt() + 1, cellSize.toInt() + 1)

  // ---- Path cells: ONLY a leaves/user-picked overlay on top of the grass. ---------------
  if (pathCells.isNotEmpty()) {
    val overlay = decorationResolver(pathAsset)
    if (overlay != null) {
      for (cy in cy0 until cy1) {
        for (cx in cx0 until cx1) {
          if (!pathCells.contains(packCell(cx, cy))) continue
          drawImage(
            image = overlay.image,
            srcOffset = IntOffset(pathCol * overlay.frameW, pathRow * overlay.frameH),
            srcSize = IntSize(overlay.frameW, overlay.frameH),
            dstOffset = IntOffset((cx * cellSize).toInt(), (cy * cellSize).toInt()),
            dstSize = dstSize,
            filterQuality = FilterQuality.None,
          )
        }
      }
    }
  }

  // ---- Random decorations. --------------------------------------------------------------
  if (DecorationCatalog.ENTRIES.isEmpty()) return
  val forbidden = { x: Int, y: Int ->
    val k = packCell(x, y)
    nodeCells.contains(k) || pathCells.contains(k)
  }
  for (cy in cy0 until cy1) {
    for (cx in cx0 until cx1) {
      if (deletedDecorations.contains(packCell(cx, cy))) continue
      val entry = DecorationSampler.sample(cx, cy, forbidden) ?: continue
      val sheet = decorationResolver(entry.assetPath) ?: continue
      drawImage(
        image = sheet.image,
        srcOffset = IntOffset(entry.col * sheet.frameW, entry.row * sheet.frameH),
        srcSize = IntSize(sheet.frameW, sheet.frameH),
        dstOffset = IntOffset((cx * cellSize).toInt(), (cy * cellSize).toInt()),
        dstSize = dstSize,
        filterQuality = FilterQuality.None,
      )
    }
  }
}

