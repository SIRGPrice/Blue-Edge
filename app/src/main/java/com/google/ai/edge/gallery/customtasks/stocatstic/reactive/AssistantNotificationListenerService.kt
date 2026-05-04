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

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.ReactiveEvent
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.ReactiveEventBus
import java.util.UUID

/** Known messaging/email clients — used as the default filter for Notification-based triggers. */
object MessagingApps {
  const val WHATSAPP = "com.whatsapp"
  const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
  const val TELEGRAM = "org.telegram.messenger"
  const val TELEGRAM_WEB = "org.telegram.messenger.web"
  const val DISCORD = "com.discord"
  const val GMAIL = "com.google.android.gm"
  const val OUTLOOK = "com.microsoft.office.outlook"
  const val YAHOO = "com.yahoo.mobile.client.android.mail"
  const val THUNDERBIRD = "net.thunderbird.android"

  val WHATSAPP_PKGS = listOf(WHATSAPP, WHATSAPP_BUSINESS)
  val TELEGRAM_PKGS = listOf(TELEGRAM, TELEGRAM_WEB)
  val DISCORD_PKGS = listOf(DISCORD)
  val EMAIL_PKGS = listOf(GMAIL, OUTLOOK, YAHOO, THUNDERBIRD)
}

/**
 * Captures WhatsApp/Telegram/Discord/Email notifications and:
 *   1. Emits a [ReactiveEvent.Notification] so workflow triggers & wait nodes can react.
 *   2. Stores any `RemoteInput`-based reply action in [ReplyActionCache] keyed by the
 *      conversation, so the matching `action.reply.*` capability can invoke it later.
 */
class AssistantNotificationListenerService : NotificationListenerService() {

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    val pkg = sbn.packageName ?: return
    if (!isSupportedPackage(pkg)) return
    val n = sbn.notification ?: return
    val extras: Bundle = n.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
      ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
    if (title.isBlank() && text.isBlank()) return

    val conversationKey = "$pkg|${sbn.key}|${sbn.groupKey ?: ""}|$title"

    // Cache any reply action for later use by action.reply.* capabilities.
    findReplyAction(n)?.let { (pi, ri) ->
      ReplyActionCache.put(conversationKey, pkg, title, pi, ri)
    }

    ReactiveEventBus.shared?.emit(
      ReactiveEvent.Notification(
        packageName = pkg,
        sender = title,
        body = text,
        conversationKey = conversationKey,
      ),
    )
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification) {
    ReplyActionCache.removeByKeyPrefix("${sbn.packageName}|${sbn.key}")
  }

  private fun isSupportedPackage(pkg: String): Boolean =
    pkg in MessagingApps.WHATSAPP_PKGS ||
      pkg in MessagingApps.TELEGRAM_PKGS ||
      pkg in MessagingApps.DISCORD_PKGS ||
      pkg in MessagingApps.EMAIL_PKGS

  /** Return the first action containing a textual [RemoteInput] (typical "Reply" action). */
  private fun findReplyAction(n: Notification): Pair<PendingIntent, RemoteInput>? {
    val actions = n.actions ?: return null
    for (a in actions) {
      val ri = a.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: continue
      return a.actionIntent to ri
    }
    // Wear extender also carries reply actions on some apps.
    val wearExtender = Notification.WearableExtender(n)
    for (a in wearExtender.actions) {
      val ri = a.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: continue
      return a.actionIntent to ri
    }
    return null
  }
}

/** In-memory cache of reply actions extracted from messaging notifications. */
object ReplyActionCache {
  data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val conversationKey: String,
    val packageName: String,
    val sender: String,
    val pendingIntent: PendingIntent,
    val remoteInput: RemoteInput,
    val storedAt: Long = System.currentTimeMillis(),
  )

  private val byKey = java.util.concurrent.ConcurrentHashMap<String, Entry>()

  fun put(
    conversationKey: String,
    packageName: String,
    sender: String,
    pi: PendingIntent,
    ri: RemoteInput,
  ) {
    byKey[conversationKey] = Entry(
      conversationKey = conversationKey,
      packageName = packageName,
      sender = sender,
      pendingIntent = pi,
      remoteInput = ri,
    )
  }

  fun get(conversationKey: String): Entry? = byKey[conversationKey]

  /** Best-effort lookup when the exact conversation key is unknown (fallback for replies). */
  fun findLatestFor(packageName: String, sender: String?): Entry? =
    byKey.values
      .filter { it.packageName == packageName && (sender == null || it.sender == sender) }
      .maxByOrNull { it.storedAt }

  fun removeByKeyPrefix(prefix: String) {
    byKey.keys.filter { it.startsWith(prefix) }.forEach { byKey.remove(it) }
  }
}

