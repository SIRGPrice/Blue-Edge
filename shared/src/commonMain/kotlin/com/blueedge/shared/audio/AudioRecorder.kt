/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Cross-platform audio recorder abstraction. Android is backed by
 * `MediaRecorder`; iOS is wired as a safe stub until the Swift
 * `AVAudioRecorder` bridge is connected.
 */
package com.blueedge.shared.audio

import kotlinx.coroutines.flow.StateFlow

data class AudioRecordingConfig(
  val sampleRateHz: Int = 16_000,
  val bitRate: Int = 64_000,
)

sealed interface AudioRecorderState {
  data object Idle : AudioRecorderState
  data object Recording : AudioRecorderState
  data class Failed(val message: String) : AudioRecorderState
}

interface AudioRecorder {
  val state: StateFlow<AudioRecorderState>
  /** Starts recording to an internal temporary file. */
  suspend fun start(config: AudioRecordingConfig = AudioRecordingConfig())
  /** Stops recording and returns the encoded audio bytes. */
  suspend fun stop(): ByteArray
  /** Cancels recording and deletes any temporary file. */
  fun cancel()
}

expect fun provideAudioRecorder(): AudioRecorder

