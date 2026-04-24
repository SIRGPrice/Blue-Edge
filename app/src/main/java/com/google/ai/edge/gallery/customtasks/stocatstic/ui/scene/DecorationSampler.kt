/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene

import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.DecorationCatalog

/**
 * Deterministic blue-noise-ish sampler for random world decorations.
 *
 *   â€¢ Divides the infinite plane into [DecorationCatalog.MIN_SPACING_CELLS]-sized blocks.
 *     A single candidate cell is picked inside each block by hashing `(bx, by)` so that,
 *     at most, ONE decoration lives in every block â€” which guarantees any two decorations
 *     are â‰¥ `MIN_SPACING_CELLS` cells apart.
 *   â€¢ A candidate is discarded when any cell inside the same Chebyshev radius belongs to
 *     a workflow node, a workflow path, or has been manually deleted by the user.
 *   â€¢ The chosen [DecorationCatalog.Entry] index is also deterministic per block, so
 *     scrolling back and forth produces the same scene.
 *
 * The function returns `null` when the cell has no decoration (either because the block's
 * candidate lives elsewhere, or because it was filtered out). All APIs are pure and
 * allocation-free beyond the returned `Entry`.
 */
internal object DecorationSampler {

  /** Block size in cells (Chebyshev spacing). */
  val SPACING: Int get() = DecorationCatalog.MIN_SPACING_CELLS

  /**
   * Returns the decoration entry that should be drawn at world cell `(cx, cy)` or `null`
   * when that cell is empty. `forbidden(cx, cy)` must return `true` for any cell that is a
   * workflow node, path, or user-deleted decoration.
   */
  fun sample(
    cx: Int,
    cy: Int,
    forbidden: (Int, Int) -> Boolean,
  ): DecorationCatalog.Entry? {
    val entries = DecorationCatalog.ENTRIES
    if (entries.isEmpty() || SPACING <= 0) return null

    // Which block does this cell belong to?
    val bx = floorDiv(cx, SPACING)
    val by = floorDiv(cy, SPACING)

    val h = hashBlock(bx, by)
    // Candidate cell inside the block (top-left-inclusive coordinates).
    val rx = (h and 0x7FFFFFFF) % SPACING
    val ry = ((h ushr 8) and 0x7FFFFFFF) % SPACING
    if (rx != floorMod(cx, SPACING) || ry != floorMod(cy, SPACING)) return null

    // Reject when any cell inside the Chebyshev spacing radius is forbidden. This enforces
    // both (a) decorations never stand on workflow tiles and (b) a decoration vanishes as
    // soon as any workflow gets within SPACING cells of it.
    val r = SPACING
    for (dy in -r..r) {
      for (dx in -r..r) {
        if (forbidden(cx + dx, cy + dy)) return null
      }
    }
    val idx = ((h ushr 16) and 0x7FFFFFFF) % entries.size
    return entries[idx]
  }

  private fun hashBlock(bx: Int, by: Int): Int {
    var h = bx * 0x27D4EB2D
    h = h xor (by * 0x165667B1)
    h = h xor -0x34A7B
    h = h xor (h ushr 15); h *= 0x85EBCA77.toInt()
    h = h xor (h ushr 13); h *= 0xC2B2AE3D.toInt()
    h = h xor (h ushr 16)
    return h
  }

  private fun floorDiv(a: Int, b: Int): Int {
    val q = a / b
    return if ((a xor b) < 0 && q * b != a) q - 1 else q
  }

  private fun floorMod(a: Int, b: Int): Int {
    val r = a % b
    return if ((r xor b) < 0 && r != 0) r + b else r
  }
}


