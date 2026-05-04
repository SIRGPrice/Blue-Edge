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
package com.blueedge.assettool.ui.components

import com.blueedge.assettool.ui.theme.EditorTheme
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

/**
 * Flat card container. A rounded panel with [EditorTheme.surface] background, subtle border,
 * and optional section title rendered above the content.
 */
class Card(title: String? = null, content: JPanel) : JPanel(BorderLayout()) {
  init {
    background = EditorTheme.surface
    border = BorderFactory.createCompoundBorder(
      RoundedLineBorder(EditorTheme.border, 10),
      BorderFactory.createEmptyBorder(12, 14, 12, 14),
    )
    if (title != null) {
      val header = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
        add(JLabel(title).apply {
          font = EditorTheme.fontSubtitle
          foreground = EditorTheme.onSurfaceMuted
        })
      }
      add(header, BorderLayout.NORTH)
    }
    content.isOpaque = false
    add(content, BorderLayout.CENTER)
  }
}

/** A vertical stack of components with consistent spacing. */
class VStack(gap: Int = 8) : JPanel() {
  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
    putClientProperty("VSTACK_GAP", gap)
  }
  fun addItem(c: Component) {
    val gap = getClientProperty("VSTACK_GAP") as Int
    if (componentCount > 0) add(Box.createVerticalStrut(gap))
    if (c is javax.swing.JComponent) c.alignmentX = LEFT_ALIGNMENT
    add(c)
  }
}

/** Horizontal row with spacing. */
class HStack(gap: Int = 6) : JPanel(FlowLayout(FlowLayout.LEFT, gap, 0)) {
  init { isOpaque = false }
}

/** Subtle vertical divider used between toolbar groups. */
fun vSeparator(): JSeparator = JSeparator(SwingConstants.VERTICAL).apply {
  preferredSize = Dimension(1, 22)
  foreground = EditorTheme.border
}

/** Primary accent button (high-contrast, filled). */
fun primaryButton(text: String, onClick: () -> Unit): JButton = JButton(text).apply {
  font = EditorTheme.fontBody.deriveFont(Font.BOLD)
  isFocusPainted = false
  putClientProperty("JButton.buttonType", "roundRect")
  background = EditorTheme.accent
  foreground = Color.WHITE
  addActionListener { onClick() }
}

/** Secondary outlined button for less prominent actions. */
fun secondaryButton(text: String, onClick: () -> Unit): JButton = JButton(text).apply {
  font = EditorTheme.fontBody
  isFocusPainted = false
  putClientProperty("JButton.buttonType", "roundRect")
  putClientProperty("JButton.outlineColor", EditorTheme.borderStrong)
  foreground = EditorTheme.onSurface
  addActionListener { onClick() }
}

/** Subtle "ghost" button without background (used for toolbar icons). */
fun ghostButton(text: String, onClick: () -> Unit): JButton = JButton(text).apply {
  font = EditorTheme.fontBody
  isFocusPainted = false
  isContentAreaFilled = false
  isBorderPainted = false
  isOpaque = false
  foreground = EditorTheme.onSurface
  putClientProperty("JButton.buttonType", "borderless")
  addActionListener { onClick() }
}

/**
 * Rounded 1-px border. FlatLaf ships one but this one respects our exact radius and draws on
 * HiDPI without artefacts.
 */
class RoundedLineBorder(
  private val lineColor: Color,
  private val radius: Int,
) : javax.swing.border.AbstractBorder() {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.color = lineColor
    g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius)
    g2.dispose()
  }
  override fun getBorderInsets(c: Component) = java.awt.Insets(1, 1, 1, 1)
  override fun isBorderOpaque() = false
}

/** Small pill used for category tags next to region rows. */
class CategoryPill(label: String, accent: Color) : JLabel(label.uppercase()) {
  init {
    font = EditorTheme.fontSmall.deriveFont(Font.BOLD)
    foreground = accent
    background = Color(accent.red, accent.green, accent.blue, 36)
    isOpaque = false
    border = BorderFactory.createCompoundBorder(
      RoundedLineBorder(Color(accent.red, accent.green, accent.blue, 120), 999),
      BorderFactory.createEmptyBorder(2, 8, 2, 8),
    )
    putClientProperty("CategoryPill.fill", background)
  }
  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.color = getClientProperty("CategoryPill.fill") as Color
    g2.fillRoundRect(0, 0, width - 1, height - 1, height, height)
    g2.dispose()
    super.paintComponent(g)
  }
}

