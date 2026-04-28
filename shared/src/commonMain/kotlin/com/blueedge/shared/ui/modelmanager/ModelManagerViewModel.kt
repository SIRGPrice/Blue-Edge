/*
 * Copyright 2026 Blue Edge contributors.
 *
 * View-model for the shared model manager. Wraps [ModelStorage] and exposes
 * "use this model" actions so the user can pin which `.task` bundle the chat
 * screen will boot into next time.
 */
package com.blueedge.shared.ui.modelmanager

import com.blueedge.shared.domain.ModelFile
import com.blueedge.shared.domain.ModelImporter
import com.blueedge.shared.domain.ModelStorage
import com.blueedge.shared.runtime.LlmEngine
import com.blueedge.shared.runtime.LlmModelDescriptor
import com.blueedge.shared.storage.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelManagerUiState(
  val files: List<ModelFile> = emptyList(),
  val baseDir: String = "",
  val activeModelPath: String? = null,
  val message: String? = null,
  val isLoading: Boolean = false,
  val canImport: Boolean = false,
  val isImporting: Boolean = false,
)

class ModelManagerViewModel(
  private val storage: ModelStorage,
  private val engine: LlmEngine,
  private val settings: SettingsRepository,
  private val importer: ModelImporter,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
  private val _state = MutableStateFlow(ModelManagerUiState())
  val state: StateFlow<ModelManagerUiState> = _state.asStateFlow()

  init { refresh() }

  fun refresh() {
    _state.value = _state.value.copy(
      files = storage.listModelFiles(),
      baseDir = storage.baseModelsDir,
      activeModelPath = settings.lastLoadedModelPath,
      canImport = importer.isSupported,
    )
  }

  /**
   * Pins [file] as the model the chat screen should boot into next time and
   * eagerly loads it into the platform engine so the next prompt does not
   * pay the cold-start cost.
   */
  fun useModel(file: ModelFile) {
    if (file.isDirectory) {
      _state.value = _state.value.copy(message = "Selected entry is a folder, not a model file.")
      return
    }
    val descriptor = LlmModelDescriptor(modelPath = file.absolutePath)
    settings.lastLoadedModelPath = file.absolutePath
    _state.value = _state.value.copy(
      activeModelPath = file.absolutePath,
      isLoading = true,
      message = "Loading ${file.name}…",
    )
    scope.launch {
      runCatching {
        if (engine.isAvailable(descriptor)) engine.load(descriptor)
      }.onSuccess {
        _state.value = _state.value.copy(isLoading = false, message = "${file.name} ready.")
      }.onFailure { err ->
        _state.value = _state.value.copy(
          isLoading = false,
          message = "Could not load ${file.name}: ${err.message ?: err::class.simpleName}",
        )
      }
    }
  }

  fun dismissMessage() {
    if (_state.value.message != null) _state.value = _state.value.copy(message = null)
  }

  /** Presents a system file picker (when supported) and refreshes the list. */
  fun importModel() {
    if (!importer.isSupported || _state.value.isImporting) return
    _state.value = _state.value.copy(isImporting = true, message = "Pick a .task model…")
    scope.launch {
      runCatching { importer.pickAndImport() }
        .onSuccess { paths ->
          val msg = when {
            paths.isEmpty() -> "Import cancelled."
            paths.size == 1 -> "Imported ${paths.first().substringAfterLast('/')}."
            else -> "Imported ${paths.size} files."
          }
          _state.value = _state.value.copy(isImporting = false, message = msg)
          refresh()
        }
        .onFailure { err ->
          _state.value = _state.value.copy(
            isImporting = false,
            message = "Import failed: ${err.message ?: err::class.simpleName}",
          )
        }
    }
  }
}

