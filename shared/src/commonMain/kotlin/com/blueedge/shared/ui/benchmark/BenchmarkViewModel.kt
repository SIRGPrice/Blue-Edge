/*
 * Copyright 2026 Blue Edge contributors.
 *
 * View-model for the shared benchmark screen. Wraps [BenchmarkRunner] with a
 * StateFlow-based UI state, model-selection helpers and last-used descriptor
 * persistence through [SettingsRepository].
 */
package com.blueedge.shared.ui.benchmark

import com.blueedge.shared.domain.ModelFile
import com.blueedge.shared.domain.ModelStorage
import com.blueedge.shared.runtime.LlmGenerationConfig
import com.blueedge.shared.runtime.LlmModelDescriptor
import com.blueedge.shared.storage.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BenchmarkUiState(
  val availableModels: List<ModelFile> = emptyList(),
  val selectedModelPath: String? = null,
  val prompt: String = BenchmarkRunner.DEFAULT_PROMPT,
  val isRunning: Boolean = false,
  val latest: BenchmarkSummary? = null,
  val errorMessage: String? = null,
)

class BenchmarkViewModel(
  private val runner: BenchmarkRunner,
  private val storage: ModelStorage,
  private val settings: SettingsRepository,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
  private val _state = MutableStateFlow(BenchmarkUiState())
  val state: StateFlow<BenchmarkUiState> = _state.asStateFlow()

  private var runJob: Job? = null

  init { refreshModels() }

  /** Re-scan the model storage directory and pre-select the last-used path. */
  fun refreshModels() {
    val all = storage.listModelFiles().filter { !it.isDirectory && isInferable(it.name) }
    val preferred = settings.lastLoadedModelPath
      ?.takeIf { path -> all.any { it.absolutePath == path } }
      ?: all.firstOrNull()?.absolutePath
    _state.value = _state.value.copy(
      availableModels = all,
      selectedModelPath = preferred,
    )
  }

  fun selectModel(absolutePath: String) {
    _state.value = _state.value.copy(selectedModelPath = absolutePath)
  }

  fun setPrompt(value: String) {
    _state.value = _state.value.copy(prompt = value)
  }

  fun dismissError() {
    if (_state.value.errorMessage != null) {
      _state.value = _state.value.copy(errorMessage = null)
    }
  }

  /** Runs the benchmark using the currently selected model, if any. */
  fun run(config: LlmGenerationConfig = LlmGenerationConfig(maxTokens = 128)) {
    if (_state.value.isRunning) return
    val path = _state.value.selectedModelPath ?: run {
      _state.value = _state.value.copy(errorMessage = "Select a model first.")
      return
    }
    val prompt = _state.value.prompt.ifBlank { BenchmarkRunner.DEFAULT_PROMPT }
    val descriptor = LlmModelDescriptor(modelPath = path)
    _state.value = _state.value.copy(isRunning = true, errorMessage = null)
    runJob?.cancel()
    runJob = scope.launch {
      runCatching { runner.run(descriptor, prompt, config) }
        .onSuccess { summary ->
          settings.lastLoadedModelPath = path
          _state.value = _state.value.copy(
            isRunning = false,
            latest = summary,
          )
        }
        .onFailure { err ->
          _state.value = _state.value.copy(
            isRunning = false,
            errorMessage = "Benchmark failed: ${err.message ?: err::class.simpleName}",
          )
        }
    }
  }

  fun cancel() {
    runJob?.cancel()
    runJob = null
    _state.value = _state.value.copy(isRunning = false)
  }

  private fun isInferable(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".task") || lower.endsWith(".tflite") || lower.endsWith(".bin") ||
      lower.endsWith(".litertlm")
  }
}

