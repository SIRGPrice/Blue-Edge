/*
 * Design tokens for the asset-region-editor (colours, spacing, typography). Centralised so
 * that every panel, card and button uses the same look.
 *
 * Uses FlatLaf Dark as the Swing base L&F (configured in Main.kt). These tokens layer on top
 * to differentiate sections (surface / surface-elevated / border / accent) and match the
 * palette used by the StoCATstic scene in the mobile app.
 */
package com.blueedge.assettool.ui.theme

import java.awt.Color
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.border.Border

object EditorTheme {
  // --- Surfaces (dark-first) ---------------------------------------------------------------
  val background     = Color(0x0F, 0x11, 0x15)
  val surface        = Color(0x16, 0x1A, 0x21)
  val surfaceElevated = Color(0x1E, 0x23, 0x2C)
  val surfaceHover   = Color(0x25, 0x2B, 0x37)
  val border         = Color(0x2A, 0x31, 0x3D)
  val borderStrong   = Color(0x3A, 0x43, 0x52)

  // --- Text --------------------------------------------------------------------------------
  val onSurface      = Color(0xE6, 0xEC, 0xF5)
  val onSurfaceMuted = Color(0x8A, 0x94, 0xA6)
  val onSurfaceDim   = Color(0x5A, 0x63, 0x74)

  // --- Accents -----------------------------------------------------------------------------
  val accent         = Color(0x4C, 0x9A, 0xFF)   // primary — cool blue
  val accentHover    = Color(0x6B, 0xAE, 0xFF)
  val highlight      = Color(0xFF, 0xCE, 0x52)   // selection / gold
  val success        = Color(0x64, 0xD5, 0x89)
  val error          = Color(0xF2, 0x5F, 0x5C)
  val warning        = Color(0xFF, 0xA5, 0x3C)

  // --- Category colours (also used by the canvas overlay) ----------------------------------
  val catCharacter   = Color(0xFF, 0x78, 0xB4)
  val catRoot        = Color(0x64, 0xC8, 0xFF)
  val catPath        = Color(0x78, 0xDC, 0x78)
  val catTaskSprite  = Color(0xFF, 0xC8, 0x64)
  val catEnemy       = Color(0xFF, 0x50, 0x50)
  val catAnimal      = Color(0xDC, 0xA0, 0xFF)
  val catUi          = Color(0xC8, 0xC8, 0xC8)

  // --- Typography --------------------------------------------------------------------------
  private val baseFamily = Font(Font.SANS_SERIF, Font.PLAIN, 13).family
  private val monoFamily = Font(Font.MONOSPACED, Font.PLAIN, 12).family
  val fontTitle    = Font(baseFamily, Font.BOLD, 15)
  val fontSubtitle = Font(baseFamily, Font.BOLD, 13)
  val fontBody     = Font(baseFamily, Font.PLAIN, 13)
  val fontSmall    = Font(baseFamily, Font.PLAIN, 11)
  val fontMono     = Font(monoFamily, Font.PLAIN, 12)

  // --- Shapes ------------------------------------------------------------------------------
  fun cardBorder(): Border = BorderFactory.createCompoundBorder(
    BorderFactory.createLineBorder(border, 1, true),
    BorderFactory.createEmptyBorder(12, 12, 12, 12),
  )

  fun sectionTitleBorder(title: String): Border = BorderFactory.createCompoundBorder(
    BorderFactory.createEmptyBorder(4, 4, 4, 4),
    BorderFactory.createTitledBorder(
      BorderFactory.createEmptyBorder(), title, 0, 0, fontSubtitle, onSurfaceMuted,
    ),
  )

  fun paddedBorder(pad: Int = 12): Border = BorderFactory.createEmptyBorder(pad, pad, pad, pad)
}

