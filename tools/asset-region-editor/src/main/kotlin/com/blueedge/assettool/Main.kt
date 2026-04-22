package com.blueedge.assettool

import com.blueedge.assettool.ui.EditorFrame
import com.blueedge.assettool.ui.theme.EditorTheme
import com.formdev.flatlaf.FlatDarkLaf
import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main() {
  // FlatLaf Dark — modern L&F with proper HiDPI, rounded cards, native-ish controls.
  try {
    // Tokens consumed by FlatLaf to theme buttons/panels/scrollbars consistently with the
    // EditorTheme palette. Must be set BEFORE installing the L&F.
    UIManager.put("Component.focusColor", EditorTheme.accent)
    UIManager.put("Component.focusedBorderColor", EditorTheme.accent)
    UIManager.put("Button.arc", 12)
    UIManager.put("Component.arc", 10)
    UIManager.put("ProgressBar.arc", 10)
    UIManager.put("TextComponent.arc", 10)
    UIManager.put("ScrollBar.thumbArc", 999)
    UIManager.put("ScrollBar.thumbInsets", java.awt.Insets(2, 2, 2, 2))
    UIManager.put("TitlePane.unifiedBackground", true)
    UIManager.put("Panel.background", EditorTheme.background)
    UIManager.put("SplitPane.background", EditorTheme.border)
    UIManager.put("SplitPaneDivider.gripColor", EditorTheme.borderStrong)
    UIManager.put("Table.showVerticalLines", false)
    UIManager.put("Table.showHorizontalLines", true)
    UIManager.put("Table.intercellSpacing", java.awt.Dimension(0, 1))
    UIManager.put("Table.gridColor", EditorTheme.border)

    FlatDarkLaf.setup()
  } catch (_: Throwable) {
    try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) } catch (_: Throwable) {}
  }
  SwingUtilities.invokeLater {
    EditorFrame().isVisible = true
  }
}
