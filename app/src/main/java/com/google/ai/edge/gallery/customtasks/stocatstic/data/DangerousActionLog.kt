/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Structured log of every "dangerous" / outbound action performed by a workflow â€” every sent
 * message or email is recorded here with payload, recipient and success flag so the user can
 * audit what their automations have actually done on their behalf.
 *
 * Entries are kept in-memory (latest first) and persisted as JSON on a best-effort basis.
 */
@Serializable
enum class DangerousActionKind { SMS, WHATSAPP, TELEGRAM, DISCORD, EMAIL, SHARE, CALL }

@Serializable
data class DangerousActionEntry(
  val timestamp: Long,
  val kind: DangerousActionKind,
  val recipient: String,
  val body: String,
  val success: Boolean,
  val message: String = "",
  val workflowId: String? = null,
  val nodeId: String? = null,
)

@Singleton
class DangerousActionLog @Inject constructor(
  @ApplicationContext private val ctx: Context,
) {
  private val file: File by lazy { File(ctx.filesDir, "stocatstic/dangerous_actions.json") }
  private val _entries = MutableStateFlow<List<DangerousActionEntry>>(loadFromDisk())
  val entries: StateFlow<List<DangerousActionEntry>> = _entries

  companion object {
    /** Process-global handle so capabilities (non-Hilt aware) can push entries. */
    @Volatile var shared: DangerousActionLog? = null
    private const val MAX_ENTRIES = 500
  }

  init { shared = this }

  fun record(entry: DangerousActionEntry) {
    val next = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
    _entries.value = next
    runCatching { persist(next) }
  }

  fun clear() { _entries.value = emptyList(); runCatching { persist(emptyList()) } }

  // ---------- persistence ------------------------------------------------------------------

  private fun persist(list: List<DangerousActionEntry>) {
    file.parentFile?.mkdirs()
    file.writeText(Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(
      DangerousActionEntry.serializer(),
    ), list))
  }

  private fun loadFromDisk(): List<DangerousActionEntry> = runCatching {
    if (!file.exists()) return emptyList()
    Json.decodeFromString(
      kotlinx.serialization.builtins.ListSerializer(DangerousActionEntry.serializer()),
      file.readText(),
    )
  }.getOrElse { emptyList() }
}

/**
 * Convenience: record a dangerous action from anywhere (capability code). Safe to call even
 * if the [DangerousActionLog] hasn't been constructed yet (early app start) â€” in that case the
 * entry is silently dropped.
 */
fun logDangerousAction(
  kind: DangerousActionKind,
  recipient: String,
  body: String,
  success: Boolean,
  message: String = "",
) {
  DangerousActionLog.shared?.record(
    DangerousActionEntry(
      timestamp = System.currentTimeMillis(),
      kind = kind,
      recipient = recipient,
      body = body,
      success = success,
      message = message,
    ),
  )
}


