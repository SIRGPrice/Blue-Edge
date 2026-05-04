/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform chat view-model that streams tokens through the platform's
 * `LlmEngine` (MediaPipe iOS, LiteRT-LM Android). UI state is exposed as a
 * `StateFlow<ChatUiState>` consumable from Compose Multiplatform.
 */
package com.blueedge.shared.chat

import com.blueedge.shared.domain.Task
import com.blueedge.shared.domain.findBuiltInTask
import com.blueedge.shared.runtime.LlmEvent
import com.blueedge.shared.runtime.LlmGenerationConfig
import com.blueedge.shared.runtime.LlmModelDescriptor
import com.blueedge.shared.runtime.createLlmEngine
import com.blueedge.shared.storage.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

enum class Role { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
  val role: Role,
  val text: String,
  val streaming: Boolean = false,
)

data class ChatUiState(
  val messages: List<ChatMessage> = emptyList(),
  val isModelReady: Boolean = false,
  val isGenerating: Boolean = false,
  val errorMessage: String? = null,
  val task: Task? = null,
)

class ChatViewModel(
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
  private val settings: SettingsRepository? = null,
) {
  private val engine = createLlmEngine()

  private val _state = MutableStateFlow(ChatUiState())
  val state: StateFlow<ChatUiState> = _state.asStateFlow()

  private var generationJob: Job? = null
  private var systemPrompt: String = ""

  init {
    // If a previous session loaded a model, hydrate the engine eagerly so
    // the user can start chatting straight away.
    settings?.lastLoadedModelPath
      ?.takeIf { it.isNotBlank() }
      ?.let { path -> loadModel(LlmModelDescriptor(modelPath = path)) }
  }

  /**
   * Binds this view-model to a [Task]. Sets the default system prompt that
   * will be prepended to subsequent generations. Calling this with a different
   * task clears the conversation so the new prompt is honored from scratch.
   */
  fun setTask(task: Task) {
    val current = _state.value.task
    systemPrompt = task.defaultSystemPrompt
    if (current?.id == task.id) {
      _state.value = _state.value.copy(task = task)
      return
    }
    generationJob?.cancel()
    generationJob = null
    _state.value = ChatUiState(
      isModelReady = _state.value.isModelReady,
      task = task,
    )
  }

  /** Convenience overload: binds by built-in task id (no-op if unknown). */
  fun setTaskById(id: String) {
    findBuiltInTask(id)?.let(::setTask)
  }

  fun loadModel(descriptor: LlmModelDescriptor) {
    scope.launch {
      runCatching {
        if (engine.isAvailable(descriptor)) engine.load(descriptor)
      }.onSuccess {
        settings?.lastLoadedModelPath = descriptor.modelPath
        _state.value = _state.value.copy(isModelReady = true, errorMessage = null)
      }.onFailure { e ->
        _state.value = _state.value.copy(
          isModelReady = false,
          errorMessage = "Could not load model: ${e.message}",
        )
      }
    }
  }

  fun send(prompt: String, config: LlmGenerationConfig = LlmGenerationConfig()) {
    if (prompt.isBlank()) return
    generationJob?.cancel()
    val updated = _state.value.messages +
      ChatMessage(Role.USER, prompt) +
      ChatMessage(Role.ASSISTANT, "", streaming = true)
    _state.value = _state.value.copy(messages = updated, isGenerating = true)

    val effectivePrompt = if (systemPrompt.isNotBlank()) "$systemPrompt\n\n$prompt" else prompt
    generationJob = scope.launch {
      val sb = StringBuilder()
      engine.generate(effectivePrompt, config).collect { event ->
        when (event) {
          is LlmEvent.Token -> {
            sb.append(event.text)
            updateLastAssistant(sb.toString(), streaming = true)
          }
          is LlmEvent.Done -> {
            updateLastAssistant(sb.toString(), streaming = false)
            _state.value = _state.value.copy(isGenerating = false)
          }
          is LlmEvent.Error -> {
            updateLastAssistant(sb.toString(), streaming = false)
            _state.value = _state.value.copy(
              errorMessage = event.message,
              isGenerating = false,
            )
          }
        }
      }
    }
  }

  fun cancelGeneration() {
    generationJob?.cancel()
    generationJob = null
    val list = _state.value.messages.toMutableList()
    val idx = list.indexOfLast { it.role == Role.ASSISTANT }
    if (idx >= 0 && list[idx].streaming) {
      list[idx] = list[idx].copy(streaming = false)
    }
    _state.value = _state.value.copy(messages = list, isGenerating = false)
  }

  /** Drops all messages from the conversation and any pending generation. */
  fun clearMessages() {
    generationJob?.cancel()
    generationJob = null
    _state.value = _state.value.copy(messages = emptyList(), isGenerating = false, errorMessage = null)
  }

  /** Clears the inline error banner without otherwise touching state. */
  fun dismissError() {
    if (_state.value.errorMessage != null) {
      _state.value = _state.value.copy(errorMessage = null)
    }
  }

  private fun updateLastAssistant(text: String, streaming: Boolean) {
    val list = _state.value.messages.toMutableList()
    val idx = list.indexOfLast { it.role == Role.ASSISTANT }
    if (idx >= 0) list[idx] = list[idx].copy(text = text, streaming = streaming)
    _state.value = _state.value.copy(messages = list)
  }
}

