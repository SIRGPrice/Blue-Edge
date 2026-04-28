/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class AndroidAudioPlayer : AudioPlayer {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val _state = MutableStateFlow<AudioPlayerState>(AudioPlayerState.Idle)
  override val state: StateFlow<AudioPlayerState> = _state

  @Volatile private var audioTrack: AudioTrack? = null
  private var playbackJob: Job? = null

  override suspend fun play(audioData: ByteArray, config: AudioPlaybackConfig) {
    if (audioData.isEmpty()) {
      _state.value = AudioPlayerState.Idle
      return
    }
    check(config.pcm16Mono) { "Only PCM16 mono audio is supported on AndroidAudioPlayer." }
    stop()
    withContext(Dispatchers.IO) {
      runCatching {
        val track = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .build(),
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(config.sampleRateHz)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build(),
          )
          .setTransferMode(AudioTrack.MODE_STATIC)
          .setBufferSizeInBytes(audioData.size)
          .build()
        val totalFrames = (audioData.size / BYTES_PER_PCM16_MONO_FRAME).coerceAtLeast(1)
        track.write(audioData, 0, audioData.size)
        audioTrack = track
        track.play()
        _state.value = AudioPlayerState.Playing(0f)
        playbackJob = scope.launch {
          while (isActive && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            val progress = (track.playbackHeadPosition.toFloat() / totalFrames).coerceIn(0f, 1f)
            _state.value = AudioPlayerState.Playing(progress)
            if (progress >= 1f) break
            delay(PROGRESS_INTERVAL_MS)
          }
          stop()
        }
      }.onFailure { e ->
        Log.e(TAG, "play failed", e)
        _state.value = AudioPlayerState.Failed(e.message ?: e.javaClass.simpleName)
        stop()
      }
    }
  }

  override fun stop() {
    playbackJob?.cancel()
    playbackJob = null
    val track = audioTrack
    audioTrack = null
    runCatching { track?.stop() }
    runCatching { track?.release() }
    _state.value = AudioPlayerState.Idle
  }

  companion object {
    private const val TAG = "AndroidAudioPlayer"
    private const val BYTES_PER_PCM16_MONO_FRAME = 2
    private const val PROGRESS_INTERVAL_MS = 30L
  }
}

actual fun provideAudioPlayer(): AudioPlayer = AndroidAudioPlayer()

