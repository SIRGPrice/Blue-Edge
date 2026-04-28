/*
 * Copyright 2026 Blue Edge contributors.
 *
 * iOS `actual` for `StatusBarColorController`. The iOS host configures the
 * status bar style at the SwiftUI `WindowGroup` level, so this is a no-op
 * placeholder. Bridge-to-Swift hook can be added later if dynamic switching
 * is required.
 */
package com.blueedge.shared.ui.theme

import androidx.compose.runtime.Composable

@Composable
actual fun StatusBarColorController(useDarkTheme: Boolean) {
  // Intentionally empty: iOS status bar style is driven by the SwiftUI host.
}

