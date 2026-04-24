/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import com.google.ai.edge.gallery.customtasks.common.CustomTaskTopBarAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene.InfiniteSceneScreen
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.theme.PixelPalette
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/**
 * StoCATstic root screen: a single infinite pixel scene. No tabs, no nested screens — the user
 * pans, zooms and edits everything in place on the same shared world.
 *
 * The global top app bar is hidden while this screen is mounted so all navigation controls
 * (back, gallery, history, …) are drawn as floating icons directly on top of the scene.
 */
@Composable
fun StocatsticRootScreen(
  @Suppress("UNUSED_PARAMETER") modelManagerViewModel: ModelManagerViewModel,
  bottomPadding: Dp,
  setAppBarControlsDisabled: (Boolean) -> Unit,
  setTopBarVisible: (Boolean) -> Unit,
  setCustomLeadingAction: (CustomTaskTopBarAction?) -> Unit,
  onNavigateUp: () -> Unit = {},
  vm: StocatsticViewModel = hiltViewModel(),
) {
  setAppBarControlsDisabled(false)
  DisposableEffect(Unit) {
    setTopBarVisible(false)
    setCustomLeadingAction(null)
    onDispose { setTopBarVisible(true) }
  }
  val gradient = Brush.verticalGradient(listOf(PixelPalette.nightSky, PixelPalette.deepSky))
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(gradient)
      .imePadding()
      .padding(bottom = bottomPadding),
  ) {
    InfiniteSceneScreen(vm = vm, onNavigateUp = onNavigateUp)
  }
}
