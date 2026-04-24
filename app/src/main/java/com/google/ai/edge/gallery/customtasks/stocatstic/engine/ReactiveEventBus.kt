/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.engine

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide event bus fed by reactive platform sources (SMS receiver, NotificationListenerService,
 * PhoneStateListener/TelephonyCallback, voicemail reader, ...).
 *
 * Consumers:
 *   * [TriggerScheduler] — subscribes one collector per workflow reactive trigger.
 *   * "Esperar ..." capabilities used mid-graph — `events.first { it.matches(filter) }`.
 *
 * All events carry a stable shape (sender, body, attachment, package) so downstream nodes
 * — including the multimodal [LlmDecisionCapability] — can consume them uniformly via
 * the standard `sender` / `body` / `attachmentUri` / `out` ports.
 */
sealed class ReactiveEvent {
  /** Raw textual message body (used as the default `out` payload of wait nodes). */
  abstract val body: String
  /** Sender address / id (phone, user, email). Empty if unknown. */
  abstract val sender: String
  /** Wall-clock timestamp in ms. */
  abstract val timestamp: Long
  /** Optional attachment content:// URI (voicemail audio, mms image, ...). */
  open val attachmentUri: String? = null
  /** Optional package name of the originating app (notifications). */
  open val packageName: String? = null

  data class Sms(
    override val sender: String,
    override val body: String,
    override val timestamp: Long = System.currentTimeMillis(),
  ) : ReactiveEvent()

  /** Any messaging / email notification caught by the listener service. */
  data class Notification(
    override val packageName: String,
    override val sender: String,
    override val body: String,
    /** Conversation key to correlate with a cached reply action. */
    val conversationKey: String,
    override val timestamp: Long = System.currentTimeMillis(),
  ) : ReactiveEvent()

  data class CallMissed(
    override val sender: String,
    override val body: String = "",
    override val attachmentUri: String? = null,
    val wasScreenOff: Boolean = false,
    override val timestamp: Long = System.currentTimeMillis(),
  ) : ReactiveEvent()
}

@Singleton
class ReactiveEventBus @Inject constructor() {
  private val _events = MutableSharedFlow<ReactiveEvent>(
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  val events: SharedFlow<ReactiveEvent> = _events.asSharedFlow()

  fun emit(event: ReactiveEvent) { _events.tryEmit(event) }

  companion object {
    /** Process-wide static handle so platform receivers (registered by the system, with no
     *  Hilt injection) can push events. Populated by [ReactiveEventBusInitializer]. */
    @Volatile var shared: ReactiveEventBus? = null
  }
}

/** Hilt-initialised singleton that exposes the bus through [ReactiveEventBus.shared]. */
@Singleton
class ReactiveEventBusInitializer @Inject constructor(bus: ReactiveEventBus) {
  init { ReactiveEventBus.shared = bus }
}

/** Matches a sender/number against a [com.google.ai.edge.gallery.customtasks.stocatstic.domain.MatchMode]
 *  + list pair. Shared by every reactive capability / trigger. */
fun matchesSender(
  candidate: String,
  mode: com.google.ai.edge.gallery.customtasks.stocatstic.domain.MatchMode,
  allowed: List<String>,
): Boolean {
  val c = candidate.trim()
  return when (mode) {
    com.google.ai.edge.gallery.customtasks.stocatstic.domain.MatchMode.ANY -> true
    com.google.ai.edge.gallery.customtasks.stocatstic.domain.MatchMode.ONE ->
      allowed.firstOrNull()?.let { it.equalsLoose(c) } ?: false
    com.google.ai.edge.gallery.customtasks.stocatstic.domain.MatchMode.LIST ->
      allowed.any { it.equalsLoose(c) }
  }
}

private fun String.equalsLoose(other: String): Boolean {
  val a = this.filter { !it.isWhitespace() && it != '-' }
  val b = other.filter { !it.isWhitespace() && it != '-' }
  return a.equals(b, ignoreCase = true) || a.endsWith(b) || b.endsWith(a)
}

