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

package com.google.ai.edge.gallery.runtime

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.litertlm.Contents
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "ModelLifecycleManager"

/** Owner of the currently loaded LLM. */
enum class ModelOwner { NONE, CHAT, WORKFLOW }

/** High-level state of the active LLM in the process. */
sealed class ModelState {
  object Unloaded : ModelState()
  data class Loading(val modelName: String) : ModelState()
  data class Loaded(val model: Model, val owner: ModelOwner) : ModelState()
}

/**
 * Central coordinator of the single on-device LLM across the chat page ("pantalla principal") and
 * StoCATstic workflows. Implements exclusive ownership with reference counting for workflows:
 *
 *  • `onChatEntered(model)`    — chat takes ownership (load happens via ModelManagerViewModel).
 *  • `onChatLeft()`            — chat releases ownership (model is NOT unloaded here).
 *  • `acquireWorkflow()`       — a workflow with AI nodes started.
 *  • `releaseWorkflow()`       — a workflow with AI nodes finished.
 *  • `onAppBackgrounded()`     — if no workflows need the model, unload; otherwise start
 *                                 keep-alive service and mark WORKFLOW as owner.
 *  • `onAppForegrounded()`     — stop keep-alive service.
 *  • `awaitReady()`            — suspend until the model is Loaded; auto-loads for workflows.
 */
@Singleton
class ModelLifecycleManager @Inject constructor(
  @ApplicationContext private val appContext: Context,
) {
  private val _state = MutableStateFlow<ModelState>(ModelState.Unloaded)
  val state: StateFlow<ModelState> = _state.asStateFlow()

  private val _runningAi = MutableStateFlow(false)
  /** True while one or more workflows with AI nodes are in flight. */
  val runningAi: StateFlow<Boolean> = _runningAi.asStateFlow()

  private val _pauseDialogRequested = MutableStateFlow(false)
  /** Flips to true when user enters chat while [runningAi] is true; UI observes this. */
  val pauseDialogRequested: StateFlow<Boolean> = _pauseDialogRequested.asStateFlow()

  private val workflowRefs = AtomicInteger(0)
  private val loadMutex = Mutex()
  /** Serializes generate() calls between chat and workflows on the shared conversation. */
  val generationMutex = Mutex()

  @Volatile private var appInForeground: Boolean = false
  @Volatile private var currentModel: Model? = null

  /** Optional callback to cancel all currently-running workflow AI branches. */
  @Volatile var cancelAllRunningAi: (() -> Unit)? = null

  // ---- Model registration -----------------------------------------------------------------------

  /** Called by tasks when they pick the model the runtime should consider "active". */
  fun registerActiveModel(model: Model?) {
    currentModel = model
    if (model == null && _state.value !is ModelState.Unloaded) {
      _state.value = ModelState.Unloaded
    }
  }

  /** Notify that the chat route became visible with [model]; transitions ownership to CHAT. */
  fun onChatEntered(model: Model) {
    currentModel = model
    val cur = _state.value
    if (cur is ModelState.Loaded) {
      _state.value = cur.copy(model = model, owner = ModelOwner.CHAT)
    }
    // If a workflow is running in background, surface pause dialog.
    if (workflowRefs.get() > 0) {
      _pauseDialogRequested.value = true
    }
  }

  fun onChatLeft() {
    val cur = _state.value
    if (cur is ModelState.Loaded && cur.owner == ModelOwner.CHAT) {
      _state.value = cur.copy(owner = if (workflowRefs.get() > 0) ModelOwner.WORKFLOW else ModelOwner.CHAT)
    }
    _pauseDialogRequested.value = false
  }

  fun dismissPauseDialog() { _pauseDialogRequested.value = false }

  fun markLoaded(model: Model, owner: ModelOwner) {
    currentModel = model
    _state.value = ModelState.Loaded(model, owner)
  }

  fun markUnloaded() {
    _state.value = ModelState.Unloaded
  }

  // ---- Workflow reference counting --------------------------------------------------------------

  fun acquireWorkflow() {
    val n = workflowRefs.incrementAndGet()
    Log.d(TAG, "acquireWorkflow -> $n")
    if (n == 1) {
      _runningAi.value = true
      // If app is in background and model is unloaded, keep-alive service will be started by
      // onAppBackgrounded; if already backgrounded, start it now to guarantee process liveness.
      if (!appInForeground) startKeepAliveService()
    }
  }

  fun releaseWorkflow() {
    val n = workflowRefs.decrementAndGet().coerceAtLeast(0)
    Log.d(TAG, "releaseWorkflow -> $n")
    if (n == 0) {
      _runningAi.value = false
      stopKeepAliveService()
      // If app is in background, unload now; otherwise the chat still owns it.
      if (!appInForeground) unloadNow()
    }
  }

  fun hasActiveWorkflow(): Boolean = workflowRefs.get() > 0

  // ---- Foreground/background --------------------------------------------------------------------

  fun onAppForegrounded() {
    appInForeground = true
    stopKeepAliveService()
  }

  fun onAppBackgrounded() {
    appInForeground = false
    if (workflowRefs.get() > 0) {
      // Transfer ownership so chat doesn't think it still owns the model.
      val cur = _state.value
      if (cur is ModelState.Loaded) _state.value = cur.copy(owner = ModelOwner.WORKFLOW)
      startKeepAliveService()
    } else {
      unloadNow()
    }
  }

  // ---- Await + auto-load for workflows ----------------------------------------------------------

  /**
   * Suspends until the model is loaded. If not loaded and we have a registered model, it auto-loads
   * with StoCATstic defaults (workflow ownership). Capabilities use this to wait transparently.
   */
  suspend fun awaitReady(): LlmModelInstance {
    val existing = currentModel?.instance as? LlmModelInstance
    if (existing != null) return existing
    loadMutex.withLock {
      val m = currentModel ?: error("No hay modelo seleccionado para StoCATstic")
      val cached = m.instance as? LlmModelInstance
      if (cached != null) return cached
      Log.d(TAG, "awaitReady: autoloading '${m.name}' for WORKFLOW")
      _state.value = ModelState.Loading(m.name)
      suspendCancellableCoroutine<Unit> { cont ->
        LlmChatModelHelper.initialize(
          context = appContext,
          model = m,
          supportImage = false,
          supportAudio = false,
          onDone = { err ->
            if (err.isEmpty() && m.instance is LlmModelInstance) {
              if (cont.isActive) cont.resume(Unit)
            } else if (cont.isActive) {
              cont.resumeWithException(RuntimeException(err.ifEmpty { "init failed" }))
            }
          },
          systemInstruction = Contents.of(
            "Eres un asistente que genera flujos de trabajo JSON y responde preguntas breves " +
              "para alimentar un nodo de un grafo."
          ),
          tools = emptyList(),
          enableConversationConstrainedDecoding = false,
        )
      }
      markLoaded(m, ModelOwner.WORKFLOW)
    }
    return currentModel!!.instance as LlmModelInstance
  }

  // ---- Private helpers --------------------------------------------------------------------------

  private fun unloadNow() {
    val m = currentModel ?: run { _state.value = ModelState.Unloaded; return }
    if (m.instance == null) { _state.value = ModelState.Unloaded; return }
    Log.d(TAG, "unloadNow: releasing '${m.name}'")
    try {
      LlmChatModelHelper.cleanUp(model = m) { /* no-op */ }
    } catch (t: Throwable) {
      Log.w(TAG, "cleanUp failed: ${t.message}")
    }
    _state.value = ModelState.Unloaded
  }

  private fun startKeepAliveService() {
    try {
      val intent = Intent(appContext, com.google.ai.edge.gallery.runtime.ModelKeepAliveService::class.java)
      appContext.startForegroundService(intent)
    } catch (t: Throwable) {
      Log.w(TAG, "startKeepAliveService failed: ${t.message}")
    }
  }

  private fun stopKeepAliveService() {
    try {
      appContext.stopService(Intent(appContext, com.google.ai.edge.gallery.runtime.ModelKeepAliveService::class.java))
    } catch (_: Throwable) { /* ignore */ }
  }
}

