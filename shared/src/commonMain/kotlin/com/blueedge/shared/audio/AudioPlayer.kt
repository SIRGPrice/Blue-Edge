/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Cross-platform audio playback abstraction. The shared chat UI can use this
 * instead of depending on Android's AudioTrack directly.
 */
package com.blueedge.shared.audio

import kotlinx.coroutines.flow.StateFlow

data class AudioPlaybackConfig(
  val sampleRateHz: Int = 16_000,
  /** Currently the shared runtime emits PCM 16-bit mono clips. */
  val pcm16Mono: Boolean = true,
)

sealed interface AudioPlayerState {
  data object Idle : AudioPlayerState
  data class Playing(val progress: Float) : AudioPlayerState
  data class Failed(val message: String) : AudioPlayerState
}

interface AudioPlayer {
  val state: StateFlow<AudioPlayerState>
  /** Plays a PCM16 mono clip. Any previous playback is stopped first. */
  suspend fun play(audioData: ByteArray, config: AudioPlaybackConfig = AudioPlaybackConfig())
  fun stop()
}

expect fun provideAudioPlayer(): AudioPlayer

