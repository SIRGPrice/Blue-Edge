/*
 * Copyright 2026 Google LLC
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

package com.google.ai.edge.gallery.customtasks.agentchat

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.common.ToolProgressAgentAction
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageInfo
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageType
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageCollapsableProgressPanel
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.SendMessageTrigger
import com.google.ai.edge.gallery.ui.llmchat.LlmChatScreen
import com.google.ai.edge.gallery.ui.llmchat.LlmChatViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.litertlm.tool

private const val TAG = "AGAgentChatScreen"

data class ToolInfo(
  val icon: ImageVector,
  val name: String,
  val description: String,
  val examplePrompt: String,
)

val AVAILABLE_TOOLS: List<ToolInfo> =
  listOf()

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  agentTools: AgentTools,
  viewModel: LlmChatViewModel = hiltViewModel(),
  onTinyGardenClicked: () -> Unit = {},
  onAppSettingsClicked: () -> Unit = {},
) {
  val context = LocalContext.current
  agentTools.context = context
  var curSystemPrompt by remember { mutableStateOf(task.defaultSystemPrompt) }
  val systemPromptUpdatedMessage = stringResource(R.string.system_prompt_updated)
  var sendMessageTrigger by remember { mutableStateOf<SendMessageTrigger?>(null) }
  var showToolsSheet by remember { mutableStateOf(false) }

  LlmChatScreen(
    modelManagerViewModel = modelManagerViewModel,
    taskId = BuiltInTaskId.LLM_AGENT_CHAT,
    navigateUp = navigateUp,
    onSkillClicked = {},
    onFirstToken = { model ->
      updateProgressPanel(viewModel = viewModel, model = model, agentTools = agentTools)
    },
    onGenerateResponseDone = { model ->
      updateProgressPanel(viewModel = viewModel, model = model, agentTools = agentTools)
    },
    onResetSessionClickedOverride = { task, model ->
      resetSession(
        viewModel,
        modelManagerViewModel,
        task,
        curSystemPrompt,
        agentTools,
      )
    },
    showImagePicker = true,
    showAudioPicker = true,
    onTinyGardenClicked = onTinyGardenClicked,
    showTinyGardenButton = true,
    onAppSettingsClicked = onAppSettingsClicked,
    showAppSettingsButton = true,
    composableBelowMessageList = { model ->
      val actionChannel = agentTools.actionChannel
      val doneIcon = Icons.Outlined.LocalLibrary
      val currentModel by androidx.compose.runtime.rememberUpdatedState(model)
      LaunchedEffect(actionChannel) {
        for (action in actionChannel) {
          Log.d(TAG, "Handling action: $action")
          when (action) {
            is ToolProgressAgentAction -> {
              viewModel.updateCollapsableProgressPanelMessage(
                model = currentModel,
                title = action.label,
                inProgress = action.inProgress,
                doneIcon = doneIcon,
                addItemTitle = action.addItemTitle,
                addItemDescription = action.addItemDescription,
                customData = action.customData,
              )
            }
          }
        }
      }
    },
    allowEditingSystemPrompt = false,
    curSystemPrompt = curSystemPrompt,
    onSystemPromptChanged = {},
    emptyStateComposable = { model ->
      Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
          !WindowInsets.isImeVisible,
          enter = fadeIn(animationSpec = tween(200)),
          exit = fadeOut(animationSpec = tween(200)),
        ) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
              modifier =
                Modifier.align(Alignment.Center)
                  .padding(horizontal = 48.dp)
                  .padding(bottom = 48.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                "Blue Edge",
                style =
                  MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Medium,
                    brush =
                      Brush.linearGradient(colors = listOf(Color(0xFF85B1F8), Color(0xFF3174F1))),
                  ),
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
              )
              Text(
                "Your on-device AI assistant. Chat naturally, send audio or images, and use powerful tools — all running locally on your device.",
                style =
                  MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }
    },
    sendMessageTrigger = sendMessageTrigger,
  )

  // Tools bottom sheet
  if (showToolsSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsState()
    val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
    val selectedModel = modelManagerUiState.selectedModel
    val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[selectedModel.name]
    val canSend = modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZED &&
        !uiState.isResettingSession && !uiState.inProgress
    ModalBottomSheet(
      onDismissRequest = { showToolsSheet = false },
      sheetState = sheetState,
    ) {
      Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
          "Available Tools",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        for (tool in AVAILABLE_TOOLS) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .then(
                if (tool.examplePrompt.isNotEmpty() && canSend) {
                  Modifier.clickable {
                    showToolsSheet = false
                    sendMessageTrigger = SendMessageTrigger(
                      model = selectedModel,
                      messages = listOf(ChatMessageText(content = tool.examplePrompt, side = ChatSide.USER)),
                    )
                  }
                } else Modifier
              )
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(tool.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
              Text(tool.name, style = MaterialTheme.typography.bodyLarge)
              Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "Tap a tool with an example to try it, or just ask Blue Edge naturally.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp),
        )
      }
    }
  }
}

private fun updateProgressPanel(viewModel: LlmChatViewModel, model: Model, agentTools: AgentTools) {
  val lastProgressPanelMessage =
    viewModel.getLastMessageWithType(
      model = model,
      type = ChatMessageType.COLLAPSABLE_PROGRESS_PANEL,
    )
  if (
    lastProgressPanelMessage != null &&
      lastProgressPanelMessage is ChatMessageCollapsableProgressPanel
  ) {
    if (lastProgressPanelMessage.title.startsWith("Loading")) {
      agentTools.sendAgentAction(
        ToolProgressAgentAction(
          label = lastProgressPanelMessage.title.replace("Loading", "Loaded"),
          inProgress = false,
        )
      )
    } else if (lastProgressPanelMessage.title.startsWith("Calling")) {
      agentTools.sendAgentAction(
        ToolProgressAgentAction(
          label = lastProgressPanelMessage.title.replace("Calling", "Called"),
          inProgress = false,
        )
      )
    } else if (lastProgressPanelMessage.title.startsWith("Executing")) {
      agentTools.sendAgentAction(
        ToolProgressAgentAction(
          label = lastProgressPanelMessage.title.replace("Executing", "Executed"),
          inProgress = false,
        )
      )
    } else if (lastProgressPanelMessage.title.startsWith("Sending")) {
      agentTools.sendAgentAction(
        ToolProgressAgentAction(
          label = lastProgressPanelMessage.title.replace("Sending", "Sent"),
          inProgress = false,
        )
      )
    } else if (lastProgressPanelMessage.title.startsWith("Opening")) {
      agentTools.sendAgentAction(
        ToolProgressAgentAction(
          label = lastProgressPanelMessage.title.replace("Opening", "Opened"),
          inProgress = false,
        )
      )
    } else if (lastProgressPanelMessage.title.startsWith("Querying")) {
      agentTools.sendAgentAction(
        ToolProgressAgentAction(
          label = lastProgressPanelMessage.title.replace("Querying", "Queried"),
          inProgress = false,
        )
      )
    }
  }
}

private fun resetSession(
  viewModel: LlmChatViewModel,
  modelManagerViewModel: ModelManagerViewModel,
  task: Task,
  curSystemPrompt: String,
  agentTools: AgentTools,
  onDone: (Model) -> Unit = {},
) {
  val model = modelManagerViewModel.uiState.value.selectedModel
  viewModel.resetSession(
    task = task,
    model = model,
    systemInstruction = com.google.ai.edge.litertlm.Contents.of(curSystemPrompt),
    tools = listOf(),
    supportImage = true,
    supportAudio = true,
    onDone = { onDone(model) },
    enableConversationConstrainedDecoding = true,
  )
}
