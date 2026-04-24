/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
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
        "Un flujo de StoCATstic estÃ¡ usando el modelo. Puedes pausarlo para usar el chat " +
          "ahora, o mantenerlo en segundo plano (el chat quedarÃ¡ en cola)."
      )
    },
    confirmButton = { TextButton(onClick = onPause) { Text("Pausar y abrir chat") } },
    dismissButton = { TextButton(onClick = onKeep) { Text("Mantener en segundo plano") } },
  )
}


