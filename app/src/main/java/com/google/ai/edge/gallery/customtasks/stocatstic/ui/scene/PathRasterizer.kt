/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene

import androidx.compose.ui.geometry.Offset
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Workflow
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.WorkflowNode

/**
 * Converts the logical workflow graph into a set of world-grid cells rendered as
 * dirt-path sprites on the terrain layer.
 *
 * Paths are **exactly one cell wide** and follow an orthogonal L-shape (Manhattan).
 * They always connect the **right face of the source node's bottom-right cell** to the
 * **left face of the target node's bottom-left cell**, regardless of task size — so
 * multi-cell tasks get their trail glued to the bottom row instead of the top-left cell.
 */

/** Packs a (cellX, cellY) pair into a single Long key for use in HashSet<Long>. */
internal fun packCell(x: Int, y: Int): Long =
  (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

/**
 * Rasterizes every edge of every workflow into a set of cells, at cell granularity.
 *
 * @param cellSize world units per cell (1 cell = 1 tile = minimum interaction unit).
 * @param footprintOf resolver that maps a node to its rectangular footprint on the grid.
 *   The path always exits from [exitCellOf] of the source footprint and enters at
 *   [entryCellOf] of the target footprint.
 */
fun rasterizeWorkflowPaths(
  flows: List<Workflow>,
  cellSize: Float,
  footprintOf: (Workflow, WorkflowNode) -> CellRect,
): Set<Long> {
  val cells = HashSet<Long>()
  for (wf in flows) {
    val byId = wf.nodes.associateBy { it.id }
    for (e in wf.edges) {
      val a = byId[e.fromNode] ?: continue
      val b = byId[e.toNode] ?: continue
      val (ax, ay) = exitCellOf(footprintOf(wf, a))
      val (bx, by) = entryCellOf(footprintOf(wf, b))
      rasterizeManhattan(ax, ay, bx, by, cells)
    }
  }
  return cells
}

/**
 * Preview edge (drag) at cell granularity, same Manhattan rule. Uses the source node's
 * bottom-right exit cell; the pointer position is treated as a loose 1-cell target.
 */
fun rasterizePreviewPath(
  fromFlow: Workflow,
  fromNodeId: String,
  pointerWorld: Offset,
  cellSize: Float,
  footprintOf: (Workflow, WorkflowNode) -> CellRect,
): Set<Long> {
  val n = fromFlow.nodes.firstOrNull { it.id == fromNodeId } ?: return emptySet()
  val (ax, ay) = exitCellOf(footprintOf(fromFlow, n))
  val bcx = kotlin.math.floor(pointerWorld.x / cellSize).toInt()
  val bcy = kotlin.math.floor(pointerWorld.y / cellSize).toInt()
  val cells = HashSet<Long>()
  rasterizeManhattan(ax, ay, bcx, bcy, cells)
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

