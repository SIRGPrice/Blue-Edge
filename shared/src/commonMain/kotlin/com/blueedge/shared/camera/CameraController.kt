/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Camera capture abstraction (CameraX on Android, AVFoundation on iOS).
 */
package com.blueedge.shared.camera

import kotlinx.coroutines.flow.Flow

/** Raw RGBA frame produced by the platform camera. */
data class CameraFrame(
  val widthPx: Int,
  val heightPx: Int,
  val rgbaBytes: ByteArray,
  val timestampMs: Long,
  val rotationDegrees: Int,
)

interface CameraController {
  fun start(): Flow<CameraFrame>
  fun stop()
  suspend fun captureStill(): ByteArray
  fun switchLens()
}

expect fun provideCameraController(): CameraController

