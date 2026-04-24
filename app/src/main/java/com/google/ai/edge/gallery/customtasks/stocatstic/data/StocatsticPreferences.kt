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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * User preferences for the StoCATstic scene's visual customisation.
 *
 *   • [characterId]: id inside `CharacterCatalog` (the sprite the cat/bunny/etc. uses).
 *   • [pathAssetId]: id inside `PathCatalog` (1-cell overlay drawn on every path cell).
 *   • [rootAssetId]: id inside `RootCatalog` (sprite used for the initial task of a flow).
 *   • [taskOverrides]: capabilityId → entry id inside `TaskSpriteCatalog`. Missing keys fall
 *     back to the built-in [TaskSpriteRegistry] default.
 *   • [firstRunCompleted]: set to true after the onboarding gallery has been dismissed with
 *     valid character + path selections.
 *
 * Note: earlier versions of the app allowed a per-tile "manual" override stored as a
 * `TileSelection`. That flow was retired — the desktop tool
 * `:tools:asset-region-editor` is now the single source of truth for tile regions. Any
 * legacy fields (`pathManual`, `rootManual`, `taskManualOverrides`) or sentinel values
 * (`pathAssetId = "manual"`, `rootAssetId = "manual"`) are ignored/normalised on load so
 * existing installs migrate transparently.
 */
@Serializable
data class StocatsticPreferences(
  val characterId: String = "bunny",
  val pathAssetId: String = "leaves",
  val rootAssetId: String = "mailbox",
  val taskOverrides: Map<String, String> = emptyMap(),
  val firstRunCompleted: Boolean = false,
)

@Singleton
class StocatsticPreferencesStore @Inject constructor(
  @ApplicationContext ctx: Context,
) {
  private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }
  private val file: File =
    File(ctx.filesDir, "stocatstic").apply { mkdirs() }.let { File(it, "preferences.json") }

  private val _prefs = MutableStateFlow(load())
  val prefs: StateFlow<StocatsticPreferences> = _prefs.asStateFlow()

  private fun load(): StocatsticPreferences = runCatching {
    if (!file.exists()) StocatsticPreferences()
    else json.decodeFromString(StocatsticPreferences.serializer(), file.readText()).migrate()
  }.getOrDefault(StocatsticPreferences())

  /**
   * Legacy v1 preferences persisted `pathAssetId = "manual"` / `rootAssetId = "manual"` when the
   * user picked a tile by hand (that flow is gone; regions are authored in the desktop tool
   * instead). Normalise those sentinel ids back to the defaults so the scene renders correctly.
   */
  private fun StocatsticPreferences.migrate(): StocatsticPreferences {
    val defaults = StocatsticPreferences()
    return copy(
      pathAssetId = if (pathAssetId == "manual") defaults.pathAssetId else pathAssetId,
      rootAssetId = if (rootAssetId == "manual") defaults.rootAssetId else rootAssetId,
    )
  }

  private fun persist(p: StocatsticPreferences) {
    _prefs.value = p
    runCatching { file.writeText(json.encodeToString(StocatsticPreferences.serializer(), p)) }
  }

  fun update(block: (StocatsticPreferences) -> StocatsticPreferences) {
    persist(block(_prefs.value))
  }

  fun setCharacter(id: String) = update { it.copy(characterId = id) }
  fun setPath(id: String) = update { it.copy(pathAssetId = id) }
  fun setRoot(id: String) = update { it.copy(rootAssetId = id) }
  fun setTaskOverride(capabilityId: String, entryId: String) = update {
    it.copy(taskOverrides = it.taskOverrides + (capabilityId to entryId))
  }
  fun clearTaskOverride(capabilityId: String) = update {
    it.copy(taskOverrides = it.taskOverrides - capabilityId)
  }

  fun markFirstRunCompleted() = update { it.copy(firstRunCompleted = true) }

  /**
   * Returns the set of [TaskSpriteCatalog] entry ids that are ALREADY consumed by another
   * capability — i.e. assets the user has explicitly overridden for a different capability.
   * Used by the gallery to visually mark "already taken" cells so the user doesn't reuse
   * an asset across unrelated tasks.
   *
   * @param excludeCapabilityId Capability currently being configured — its own selection is
   *   *not* considered "consumed" from its own row's point of view.
   */
  fun consumedTaskEntryIds(excludeCapabilityId: String? = null): Map<String, String> {
    val p = _prefs.value
    val out = HashMap<String, String>(p.taskOverrides.size)
    p.taskOverrides.forEach { (cap, entry) ->
      if (cap == excludeCapabilityId) return@forEach
      // first-writer-wins keeps the label of the capability that "owns" the asset.
      out.putIfAbsent(entry, cap)
    }
    return out
  }
}
