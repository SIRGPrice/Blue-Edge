/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Phase 2 will back this with AVCaptureSession through a Swift wrapper.
 */
package com.blueedge.shared.camera

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private class IosCameraStub : CameraController {
  override fun start(): Flow<CameraFrame> = emptyFlow()
  override fun stop() {}
  override suspend fun captureStill(): ByteArray = ByteArray(0)
  override fun switchLens() {}
}

actual fun provideCameraController(): CameraController = IosCameraStub()

