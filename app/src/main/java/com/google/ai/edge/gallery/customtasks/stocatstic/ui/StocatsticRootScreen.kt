/*
 * Copyright 2026 SIRGPrice and Blue Edge contributors
 * Part of Blue Edge, a heavily modified app fork based on Google AI Edge Gallery.
 * Upstream project originally published by Google LLC:
 * https://github.com/google-ai-edge/gallery
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
