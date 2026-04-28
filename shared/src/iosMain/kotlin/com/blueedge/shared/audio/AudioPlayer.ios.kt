/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Safe iOS placeholder. Replace with an AVAudioEngine/AVAudioPlayerNode
 * bridge without changing common callers.
 */
package com.blueedge.shared.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private class IosAudioPlayerStub : AudioPlayer {
  private val _state = MutableStateFlow<AudioPlayerState>(AudioPlayerState.Idle)
  override val state: StateFlow<AudioPlayerState> = _state

  override suspend fun play(audioData: ByteArray, config: AudioPlaybackConfig) {
    _state.value = AudioPlayerState.Failed("iOS audio playback bridge is not connected yet.")
  }

  override fun stop() {
    _state.value = AudioPlayerState.Idle
  }
}

actual fun provideAudioPlayer(): AudioPlayer = IosAudioPlayerStub()

