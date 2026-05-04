/*
 * Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge
 *
 * Licensed under the Blue Edge Custom License 1.0.
 * You may not use this file except in compliance with that license.
 * GitHub may host, cache, display, and facilitate collaboration on this file
 * as required by the GitHub Terms of Service.
 * See the repository root: BLUE_EDGE_CUSTOM_LICENSE.md
 */
package com.blueedge.assettool.ui

import com.blueedge.assettool.ui.theme.EditorTheme
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.JViewport

/**
 * Canvas that renders a spritesheet with an integer-scale grid overlay. Supports:
 *  - left click to toggle a cell in the current selection
 *  - middle-drag or right-drag to pan the viewport (when placed in a JScrollPane)
 *  - Ctrl + mouse wheel to change the zoom factor
 *  - hover cell tracked via [onHoverCellChanged] for the status bar
 *  - visual marker for cells that already belong to a saved region (colour-coded by category)
 */
class CanvasPanel : JPanel() {

  private var image: BufferedImage? = null
  /** Cell size in source pixels. */
  var cellSize: Int = 16
    set(value) { field = value.coerceAtLeast(1); revalidateSize(); repaint() }
  /** Integer zoom factor. */
  var zoom: Int = 3
    set(value) {
      val clamped = value.coerceIn(1, 16)
      if (clamped == field) return
      field = clamped
      revalidateSize()
      repaint()
      onZoomChanged?.invoke(clamped)
    }

  /** Currently-selected cells (col,row) for the active asset. */
  private val selection = linkedSetOf<Pair<Int, Int>>()
  /** Cells that already belong to a saved region, keyed by (col,row). */
  private val assigned = mutableMapOf<Pair<Int, Int>, AssignedTag>()
  /** Temporary preview cells (used by dialogs without mutating the real selection). */
  private val previewSelection = linkedSetOf<Pair<Int, Int>>()

  data class AssignedTag(val id: String, val color: Color)

  var onSelectionChanged: ((Set<Pair<Int, Int>>) -> Unit)? = null
  var onHoverCellChanged: ((Pair<Int, Int>?) -> Unit)? = null
  var onZoomChanged: ((Int) -> Unit)? = null

  private var hoverCell: Pair<Int, Int>? = null

  init {
    background = EditorTheme.background
    isOpaque = true

    val ml = object : MouseAdapter() {
      private var panAnchor: java.awt.Point? = null
      private var viewAnchor: java.awt.Point? = null

      override fun mousePressed(e: MouseEvent) {
        if (javax.swing.SwingUtilities.isMiddleMouseButton(e) ||
            javax.swing.SwingUtilities.isRightMouseButton(e)) {
          panAnchor = e.locationOnScreen
          val vp = parentViewport()
          viewAnchor = vp?.viewPosition
          cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR)
          return
        }
        if (!javax.swing.SwingUtilities.isLeftMouseButton(e)) return
        val img = image ?: return
        val cx = e.x / (cellSize * zoom)
        val cy = e.y / (cellSize * zoom)
        val maxCols = img.width / cellSize
        val maxRows = img.height / cellSize
        if (cx !in 0 until maxCols || cy !in 0 until maxRows) return
        val key = cx to cy
        if (!selection.add(key)) selection.remove(key)
        onSelectionChanged?.invoke(selection.toSet())
        repaint()
      }

      override fun mouseReleased(e: MouseEvent) {
        panAnchor = null
        viewAnchor = null
        cursor = java.awt.Cursor.getDefaultCursor()
      }

      override fun mouseDragged(e: MouseEvent) {
        val anchor = panAnchor ?: return
        val vp = parentViewport() ?: return
        val start = viewAnchor ?: vp.viewPosition
        val current = e.locationOnScreen
        val dx = anchor.x - current.x
        val dy = anchor.y - current.y
        val newPos = java.awt.Point(
          (start.x + dx).coerceAtLeast(0).coerceAtMost((vp.view.width - vp.width).coerceAtLeast(0)),
          (start.y + dy).coerceAtLeast(0).coerceAtMost((vp.view.height - vp.height).coerceAtLeast(0)),
        )
        vp.viewPosition = newPos
      }

      override fun mouseMoved(e: MouseEvent) {
        val img = image ?: return
        val cx = e.x / (cellSize * zoom)
        val cy = e.y / (cellSize * zoom)
        val maxCols = img.width / cellSize
        val maxRows = img.height / cellSize
        val cell = if (cx in 0 until maxCols && cy in 0 until maxRows) cx to cy else null
        if (cell != hoverCell) {
          hoverCell = cell
          onHoverCellChanged?.invoke(cell)
          repaint()
        }
      }

      override fun mouseExited(e: MouseEvent) {
        if (hoverCell != null) { hoverCell = null; onHoverCellChanged?.invoke(null); repaint() }
      }
    }
    addMouseListener(ml)
    addMouseMotionListener(ml)

    addMouseWheelListener { e: MouseWheelEvent ->
      if (e.isControlDown) {
        zoom = (zoom + if (e.wheelRotation < 0) 1 else -1).coerceIn(1, 16)
        e.consume()
      } else {
        // Forward to parent scrollpane for regular scrolling.
        parent?.dispatchEvent(javax.swing.SwingUtilities.convertMouseEvent(this, e, parent))
      }
    }
  }

  private fun parentViewport(): JViewport? {
    var c: java.awt.Container? = parent
    while (c != null) { if (c is JViewport) return c; c = c.parent }
    return null
  }

  fun setImage(img: BufferedImage?) {
    this.image = img
    selection.clear()
    onSelectionChanged?.invoke(emptySet())
    revalidateSize()
    repaint()
  }

  fun setAssigned(map: Map<Pair<Int, Int>, AssignedTag>) {
    assigned.clear()
    assigned.putAll(map)
    repaint()
  }

  fun setSelection(cells: Set<Pair<Int, Int>>) {
    selection.clear()
    selection.addAll(cells)
    repaint()
  }

  fun setPreviewSelection(cells: Set<Pair<Int, Int>>) {
    previewSelection.clear()
    previewSelection.addAll(cells)
    repaint()
  }

  fun clearPreviewSelection() {
    if (previewSelection.isEmpty()) return
    previewSelection.clear()
    repaint()
  }

  fun clearSelection() {
    selection.clear()
    onSelectionChanged?.invoke(emptySet())
    repaint()
  }

  fun currentSelection(): Set<Pair<Int, Int>> = selection.toSet()

  private fun revalidateSize() {
    val img = image
    val w = if (img != null) img.width * zoom else 400
    val h = if (img != null) img.height * zoom else 300
    preferredSize = Dimension(w, h)
    revalidate()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val g2 = g as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

    val img = image ?: run {
      g2.color = EditorTheme.onSurfaceDim
      g2.font = EditorTheme.fontBody
      g2.drawString("Selecciona un PNG en el panel de la izquierda.", 20, 40)
      return
    }
    val cs = cellSize * zoom
    val totalW = img.width * zoom
    val totalH = img.height * zoom

    // Subtle checker pattern so transparent pixels in the PNG are visible against the dark bg.
    drawChecker(g2, totalW, totalH)
    g2.drawImage(img, 0, 0, totalW, totalH, null)

    // Grid lines — fainter than before, brighter on multiples of 4 to help line up the eye.
    var x = 0; var col = 0
    while (x <= totalW) {
      g2.color = if (col % 4 == 0) Color(255, 255, 255, 32) else Color(255, 255, 255, 16)
      g2.stroke = BasicStroke(if (col % 4 == 0) 1.2f else 1f)
      g2.drawLine(x, 0, x, totalH); x += cs; col++
    }
    var y = 0; var row = 0
    while (y <= totalH) {
      g2.color = if (row % 4 == 0) Color(255, 255, 255, 32) else Color(255, 255, 255, 16)
      g2.stroke = BasicStroke(if (row % 4 == 0) 1.2f else 1f)
      g2.drawLine(0, y, totalW, y); y += cs; row++
    }

    // Assigned cells (translucent colour + first letters of id).
    g2.font = EditorTheme.fontSmall
    for ((cell, tag) in assigned) {
      val (cx, cy) = cell
      g2.color = Color(tag.color.red, tag.color.green, tag.color.blue, 90)
      g2.fillRect(cx * cs, cy * cs, cs, cs)
      g2.color = Color(tag.color.red, tag.color.green, tag.color.blue, 220)
      g2.stroke = BasicStroke(1f)
      g2.drawRect(cx * cs, cy * cs, cs - 1, cs - 1)
      if (cs >= 28) {
        g2.color = EditorTheme.onSurface
        g2.drawString(tag.id.take(8), cx * cs + 4, cy * cs + 14)
      }
    }

    // Hover cell — soft outline.
    hoverCell?.let { (cx, cy) ->
      g2.color = Color(255, 255, 255, 80)
      g2.stroke = BasicStroke(1f)
      g2.drawRect(cx * cs, cy * cs, cs - 1, cs - 1)
    }

    // Current selection (bright accent outline).
    g2.color = EditorTheme.highlight
    g2.stroke = BasicStroke(2f)
    for ((cx, cy) in selection) {
      g2.drawRect(cx * cs + 1, cy * cs + 1, cs - 2, cs - 2)
    }

    // Temporary preview rectangle (cyan/translucent so it doesn't overwrite the real selection).
    if (previewSelection.isNotEmpty()) {
      g2.color = Color(90, 220, 255, 70)
      for ((cx, cy) in previewSelection) {
        g2.fillRect(cx * cs + 1, cy * cs + 1, cs - 1, cs - 1)
      }
      g2.color = Color(90, 220, 255, 220)
      g2.stroke = BasicStroke(2f)
      for ((cx, cy) in previewSelection) {
        g2.drawRect(cx * cs + 1, cy * cs + 1, cs - 2, cs - 2)
      }
    }

  }

  private fun drawChecker(g2: Graphics2D, w: Int, h: Int) {
    val tile = 8
    val a = EditorTheme.surface
    val b = EditorTheme.surfaceElevated
    var y = 0
    while (y < h) {
      var x = 0
      val odd = (y / tile) and 1
      while (x < w) {
        g2.color = if (((x / tile) and 1) xor odd == 0) a else b
        g2.fillRect(x, y, tile, tile)
        x += tile
      }
      y += tile
    }
  }
}
