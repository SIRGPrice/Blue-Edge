/*
 * Copyright 2026 SIRGPrice and Blue Edge contributors
 * Part of Blue Edge, a heavily modified app fork based on Google AI Edge Gallery.
 * Upstream project originally published by Google LLC:
 * https://github.com/google-ai-edge/gallery
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

package com.google.ai.edge.gallery.customtasks.stocatstic.reactive

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.CallLog
import android.provider.VoicemailContract
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.ReactiveEvent
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.ReactiveEventBus

/**
 * Listens to new missed calls. Since Android does not expose a generic "MISSED_CALL" broadcast,
 * we observe the provider-specific broadcast `NEW_OUTGOING_CALL` is useless here; instead we
 * rely on the system sending `NEW_VOICEMAIL` (via [VoicemailContract.ACTION_NEW_VOICEMAIL]) and
 * on the call-log ContentObserver approach from the notification listener. The manifest entry
 * wires this receiver to the voicemail action; the missed-call detection in interactive mode is
 * performed by querying [CallLog.Calls].
 */
class CallStateReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    // Only emit missed-call events when the appropriate permissions are granted.
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
      != PackageManager.PERMISSION_GRANTED
    ) return
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val screenOff = !pm.isInteractive
    val voicemailUri: String? = runCatching {
      val lastVoicemailId = intent.getLongExtra("extra_voicemail_id", -1L)
      if (lastVoicemailId > 0)
        ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, lastVoicemailId).toString()
      else null
    }.getOrNull()

    val number = queryLastMissedCallNumber(context) ?: return
    ReactiveEventBus.shared?.emit(
      ReactiveEvent.CallMissed(
        sender = number,
        attachmentUri = voicemailUri,
        wasScreenOff = screenOff,
      ),
    )
  }

  private fun queryLastMissedCallNumber(context: Context): String? {
    return runCatching {
      context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
        "${CallLog.Calls.TYPE} = ?",
        arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
        "${CallLog.Calls.DATE} DESC LIMIT 1",
      )?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
      }
    }.getOrNull()
  }
}

