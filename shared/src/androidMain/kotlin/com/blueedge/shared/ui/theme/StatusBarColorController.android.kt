/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Android `actual` for `StatusBarColorController`. Mirrors the original
 * implementation in `:app/.../ui/theme/Theme.kt` so the shared theme behaves
 * identically once `:app` adopts `BlueEdgeTheme`.
 */
package com.blueedge.shared.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun StatusBarColorController(useDarkTheme: Boolean) {
  val view = LocalView.current
  val window = (view.context as? Activity)?.window ?: return
  SideEffect {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowCompat.getInsetsController(window, view)
    @Suppress("DEPRECATION")
    window.statusBarColor = AndroidColor.TRANSPARENT
    @Suppress("DEPRECATION")
    window.navigationBarColor = AndroidColor.TRANSPARENT
    controller.isAppearanceLightStatusBars = !useDarkTheme
    controller.isAppearanceLightNavigationBars = !useDarkTheme
  }
  LaunchedEffect(useDarkTheme) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
  }
}

