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

package com.google.ai.edge.gallery.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Dialog shown on the main chat screen when the user comes back to foreground (or enters the chat)
 * while a StoCATstic workflow is actively running an AI node. The chat cannot fully engage the
 * model until either the workflow is paused or the user chooses to keep it in background.
 */
@Composable
fun ActiveWorkflowPauseDialog(
  onPause: () -> Unit,
  onKeep: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onKeep,
    title = { Text("Tarea de IA en curso") },
    text = {
      Text(
        "Un flujo de StoCATstic está usando el modelo. Puedes pausarlo para usar el chat " +
          "ahora, o mantenerlo en segundo plano (el chat quedará en cola)."
      )
    },
    confirmButton = { TextButton(onClick = onPause) { Text("Pausar y abrir chat") } },
    dismissButton = { TextButton(onClick = onKeep) { Text("Mantener en segundo plano") } },
  )
}

