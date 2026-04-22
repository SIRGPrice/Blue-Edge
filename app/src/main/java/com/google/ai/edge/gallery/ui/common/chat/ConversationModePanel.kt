/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.common.chat

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

private const val TAG = "ConversationModeTts"

/**
 * Manages TTS for conversation mode. Accumulates streaming text tokens and speaks
 * complete sentences.
 */
class ConversationModeTts(
  context: android.content.Context,
  private val onSpeakingChanged: (Boolean) -> Unit = {},
) {
  private var tts: TextToSpeech? = null
  private var isInitialized = false
  private val buffer = StringBuilder()
  private var utteranceCount = 0

  init {
    tts = TextToSpeech(context) { status ->
      if (status == TextToSpeech.SUCCESS) {
        isInitialized = true
        tts?.language = Locale.getDefault()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) {
            onSpeakingChanged(true)
          }
          override fun onDone(utteranceId: String?) {
            onSpeakingChanged(false)
          }
          @Deprecated("Deprecated")
          override fun onError(utteranceId: String?) {
            onSpeakingChanged(false)
          }
        })
      } else {
        Log.e(TAG, "TTS initialization failed with status: $status")
      }
    }
  }

  /** Feed a partial token from streaming. Will speak when a sentence boundary is detected. */
  fun feedToken(token: String) {
    buffer.append(token)
    val text = buffer.toString()
    val lastBoundary = maxOf(
      text.lastIndexOf('.'),
      text.lastIndexOf('!'),
      text.lastIndexOf('?'),
      text.lastIndexOf('\n'),
    )
    if (lastBoundary >= 0) {
      val toSpeak = text.substring(0, lastBoundary + 1).trim()
      buffer.clear()
      buffer.append(text.substring(lastBoundary + 1))
      if (toSpeak.isNotEmpty() && isInitialized) {
        utteranceCount++
        tts?.speak(toSpeak, TextToSpeech.QUEUE_ADD, null, "conv_$utteranceCount")
      }
    }
  }

  /** Called when generation is done — flush remaining buffer. */
  fun flush() {
    val remaining = buffer.toString().trim()
    if (remaining.isNotEmpty() && isInitialized) {
      utteranceCount++
      tts?.speak(remaining, TextToSpeech.QUEUE_ADD, null, "conv_$utteranceCount")
    }
    buffer.clear()
  }

  fun stop() {
    tts?.stop()
    buffer.clear()
  }

  fun shutdown() {
    tts?.stop()
    tts?.shutdown()
    tts = null
  }
}
