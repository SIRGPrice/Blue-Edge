/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Cross-platform key/value persistence backed by `multiplatform-settings`.
 *
 * Scope: simple primitives + JSON-serialized objects. The Android `:app`
 * keeps its existing Proto DataStore as the canonical source of truth
 * during the transition; a one-shot importer can hydrate this layer at
 * first launch when needed. iOS uses `NSUserDefaults` natively.
 */
package com.blueedge.shared.storage

import com.blueedge.shared.ui.theme.ThemeMode
import com.russhwolf.settings.Settings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class SettingsRepository(private val settings: Settings) {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  // --- Primitives ---------------------------------------------------------
  fun getString(key: String, default: String = ""): String = settings.getString(key, default)
  fun putString(key: String, value: String) { settings.putString(key, value) }
  fun getInt(key: String, default: Int = 0): Int = settings.getInt(key, default)
  fun putInt(key: String, value: Int) { settings.putInt(key, value) }
  fun getBoolean(key: String, default: Boolean = false): Boolean = settings.getBoolean(key, default)
  fun putBoolean(key: String, value: Boolean) { settings.putBoolean(key, value) }
  fun remove(key: String) { settings.remove(key) }

  // --- JSON-serialized objects -------------------------------------------
  fun <T> putObject(key: String, serializer: KSerializer<T>, value: T) {
    settings.putString(key, json.encodeToString(serializer, value))
  }

  fun <T> getObject(key: String, serializer: KSerializer<T>): T? {
    val raw = settings.getStringOrNull(key) ?: return null
    return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
  }

  // --- Common typed accessors --------------------------------------------
  var themeMode: ThemeMode
    get() = runCatching { ThemeMode.valueOf(settings.getString(KEY_THEME, ThemeMode.AUTO.name)) }
      .getOrDefault(ThemeMode.AUTO)
    set(value) { settings.putString(KEY_THEME, value.name) }

  companion object {
    const val KEY_THEME = "blueedge.theme"
  }
}

/** Platform factory; resolved through Koin. */
expect fun provideSettings(): Settings



