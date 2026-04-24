/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.reactive

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.URLEncoder
import kotlinx.coroutines.delay

/**
 * StoCATstic automation service. Enabled manually by the user in Accessibility Settings. It
 * is used only as a **fallback** when a messaging notification's `RemoteInput` is no longer
 * available â€” in that case [AutomationController] opens the messaging app with a deep link
 * and this service clicks the on-screen "send" button so the reply goes through without
 * further user interaction.
 *
 * The service deliberately does nothing on normal events â€” it only acts when asked to by
 * [AutomationController] through its static snapshot of the active service instance.
 */
class AssistantAccessibilityService : AccessibilityService() {

  override fun onServiceConnected() {
    super.onServiceConnected()
    active = this
  }

  override fun onUnbind(intent: Intent?): Boolean {
    active = null
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    active = null
    super.onDestroy()
  }

  override fun onInterrupt() { /* no-op */ }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // Intentionally no-op: we act imperatively from [AutomationController] rather than
    // reactively on every event to keep the service cheap and predictable.
  }

  companion object {
    @Volatile var active: AssistantAccessibilityService? = null
      private set

    fun isEnabled(context: Context): Boolean {
      if (active != null) return true
      // Fallback check via the system settings string (works even before the service is
      // first connected after a reboot).
      val enabled = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ).orEmpty()
      val expected = context.packageName + "/" + AssistantAccessibilityService::class.java.name
      return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
  }
}

/**
 * High-level "send a message in another app" API. Uses deep links when available and then
 * drives the UI through [AssistantAccessibilityService].
 *
 * Every operation is best-effort and returns a boolean: callers (typically
 * [com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.ReplyViaNotificationCapability])
 * should fall back gracefully and log the attempt.
 */
object AutomationController {

  private const val WAIT_FOR_UI_MS = 2_000L
  private const val POLL_MS = 120L

  /** Public entry. Returns true when we believe the message was sent. */
  suspend fun sendMessage(
    ctx: Context,
    packageName: String,
    recipient: String,
    body: String,
  ): Boolean {
    if (body.isBlank()) return false
    return when (packageName) {
      in MessagingApps.WHATSAPP_PKGS -> sendWhatsApp(ctx, recipient, body)
      in MessagingApps.TELEGRAM_PKGS -> sendTelegram(ctx, recipient, body)
      in MessagingApps.DISCORD_PKGS -> sendDiscord(ctx, recipient, body)
      in MessagingApps.EMAIL_PKGS -> sendEmailIntent(ctx, recipient, body)
      else -> false
    }
  }

  // ---------- WhatsApp ---------------------------------------------------------------------

  private suspend fun sendWhatsApp(ctx: Context, phone: String, body: String): Boolean {
    val cleaned = phone.filter { it.isDigit() || it == '+' }
    val uri = Uri.parse(
      "https://api.whatsapp.com/send?phone=${URLEncoder.encode(cleaned, "UTF-8")}" +
        "&text=${URLEncoder.encode(body, "UTF-8")}",
    )
    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return clickSendButton(
      contentDescriptionsEs = listOf("Enviar", "enviar"),
      contentDescriptionsEn = listOf("Send", "send"),
    )
  }

  // ---------- Telegram ---------------------------------------------------------------------

  private suspend fun sendTelegram(ctx: Context, username: String, body: String): Boolean {
    val handle = username.removePrefix("@").trim()
    val uri = Uri.parse("https://t.me/$handle")
    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    // Telegram deep link doesn't pre-fill the message â€” write it ourselves and click send.
    if (!pasteTextIntoEditable(body)) return false
    return clickSendButton(
      contentDescriptionsEs = listOf("Enviar"),
      contentDescriptionsEn = listOf("Send", "Send message"),
    )
  }

  // ---------- Discord (best effort â€” no deep-link to DMs) ---------------------------------

  private suspend fun sendDiscord(ctx: Context, @Suppress("UNUSED_PARAMETER") user: String, body: String): Boolean {
    // Open the app (launcher intent). User-specific DM deep links are not publicly supported,
    // so we rely on the last-open conversation.
    val i = ctx.packageManager.getLaunchIntentForPackage(MessagingApps.DISCORD) ?: return false
    ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    if (!pasteTextIntoEditable(body)) return false
    return clickSendButton(
      contentDescriptionsEn = listOf("Send", "Send message"),
      contentDescriptionsEs = listOf("Enviar"),
    )
  }

  // ---------- Email fallback via intent (no accessibility needed) -------------------------

  private fun sendEmailIntent(ctx: Context, to: String, body: String): Boolean {
    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to"))
      .putExtra(Intent.EXTRA_TEXT, body)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { ctx.startActivity(i); true }.getOrDefault(false)
  }

  // ---------- Accessibility primitives ----------------------------------------------------

  private suspend fun pasteTextIntoEditable(text: String): Boolean {
    val svc = AssistantAccessibilityService.active ?: return false
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < WAIT_FOR_UI_MS) {
      val root = svc.rootInActiveWindow
      val edit = root?.let { findEditable(it) }
      if (edit != null) {
        val args = Bundle().apply {
          putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return ok
      }
      delay(POLL_MS)
    }
    return false
  }

  private suspend fun clickSendButton(
    contentDescriptionsEs: List<String> = emptyList(),
    contentDescriptionsEn: List<String> = emptyList(),
  ): Boolean {
    val svc = AssistantAccessibilityService.active ?: return false
    val needles = (contentDescriptionsEs + contentDescriptionsEn).map { it.lowercase() }
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < WAIT_FOR_UI_MS) {
      val root = svc.rootInActiveWindow
      if (root != null) {
        findClickableByContentDescription(root, needles)?.let {
          return it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
      }
      delay(POLL_MS)
    }
    return false
  }

  // ---------- node search ------------------------------------------------------------------

  private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    if (root.isEditable) return root
    for (i in 0 until root.childCount) {
      val child = root.getChild(i) ?: continue
      findEditable(child)?.let { return it }
    }
    return null
  }

  private fun findClickableByContentDescription(
    root: AccessibilityNodeInfo,
    needles: List<String>,
  ): AccessibilityNodeInfo? {
    val cd = root.contentDescription?.toString()?.lowercase().orEmpty()
    if (root.isClickable && needles.any { it in cd }) return root
    for (i in 0 until root.childCount) {
      val child = root.getChild(i) ?: continue
      findClickableByContentDescription(child, needles)?.let { return it }
    }
    return null
  }
}


