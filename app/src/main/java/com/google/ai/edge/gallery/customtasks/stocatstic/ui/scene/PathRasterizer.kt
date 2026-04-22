/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene

import androidx.compose.ui.geometry.Offset
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Workflow

/**
 * Converts the logical workflow graph into a set of world-grid cells rendered as
 * dirt-path sprites on the terrain layer.
 *
 * Paths are **exactly one cell wide** and follow an orthogonal L-shape (Manhattan)
 * from the source node cell to the destination node cell: first horizontal, then
 * vertical. This yields clean, readable trails that look hand-authored rather than
 * the previous dilated Bézier curves.
 */

/** Packs a (cellX, cellY) pair into a single Long key for use in HashSet<Long>. */
internal fun packCell(x: Int, y: Int): Long =
  (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

/**
 * Rasterizes every edge of every workflow into a set of cells, at cell granularity.
 *
 * @param cellSize world units per cell (1 cell = 1 tile = minimum interaction unit).
 * @param nodeW node width in world units (kept for signature compatibility; unused).
 * @param nodeH node height in world units (kept for signature compatibility; unused).
 */
fun rasterizeWorkflowPaths(
  flows: List<Workflow>,
  cellSize: Float,
  @Suppress("UNUSED_PARAMETER") nodeW: Float = cellSize,
  @Suppress("UNUSED_PARAMETER") nodeH: Float = cellSize,
): Set<Long> {
  val cells = HashSet<Long>()
  for (wf in flows) {
    val byId = wf.nodes.associateBy { it.id }
    for (e in wf.edges) {
      val a = byId[e.fromNode] ?: continue
      val b = byId[e.toNode] ?: continue
      val acx = kotlin.math.floor((wf.originX + a.x) / cellSize).toInt()
      val acy = kotlin.math.floor((wf.originY + a.y) / cellSize).toInt()
      val bcx = kotlin.math.floor((wf.originX + b.x) / cellSize).toInt()
      val bcy = kotlin.math.floor((wf.originY + b.y) / cellSize).toInt()
      rasterizeManhattan(acx, acy, bcx, bcy, cells)
    }
  }
  return cells
}

/**
 * Preview edge (drag) at cell granularity, same Manhattan rule.
 */
fun rasterizePreviewPath(
  fromFlow: Workflow,
  fromNodeId: String,
  pointerWorld: Offset,
  cellSize: Float,
  @Suppress("UNUSED_PARAMETER") nodeW: Float = cellSize,
  @Suppress("UNUSED_PARAMETER") nodeH: Float = cellSize,
): Set<Long> {
  val n = fromFlow.nodes.firstOrNull { it.id == fromNodeId } ?: return emptySet()
  val acx = kotlin.math.floor((fromFlow.originX + n.x) / cellSize).toInt()
  val acy = kotlin.math.floor((fromFlow.originY + n.y) / cellSize).toInt()
  val bcx = kotlin.math.floor(pointerWorld.x / cellSize).toInt()
  val bcy = kotlin.math.floor(pointerWorld.y / cellSize).toInt()
  val cells = HashSet<Long>()
  rasterizeManhattan(acx, acy, bcx, bcy, cells)
  return cells
}

/**
 * L-shaped cell trail from (ax,ay) to (bx,by): horizontal row on the source's y,
 * then vertical column on the target's x. Both endpoints (source & target cells)
 * are **excluded** from the trail because the task sprites occupy those cells.
 */
private fun rasterizeManhattan(
  ax: Int, ay: Int, bx: Int, by: Int, out: HashSet<Long>,
) {
  if (ax == bx && ay == by) return
  // Horizontal run at y = ay, from ax to bx (exclusive of endpoints).
  val x0 = minOf(ax, bx); val x1 = maxOf(ax, bx)
  for (x in x0..x1) {
    if (x == ax) continue                     // source cell (always)
    if (x == bx && ay == by) continue         // target endpoint if same row
    out.add(packCell(x, ay))
  }
  // Vertical run at x = bx, from ay to by (exclusive of endpoints).
  val y0 = minOf(ay, by); val y1 = maxOf(ay, by)
  for (y in y0..y1) {
    if (bx == ax && y == ay) continue         // source endpoint if same column
    if (y == by) continue                     // target cell (final position)
    out.add(packCell(bx, y))
  }
}

/**
 * Ordered list of cells from `(ax, ay)` to `(bx, by)` following the same L-shape the
 * rasterizer uses (horizontal first, then vertical). Both endpoints are INCLUDED so the
 * [CatActor] can animate a waypoint walk from task → task along the exact dirt trail.
 */
fun manhattanPathCells(ax: Int, ay: Int, bx: Int, by: Int): List<Pair<Int, Int>> {
  if (ax == bx && ay == by) return listOf(ax to ay)
  val out = ArrayList<Pair<Int, Int>>(kotlin.math.abs(bx - ax) + kotlin.math.abs(by - ay) + 1)
  // Horizontal leg at y = ay.
  if (ax <= bx) for (x in ax..bx) out.add(x to ay) else for (x in ax downTo bx) out.add(x to ay)
  // Vertical leg at x = bx (skip the corner we just added).
  if (ay <= by) for (y in (ay + 1)..by) out.add(bx to y)
  else for (y in (ay - 1) downTo by) out.add(bx to y)
  return out
}

