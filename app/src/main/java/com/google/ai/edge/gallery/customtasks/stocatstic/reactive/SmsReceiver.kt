/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
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

