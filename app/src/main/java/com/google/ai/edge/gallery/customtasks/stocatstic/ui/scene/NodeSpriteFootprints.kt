/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene

import androidx.compose.ui.geometry.Offset
import com.google.ai.edge.gallery.customtasks.stocatstic.data.StocatsticPreferences
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Workflow
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.WorkflowNode
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.GalleryEntry
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.RootCatalog
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.AssetType
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.TaskSpriteCatalog
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.TaskSpriteRegistry

const val NODE_CLEARANCE_CELLS = 3

data class CellRect(
  val left: Int,
  val top: Int,
  val width: Int,
  val height: Int,
) {
  val right: Int get() = left + width - 1
  val bottom: Int get() = top + height - 1

  fun contains(cellX: Int, cellY: Int): Boolean =
    cellX in left..right && cellY in top..bottom

  fun isTooCloseTo(other: CellRect, clearance: Int = NODE_CLEARANCE_CELLS): Boolean = !(
    right + clearance < other.left ||
      other.right + clearance < left ||
      bottom + clearance < other.top ||
      other.bottom + clearance < top
  )

  fun packedCells(): Sequence<Long> = sequence {
    for (y in top..bottom) {
      for (x in left..right) {
        yield(packCell(x, y))
      }
    }
  }
}

fun GalleryEntry.toSpriteEntry(): TaskSpriteRegistry.Entry =
  TaskSpriteRegistry.Entry(
    assetPath = assetPath,
    col = col,
    row = row,
    widthCells = colSpan,
    heightCells = rowSpan,
    // Carry the behavioural tag through so the scene can choose the right work animation.
    assetType = assetType
      ?: com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.defaultAssetTypeFor(id),
  )

fun resolveTaskSpriteEntry(
  capabilityId: String,
  prefs: StocatsticPreferences,
): TaskSpriteRegistry.Entry =
  prefs.taskOverrides[capabilityId]
    ?.let { id -> TaskSpriteCatalog.ENTRIES.firstOrNull { it.id == id }?.toSpriteEntry() }
    ?: TaskSpriteRegistry.get(capabilityId)

fun resolveTaskSpriteAssetType(
  capabilityId: String,
  prefs: StocatsticPreferences,
): AssetType = resolveTaskSpriteEntry(capabilityId, prefs).assetType
  ?: com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.defaultAssetTypeFor(
    prefs.taskOverrides[capabilityId] ?: TaskSpriteRegistry.defaultEntryId(capabilityId)
  )

fun resolveNodeSpriteEntry(
  workflow: Workflow,
  node: WorkflowNode,
  prefs: StocatsticPreferences,
): TaskSpriteRegistry.Entry =
  if (workflow.predecessors(node.id).isEmpty()) {
    RootCatalog.byId(prefs.rootAssetId).toSpriteEntry()
  } else {
    resolveTaskSpriteEntry(node.capabilityId, prefs)
  }

fun resolveCandidateSpriteEntry(
  isRoot: Boolean,
  capabilityId: String,
  prefs: StocatsticPreferences,
): TaskSpriteRegistry.Entry =
  if (isRoot) RootCatalog.byId(prefs.rootAssetId).toSpriteEntry()
  else resolveTaskSpriteEntry(capabilityId, prefs)

fun resolveWorkAssetTypeForNode(
  node: WorkflowNode,
  prefs: StocatsticPreferences,
): AssetType = resolveTaskSpriteAssetType(node.capabilityId, prefs)

fun footprintForCell(
  cellX: Int,
  cellY: Int,
  sprite: TaskSpriteRegistry.Entry,
): CellRect = CellRect(
  left = cellX,
  top = cellY,
  width = sprite.widthCells.coerceAtLeast(1),
  height = sprite.heightCells.coerceAtLeast(1),
)

fun footprintForNode(
  workflow: Workflow,
  node: WorkflowNode,
  prefs: StocatsticPreferences,
  cellSize: Float,
): CellRect {
  val cellX = kotlin.math.floor((workflow.originX + node.x) / cellSize).toInt()
  val cellY = kotlin.math.floor((workflow.originY + node.y) / cellSize).toInt()
  return footprintForCell(cellX, cellY, resolveNodeSpriteEntry(workflow, node, prefs))
}

fun centerBelow(
  rect: CellRect,
  cellSize: Float,
  marginBottom: Float = 28f,
): Offset = Offset(
  x = rect.left * cellSize + (rect.width * cellSize) / 2f,
  y = (rect.top + rect.height) * cellSize + marginBottom,
)

/**
 * The cell through which a path ENTERS a node. By convention every path arrives at the
 * LEFT face of the node's BOTTOM-LEFT cell, so intermediate tasks drawn as wide multi-cell
 * sprites still connect correctly along their bottom row.
 */
fun entryCellOf(rect: CellRect): Pair<Int, Int> = rect.left to rect.bottom

/**
 * The cell from which a path EXITS a node. Mirror of [entryCellOf]: every path leaves from
 * the RIGHT face of the node's BOTTOM-RIGHT cell.
 */
fun exitCellOf(rect: CellRect): Pair<Int, Int> = rect.right to rect.bottom

