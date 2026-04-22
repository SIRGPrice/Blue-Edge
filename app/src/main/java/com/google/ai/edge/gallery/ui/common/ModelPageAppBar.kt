/*
 * Copyright 2025 Google LLC
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

package com.google.ai.edge.gallery.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MapsUgc
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.common.CustomTaskTopBarAction
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.convertValueToTargetType
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPageAppBar(
  task: Task,
  model: Model,
  modelManagerViewModel: ModelManagerViewModel,
  onBackClicked: () -> Unit,
  onModelSelected: (prev: Model, cur: Model) -> Unit,
  inProgress: Boolean,
  modelPreparing: Boolean,
  modifier: Modifier = Modifier,
  isResettingSession: Boolean = false,
  onResetSessionClicked: (Model) -> Unit = {},
  canShowResetSessionButton: Boolean = false,
  hideModelSelector: Boolean = false,
  useThemeColor: Boolean = false,
  onConfigChanged: (oldConfigValues: Map<String, Any>, newConfigValues: Map<String, Any>) -> Unit =
    { _, _ ->
    },
  allowEditingSystemPrompt: Boolean = false,
  curSystemPrompt: String = "",
  onSystemPromptChanged: (String) -> Unit = {},
  hideBackButton: Boolean = false,
  onSkillClicked: () -> Unit = {},
  showSkillButton: Boolean = false,
  onTinyGardenClicked: () -> Unit = {},
  showTinyGardenButton: Boolean = false,
  onAppSettingsClicked: () -> Unit = {},
  showAppSettingsButton: Boolean = false,
  customLeadingAction: CustomTaskTopBarAction? = null,
) {
  var showConfigDialog by remember { mutableStateOf(false) }
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val context = LocalContext.current
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[model.name]
  val modelInitializationStatus = modelManagerUiState.modelInitializationStatus[model.name]
  val isModelInitializing =
    modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZING
  val isModelInitialized =
    modelInitializationStatus?.status == ModelInitializationStatusType.INITIALIZED
  val isStoCATstic = task.id == BuiltInTaskId.LLM_TINY_GARDEN
  val stoCATsticContentColor = Color.White

  CenterAlignedTopAppBar(
    title = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Task type.
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (isStoCATstic) {
            val goldenGradient = Brush.linearGradient(colors = listOf(Color(0xFFFFD700), Color(0xFFFFA500)))
            val blueGradient = Brush.linearGradient(colors = listOf(Color(0xFF85B1F8), Color(0xFF3174F1)))
            Text(
              buildAnnotatedString {
                withStyle(SpanStyle(brush = blueGradient)) { append("Sto") }
                withStyle(SpanStyle(brush = goldenGradient)) { append("CAT") }
                withStyle(SpanStyle(brush = blueGradient)) { append("stic Assistant") }
              },
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            )
          } else {
            Icon(
              painter = painterResource(R.drawable.view_in_ar_24px),
              modifier = Modifier.size(20.dp),
              contentDescription = null,
              tint = Color.Unspecified,
            )
            Text(
              stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                brush = Brush.linearGradient(colors = listOf(Color(0xFF85B1F8), Color(0xFF3174F1))),
              ),
            )
          }
        }

        // Model chips pager.
        if (!hideModelSelector) {
          val enableModelPickerChip = !isModelInitializing && !inProgress
          ModelPickerChip(
            enabled = enableModelPickerChip,
            task = task,
            initialModel = model,
            modelManagerViewModel = modelManagerViewModel,
            onModelSelected = onModelSelected,
          )
        }
      }
    },
    modifier = modifier,
    colors =
      if (isStoCATstic) {
        TopAppBarDefaults.centerAlignedTopAppBarColors(
          containerColor = Color.Transparent,
          scrolledContainerColor = Color.Transparent,
          navigationIconContentColor = stoCATsticContentColor,
          actionIconContentColor = stoCATsticContentColor,
          titleContentColor = stoCATsticContentColor,
        )
      } else {
        TopAppBarDefaults.centerAlignedTopAppBarColors()
      },
    // Left side: Game and App Settings buttons (hidden when model not downloaded).
    navigationIcon = {
      val downloadSucceeded = curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
      val iconSize = 20.dp
      Row(verticalAlignment = Alignment.CenterVertically) {
        customLeadingAction?.let { action ->
          val enableButton = !isModelInitializing && !inProgress
          IconButton(
            onClick = action.onClick,
            enabled = enableButton,
            modifier = Modifier.alpha(if (enableButton) 1f else 0.5f),
          ) {
            Icon(
              imageVector = action.icon,
              contentDescription = action.contentDescription,
              modifier = Modifier.size(iconSize),
              tint = if (isStoCATstic) stoCATsticContentColor else LocalContentColor.current,
            )
          }
        }
        if (showTinyGardenButton && downloadSucceeded) {
          val enableTinyGardenButton = !isModelInitializing && !inProgress
          IconButton(onClick = onTinyGardenClicked, enabled = enableTinyGardenButton,
            modifier = Modifier.alpha(if (enableTinyGardenButton) 1f else 0.5f)) {
            Icon(
              painter = painterResource(R.drawable.graph_1_24px),
              contentDescription = "StoCATstic Assistant",
              modifier = Modifier.size(iconSize),
              tint = Color.Unspecified,
            )
          }
        }
        if (showAppSettingsButton && downloadSucceeded) {
          val enableButton = !isModelInitializing && !inProgress
          IconButton(onClick = onAppSettingsClicked, enabled = enableButton,
            modifier = Modifier.alpha(if (enableButton) 1f else 0.5f)) {
            Icon(
              painter = painterResource(R.drawable.settings_24px),
              contentDescription = "App Settings",
              modifier = Modifier.size(iconSize),
              tint = Color.Unspecified,
            )
          }
        }
      }
    },
    // Right side: Settings and Reset session.
    actions = {
      val downloadSucceeded = curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
      val showConfigButton = model.configs.isNotEmpty() && downloadSucceeded
      val showResetSessionButton = canShowResetSessionButton && downloadSucceeded
      val iconSize = 20.dp

      if (showConfigButton) {
        val enableConfigButton = !isModelInitializing && !inProgress && isModelInitialized
        IconButton(
          onClick = { showConfigDialog = true },
          enabled = enableConfigButton,
          modifier = Modifier.alpha(if (enableConfigButton) 1f else 0.5f),
        ) {
          Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = stringResource(R.string.cd_model_settings_icon),
            modifier = Modifier.size(iconSize),
              tint = if (isStoCATstic) stoCATsticContentColor else LocalContentColor.current,
          )
        }
      }
      if (showResetSessionButton) {
        if (isResettingSession) {
          Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
              strokeWidth = 2.dp,
              modifier = Modifier.size(16.dp),
            )
          }
        } else {
          val enableResetButton =
            !isModelInitializing && !modelPreparing && !inProgress && isModelInitialized
          IconButton(
            onClick = { onResetSessionClicked(model) },
            enabled = enableResetButton,
            modifier = Modifier.alpha(if (enableResetButton) 1f else 0.5f),
          ) {
            Icon(
              imageVector = Icons.Outlined.MapsUgc,
              contentDescription = stringResource(R.string.cd_reset_session_icon),
              modifier = Modifier.size(iconSize),
            )
          }
        }
      }
    },
  )

  // Config dialog.
  if (showConfigDialog) {
    // Remove the reset conversation turn count config for non-tiny-garden tasks.
    //
    // This may happen when user imports a model with "enable tiny garden" turned on and use the
    // model in another non-tiny-garden task.
    val modelConfigs = model.configs.toMutableList()
    if (task.id != BuiltInTaskId.LLM_TINY_GARDEN) {
      modelConfigs.removeIf { it.key == ConfigKeys.RESET_CONVERSATION_TURN_COUNT }
    }
    if (!task.allowThinking()) {
      modelConfigs.removeIf { it.key == ConfigKeys.ENABLE_THINKING }
    }
    ConfigDialog(
      title = "Configurations",
      configs = modelConfigs,
      initialValues = model.configValues,
      onDismissed = { showConfigDialog = false },
      onOk = { curConfigValues, oldSystemPrompt, newSystemPrompt ->
        // Hide config dialog.
        showConfigDialog = false

        // Check if the configs are changed or not. Also check if the model needs to be
        // re-initialized.
        var same = true
        var needReinitialization = false
        for (config in modelConfigs) {
          val key = config.key.label
          val oldValue =
            convertValueToTargetType(
              value = model.configValues.getValue(key),
              valueType = config.valueType,
            )
          val newValue =
            convertValueToTargetType(
              value = curConfigValues.getValue(key),
              valueType = config.valueType,
            )
          if (oldValue != newValue) {
            same = false
            if (config.needReinitialization) {
              needReinitialization = true
            }
            break
          }
        }
        if (same) {
          if (newSystemPrompt != oldSystemPrompt) {
            onSystemPromptChanged(newSystemPrompt)
          }
          return@ConfigDialog
        }

        // Save the config values to Model.
        val oldConfigValues = model.configValues
        model.prevConfigValues = oldConfigValues
        model.configValues = curConfigValues
        modelManagerViewModel.updateConfigValuesUpdateTrigger()

        if (!task.handleModelConfigChangesInTask) {
          // Force to re-initialize the model with the new configs.
          if (needReinitialization) {
            modelManagerViewModel.initializeModel(
              context = context,
              task = task,
              model = model,
              force = true,
              onDone = {
                if (oldSystemPrompt != newSystemPrompt) {
                  onSystemPromptChanged(newSystemPrompt)
                }
              },
            )
          }

          // Notify.
          onConfigChanged(oldConfigValues, model.configValues)
        }
      },
      showSystemPromptEditorTab = allowEditingSystemPrompt,
      defaultSystemPrompt = task.defaultSystemPrompt,
      curSystemPrompt = curSystemPrompt,
    )
  }
}
