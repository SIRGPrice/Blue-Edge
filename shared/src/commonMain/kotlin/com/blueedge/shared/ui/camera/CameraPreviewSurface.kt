/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Cross-platform camera preview surface. This gives shared UI a stable
 * composable API while platform internals evolve from placeholders to
 * CameraX / AVCapture previews.
 */
package com.blueedge.shared.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.blueedge.shared.camera.CameraController
import com.blueedge.shared.camera.CameraFrame

@Composable
expect fun CameraPreviewSurface(
  controller: CameraController,
  modifier: Modifier = Modifier,
  onFrame: (CameraFrame) -> Unit = {},
)

