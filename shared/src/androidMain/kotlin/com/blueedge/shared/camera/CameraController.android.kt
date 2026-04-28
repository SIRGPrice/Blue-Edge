/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.camera

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private class AndroidCameraStub : CameraController {
  override fun start(): Flow<CameraFrame> = emptyFlow()
  override fun stop() {}
  override suspend fun captureStill(): ByteArray = ByteArray(0)
  override fun switchLens() {}
}

actual fun provideCameraController(): CameraController = AndroidCameraStub()

