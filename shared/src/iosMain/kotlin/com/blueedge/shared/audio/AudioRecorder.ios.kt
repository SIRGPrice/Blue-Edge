/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Safe iOS placeholder. The Swift AVAudioRecorder bridge can replace this
 * without changing common callers.
 */
package com.blueedge.shared.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private class IosAudioRecorderStub : AudioRecorder {
  private val _state = MutableStateFlow<AudioRecorderState>(AudioRecorderState.Idle)
  override val state: StateFlow<AudioRecorderState> = _state

  override suspend fun start(config: AudioRecordingConfig) {
    _state.value = AudioRecorderState.Failed("iOS audio recorder bridge is not connected yet.")
  }

  override suspend fun stop(): ByteArray = ByteArray(0)
  override fun cancel() { _state.value = AudioRecorderState.Idle }
}

actual fun provideAudioRecorder(): AudioRecorder = IosAudioRecorderStub()

