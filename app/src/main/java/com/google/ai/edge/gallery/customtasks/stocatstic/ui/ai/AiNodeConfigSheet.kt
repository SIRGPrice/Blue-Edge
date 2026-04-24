/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.theme.PixelPalette
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Dedicated configuration sheet for the "Decidir con IA" special task. This node is the only one
 * allowed to expose a full screen — every other task is configured through the standardised
 * parameter editors. The sheet lets the user compose a multimodal request:
 *
 *   * **Prompt**: free-form text with `{{input}}`, `{{sender}}`, `{{body}}`, `{{var.X}}` placeholders.
 *   * **Incluir salida previa**: toggle that auto-appends upstream text when the template does not.
 *   * **Imágenes**: list of picked image URIs (gallery → ContentResolver).
 *   * **Audio**: list of picked audio URIs.
 *   * **Usar adjunto del nodo anterior**: if a reactive task produced an `attachmentUri`
 *     (e.g. voicemail), route it to the right modality automatically.
 *
 * Everything is persisted under the node's `config.config` key as a [JsonObject]; the
 * [com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin.LlmDecisionCapability]
 * reads it at runtime. Output is plain text (in `out`), so this task chains with every other
 * capability (reply.*, notify, tts, branch, ...).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiNodeConfigSheet(
  initialConfig: JsonObject,
  onSave: (JsonObject) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  // Read initial state.
  var prompt by remember {
    mutableStateOf(
      (initialConfig["prompt"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        .ifBlank { "Resume brevemente: {{input}}" },
    )
  }
  var includeInput by remember {
    mutableStateOf(
      (initialConfig["includeInputText"] as? JsonPrimitive)?.contentOrNull
        ?.toBooleanStrictOrNull() ?: true,
    )
  }
  var useUpstream by remember {
    mutableStateOf(
      (initialConfig["useUpstreamAttachment"] as? JsonPrimitive)?.contentOrNull
        ?.toBooleanStrictOrNull() ?: true,
    )
  }
  val images = remember {
    mutableStateListOf<String>().apply {
      (initialConfig["images"] as? JsonArray)?.forEach {
        (it as? JsonPrimitive)?.contentOrNull?.let(::add)
      }
    }
  }
  val audio = remember {
    mutableStateListOf<String>().apply {
      (initialConfig["audio"] as? JsonArray)?.forEach {
        (it as? JsonPrimitive)?.contentOrNull?.let(::add)
      }
    }
  }

  val pickImage = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(images::add) }
  val pickAudio = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(audio::add) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PixelPalette.deepSky,
    dragHandle = null,
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
      // Header
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = PixelPalette.catBlueDeep)
        Spacer(Modifier.width(8.dp))
        Text(
          "Configurar IA multimodal",
          color = PixelPalette.onDark,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, null, tint = PixelPalette.onDarkMuted)
        }
      }
      Text(
        "Tarea especial: acepta texto + imágenes + audio y devuelve texto. " +
          "Compatible con cualquier otra tarea (antes y después).",
        color = PixelPalette.onDarkMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
      )

      // Prompt -----------------------------------------------------------------------------
      Text("Prompt", color = PixelPalette.onDark, style = MaterialTheme.typography.labelLarge)
      Text(
        "Admite {{input}}, {{sender}}, {{body}} y {{var.X}}.",
        color = PixelPalette.onDarkMuted, style = MaterialTheme.typography.labelSmall,
      )
      Box(
        Modifier
          .fillMaxWidth()
          .heightIn(min = 100.dp, max = 220.dp)
          .padding(top = 6.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(PixelPalette.softGlow.copy(alpha = 0.75f))
          .padding(horizontal = 12.dp, vertical = 10.dp),
      ) {
        BasicTextField(
          value = prompt, onValueChange = { prompt = it },
          textStyle = TextStyle(color = PixelPalette.onDark, fontSize = 14.sp),
          cursorBrush = SolidColor(PixelPalette.moon),
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Spacer(Modifier.padding(top = 10.dp))
      ToggleRow("Incluir salida de la tarea anterior", includeInput) { includeInput = it }
      ToggleRow("Usar adjunto del nodo anterior (voz/imagen)", useUpstream) { useUpstream = it }

      HorizontalDivider(Modifier.padding(vertical = 12.dp), color = PixelPalette.softGlow)

      // Attachments ------------------------------------------------------------------------
      AttachmentSection(
        title = "Imágenes",
        icon = Icons.Outlined.Image,
        items = images,
        onAdd = { pickImage.launch(arrayOf("image/*")) },
        onRemove = { images.remove(it) },
      )
      Spacer(Modifier.padding(top = 10.dp))
      AttachmentSection(
        title = "Audio",
        icon = Icons.Outlined.AudioFile,
        items = audio,
        onAdd = { pickAudio.launch(arrayOf("audio/*")) },
        onRemove = { audio.remove(it) },
      )

      HorizontalDivider(Modifier.padding(vertical = 14.dp), color = PixelPalette.softGlow)

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
        Button(
          onClick = {
            onSave(
              JsonObject(
                mapOf(
                  "prompt" to JsonPrimitive(prompt),
                  "includeInputText" to JsonPrimitive(includeInput),
                  "useUpstreamAttachment" to JsonPrimitive(useUpstream),
                  "images" to JsonArray(images.map { JsonPrimitive(it) }),
                  "audio" to JsonArray(audio.map { JsonPrimitive(it) }),
                ),
              ),
            )
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = PixelPalette.catBlueDeep),
          modifier = Modifier.weight(1f),
        ) { Text("Guardar") }
      }
    }
  }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
  ) {
    Text(label, color = PixelPalette.onDark, modifier = Modifier.weight(1f))
    Switch(checked = value, onCheckedChange = onChange)
  }
}

@Composable
private fun AttachmentSection(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  items: List<String>,
  onAdd: () -> Unit,
  onRemove: (String) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = PixelPalette.onDark)
    Spacer(Modifier.width(6.dp))
    Text(title, color = PixelPalette.onDark, style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.weight(1f))
    OutlinedButton(onClick = onAdd) { Text("Añadir") }
  }
  if (items.isEmpty()) {
    Text("(ninguno)", color = PixelPalette.onDarkMuted,
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(start = 4.dp, top = 2.dp))
  } else {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 120.dp)) {
      items(items) { uri ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) {
          Card(
            colors = CardDefaults.cardColors(containerColor = PixelPalette.softGlow.copy(alpha = 0.6f)),
            modifier = Modifier.weight(1f),
          ) {
            Text(
              uri,
              color = PixelPalette.onDark,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              maxLines = 1,
            )
          }
          IconButton(onClick = { onRemove(uri) }) { Icon(Icons.Outlined.Close, null) }
        }
      }
    }
  }
}

