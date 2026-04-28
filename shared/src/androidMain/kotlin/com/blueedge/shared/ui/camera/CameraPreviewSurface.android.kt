/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Android placeholder surface. It starts/stops the shared `CameraController`
 * lifecycle and emits frames to `onFrame`; the visual PreviewView binding can
 * replace the Box internals later without changing common callers.
 */
package com.blueedge.shared.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blueedge.shared.camera.CameraController
import com.blueedge.shared.camera.CameraFrame

@Composable
actual fun CameraPreviewSurface(
  controller: CameraController,
  modifier: Modifier,
  onFrame: (CameraFrame) -> Unit,
) {
  LaunchedEffect(controller) {
    controller.start().collect { frame -> onFrame(frame) }
  }
  DisposableEffect(controller) {
    onDispose { controller.stop() }
  }
  Box(
    modifier = modifier.fillMaxSize().background(Color.Black),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "Camera preview",
      style = MaterialTheme.typography.bodyMedium,
      color = Color.White,
      modifier = Modifier.padding(16.dp),
    )
  }
}

