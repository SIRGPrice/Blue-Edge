/*
 * Copyright 2025 Google LLC & Modifications Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge,
 * a heavily modified fork of Google AI Edge Gallery: https://github.com/google-ai-edge/gallery
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
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

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.preview.PreviewModelManagerViewModel
// import com.google.ai.edge.gallery.ui.preview.TASK_TEST1
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.modelitem.StatusIcon
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.labelSmallNarrow

@Composable
fun ModelPicker(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  onModelSelected: (Model) -> Unit,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  var showMemoryWarning by remember { mutableStateOf(false) }
  var modelToPick by remember { mutableStateOf<Model?>(null) }
  var showConfirmDeleteDialog by remember { mutableStateOf(false) }
  var modelToDelete by remember { mutableStateOf<Model?>(null) }
  val context = LocalContext.current

  Column(modifier = Modifier.padding(bottom = 8.dp)) {
    // Title
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        painterResource(R.drawable.playing_cards_24px),
        modifier = Modifier.size(20.dp),
        contentDescription = null,
        tint = Color.Unspecified,
      )
      androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
      BasicText(
        text = "Blue Edge Models",
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
          brush = Brush.linearGradient(colors = listOf(Color(0xFF85B1F8), Color(0xFF3174F1))),
        ),
        color = { Color.Unspecified },
      )
    }

    // Model list.
    for (model in task.models) {
      val selected = model.name == modelManagerUiState.selectedModel.name
      val downloadStatus = modelManagerUiState.modelDownloadStatus[model.name]
      val isDownloaded = downloadStatus?.status == com.google.ai.edge.gallery.data.ModelDownloadStatusType.SUCCEEDED
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
          Modifier.fillMaxWidth()
            .clickable {
              // Show memory warning before proceeding.
              if (isMemoryLow(context = context, model = model)) {
                modelToPick = model
                showMemoryWarning = true
              } else {
                onModelSelected(model)
              }
            }
            .background(
              if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
            )
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
      ) {
        Spacer(modifier = Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            model.displayName.ifEmpty { model.name },
            style = MaterialTheme.typography.bodyMedium,
          )
          if (model.runtimeType != RuntimeType.AICORE) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              StatusIcon(
                task = task,
                model = model,
                downloadStatus = modelManagerUiState.modelDownloadStatus[model.name],
              )
              Text(
                if (model.localFileRelativeDirPathOverride.isEmpty()) {
                  model.sizeInBytes.humanReadableSize()
                } else {
                  "{ext_file_dir}/${model.localFileRelativeDirPathOverride}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = labelSmallNarrow.copy(lineHeight = 10.sp),
              )
            }
          }
        }
        if (isDownloaded && !model.imported) {
          IconButton(onClick = {
            modelToDelete = model
            showConfirmDeleteDialog = true
          }) {
            Icon(
              Icons.Outlined.Delete,
              contentDescription = "Delete model",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        if (selected) {
          Icon(
            Icons.Filled.CheckCircle,
            modifier = Modifier.size(16.dp).padding(end = 4.dp),
            contentDescription = stringResource(R.string.cd_selected_icon),
          )
        }
      }
    }
  }

  if (showConfirmDeleteDialog && modelToDelete != null) {
    AlertDialog(
      onDismissRequest = { showConfirmDeleteDialog = false },
      title = { Text("Delete model?") },
      text = { Text("This will delete \"${modelToDelete!!.displayName.ifEmpty { modelToDelete!!.name }}\" from your device. You can re-download it later.") },
      confirmButton = {
        Button(onClick = {
          modelManagerViewModel.deleteModel(modelToDelete!!)
          showConfirmDeleteDialog = false
          modelToDelete = null
        }) { Text("Delete") }
      },
      dismissButton = {
        OutlinedButton(onClick = { showConfirmDeleteDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showMemoryWarning) {
    MemoryWarningAlert(
      onProceeded = {
        val curModelToPick = modelToPick
        if (curModelToPick != null) {
          onModelSelected(curModelToPick)
        }
        showMemoryWarning = false
      },
      onDismissed = { showMemoryWarning = false },
    )
  }
}

// @Preview(showBackground = true)
// @Composable
// fun ModelPickerPreview() {
//   val context = LocalContext.current

//   GalleryTheme {
//     ModelPicker(
//       task = TASK_TEST1,
//       modelManagerViewModel = PreviewModelManagerViewModel(context = context),
//       onModelSelected = {},
//     )
//   }
// }
