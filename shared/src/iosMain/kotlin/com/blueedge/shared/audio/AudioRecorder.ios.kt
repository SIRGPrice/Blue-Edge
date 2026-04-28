/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Real iOS audio recorder. Delegates to the Swift `AVAudioRecorder` bridge
 * (BlueEdgeAudioRecorderBridge.swift). Falls back to a safe stub if the
 * bridge is not registered.
 */
package com.blueedge.shared.audio
import com.blueedge.shared.ios.bridges.IosBridgeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
private class IosAVAudioRecorder : AudioRecorder {
  private val _state = MutableStateFlow<AudioRecorderState>(AudioRecorderState.Idle)
  override val state: StateFlow<AudioRecorderState> = _state
  override suspend fun start(config: AudioRecordingConfig) {
    val bridge = IosBridgeRegistry.current?.audioRecorder
    if (bridge == null) {
      _state.value = AudioRecorderState.Failed("Audio recorder bridge is not registered.")
      return
    }
    runCatching {
      bridge.start(sampleRate = config.sampleRateHz, bitRate = config.bitRate)
    }.onSuccess {
      _state.value = AudioRecorderState.Recording
    }.onFailure { err ->
      _state.value = AudioRecorderState.Failed(err.message ?: "Recorder failed")
    }
  }
  override suspend fun stop(): ByteArray {
    val bridge = IosBridgeRegistry.current?.audioRecorder ?: return ByteArray(0)
    val bytes = runCatching { bridge.stop() }.getOrElse { ByteArray(0) }
    _state.value = AudioRecorderState.Idle
    return bytes
  }
  override fun cancel() {
    IosBridgeRegistry.current?.audioRecorder?.cancel()
    _state.value = AudioRecorderState.Idle
  }
}
actual fun provideAudioRecorder(): AudioRecorder = IosAVAudioRecorder()
