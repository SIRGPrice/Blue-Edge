/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.ActiveModelLlmRunner
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.StocatsticRootScreen
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Contents
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * StoCATstic Assistant — Visual workflow programming task.
 *
 * The task metadata stays registered under [BuiltInTaskId.LLM_TINY_GARDEN] to keep the icon entry
 * point (`graph_1_24px`) and app-bar wiring functional without a cross-cutting refactor; the id is
 * an opaque string as far as navigation is concerned.
 */
class StocatsticTask @Inject constructor(
  private val llmRunner: ActiveModelLlmRunner,
  @Suppress("unused") private val bootstrap: StocatsticBootstrap,
) : CustomTask {

  override val task = Task(
    id = BuiltInTaskId.LLM_TINY_GARDEN,
    label = "StoCATstic Assistant",
    description = "Programa visualmente flujos de trabajo y automatizaciones en el dispositivo. " +
      "Encadena tareas (intents, linterna, notificaciones, TTS, IA…) en serie o paralelo, " +
      "dispáralas manualmente, por horario o por evento. El gato pixelado ejecuta el flujo en vivo.",
    shortDescription = "Programación gráfica de flujos",
    sourceCodeUrl = "",
    category = Category.LLM,
    icon = Icons.Outlined.AccountTree,
    iconVectorResourceId = R.drawable.graph_1_24px,
    agentNameRes = R.string.chat_agent_agent_name,
    models = mutableListOf(),
    handleModelConfigChangesInTask = true,
    experimental = false,
  )

  override fun initializeModelFn(
    context: Context, coroutineScope: CoroutineScope, model: Model, onDone: (String) -> Unit,
  ) {
    llmRunner.activeModel = model
    LlmChatModelHelper.initialize(
      context = context, model = model,
      supportImage = false, supportAudio = false,
      onDone = onDone,
      systemInstruction = Contents.of("Eres un asistente que genera flujos de trabajo JSON y " +
        "responde preguntas breves para alimentar un nodo de un grafo."),
      tools = emptyList(),
      enableConversationConstrainedDecoding = false,
    )
  }

  override fun cleanUpModelFn(
    context: Context, coroutineScope: CoroutineScope, model: Model, onDone: () -> Unit,
  ) {
    if (llmRunner.activeModel == model) llmRunner.activeModel = null
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val d = data as CustomTaskData
    StocatsticRootScreen(
      modelManagerViewModel = d.modelManagerViewModel,
      bottomPadding = d.bottomPadding,
      setAppBarControlsDisabled = d.setAppBarControlsDisabled,
      setTopBarVisible = d.setTopBarVisible,
      setCustomLeadingAction = d.setCustomLeadingAction,
    )
  }
}

