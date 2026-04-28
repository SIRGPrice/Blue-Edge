/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.audio

import android.media.MediaRecorder
import com.blueedge.shared.platform.AndroidContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

private class AndroidAudioRecorder : AudioRecorder {
  private val _state = MutableStateFlow<AudioRecorderState>(AudioRecorderState.Idle)
  override val state: StateFlow<AudioRecorderState> = _state

  private var recorder: MediaRecorder? = null
  private var outputFile: File? = null

  override suspend fun start(config: AudioRecordingConfig) {
    val context = AndroidContext.appContext
    check(recorder == null) { "Audio recording already in progress" }
    val file = withContext(Dispatchers.IO) {
      File.createTempFile("blueedge_audio_", ".m4a", context.cacheDir)
    }
    withContext(Dispatchers.Main) {
      runCatching {
        MediaRecorder(context).apply {
          setAudioSource(MediaRecorder.AudioSource.MIC)
          setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
          setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
          setAudioSamplingRate(config.sampleRateHz)
          setAudioEncodingBitRate(config.bitRate)
          setOutputFile(file.absolutePath)
          prepare()
          start()
        }
      }.onSuccess {
        outputFile = file
        recorder = it
        _state.value = AudioRecorderState.Recording
      }.onFailure { e ->
        file.delete()
        _state.value = AudioRecorderState.Failed(e.message ?: e.javaClass.simpleName)
        throw e
      }
    }
  }

  override suspend fun stop(): ByteArray = withContext(Dispatchers.IO) {
    val rec = recorder ?: return@withContext ByteArray(0)
    val file = outputFile ?: return@withContext ByteArray(0)
    recorder = null
    outputFile = null
    runCatching { rec.stop() }
    runCatching { rec.release() }
    val bytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
    file.delete()
    _state.value = AudioRecorderState.Idle
    bytes
  }

  override fun cancel() {
    val rec = recorder
    val file = outputFile
    recorder = null
    outputFile = null
    runCatching { rec?.stop() }
    runCatching { rec?.release() }
    runCatching { file?.delete() }
    _state.value = AudioRecorderState.Idle
  }
}

actual fun provideAudioRecorder(): AudioRecorder = AndroidAudioRecorder()

