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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 */
@Composable
fun StocatsticRootScreen(
  @Suppress("UNUSED_PARAMETER") modelManagerViewModel: ModelManagerViewModel,
  bottomPadding: Dp,
  setAppBarControlsDisabled: (Boolean) -> Unit,
  @Suppress("UNUSED_PARAMETER") setTopBarVisible: (Boolean) -> Unit,
  setCustomLeadingAction: (CustomTaskTopBarAction?) -> Unit,
  vm: StocatsticViewModel = hiltViewModel(),
) {
  setAppBarControlsDisabled(false)
  var galleryRequestCount by remember { mutableIntStateOf(0) }
  DisposableEffect(Unit) {
    setCustomLeadingAction(
      CustomTaskTopBarAction(
        icon = Icons.Outlined.Collections,
        contentDescription = "Abrir galería de assets",
        onClick = { galleryRequestCount++ },
      )
    )
    onDispose { setCustomLeadingAction(null) }
  }
  val gradient = Brush.verticalGradient(listOf(PixelPalette.nightSky, PixelPalette.deepSky))
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(gradient)
      .imePadding()
      .padding(bottom = bottomPadding),
  ) {
    InfiniteSceneScreen(vm = vm, externalGalleryRequestCount = galleryRequestCount)
  }
}

