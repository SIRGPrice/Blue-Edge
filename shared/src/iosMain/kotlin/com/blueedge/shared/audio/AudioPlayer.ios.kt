/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Real iOS audio player. Delegates to the Swift `AVAudioEngine` bridge
 * (BlueEdgeAudioPlayerBridge.swift). Falls back to a safe stub if the
 * bridge is not registered.
 */
package com.blueedge.shared.audio
import com.blueedge.shared.ios.bridges.IosBridgeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
private class IosAVAudioPlayer : AudioPlayer {
  private val _state = MutableStateFlow<AudioPlayerState>(AudioPlayerState.Idle)
  override val state: StateFlow<AudioPlayerState> = _state
  override suspend fun play(audioData: ByteArray, config: AudioPlaybackConfig) {
    val bridge = IosBridgeRegistry.current?.audioPlayer
    if (bridge == null) {
      _state.value = AudioPlayerState.Failed("Audio player bridge is not registered.")
      return
    }
    _state.value = AudioPlayerState.Playing(0f)
    bridge.play(
      pcm16Mono = audioData,
      sampleRate = config.sampleRateHz,
      onProgress = { p -> _state.value = AudioPlayerState.Playing(p) },
      onFinished = { _state.value = AudioPlayerState.Idle },
    )
  }
  override fun stop() {
    IosBridgeRegistry.current?.audioPlayer?.stop()
    _state.value = AudioPlayerState.Idle
  }
}
actual fun provideAudioPlayer(): AudioPlayer = IosAVAudioPlayer()
