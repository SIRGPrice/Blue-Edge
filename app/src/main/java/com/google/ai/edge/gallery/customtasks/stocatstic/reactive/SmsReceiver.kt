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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.ReactiveEvent
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.ReactiveEventBus

/** Pushes incoming SMS messages into [ReactiveEventBus]. Registered in AndroidManifest. */
class SmsReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
    val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
    // Group multipart messages by sender.
    val grouped = msgs.groupBy { it.displayOriginatingAddress ?: "" }
    grouped.forEach { (sender, parts) ->
      val body = parts.joinToString("") { it.displayMessageBody ?: "" }
      ReactiveEventBus.shared?.emit(ReactiveEvent.Sms(sender = sender, body = body))
    }
  }
}

