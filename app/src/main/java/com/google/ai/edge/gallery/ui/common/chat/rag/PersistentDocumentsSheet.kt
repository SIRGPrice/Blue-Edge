/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet that lists every document currently stored in the persistent RAG and
 * lets the user remove them individually. Tapping a row attaches the document to the
 * next message; the "+" header button opens the file picker to add new permanent
 * documents in-place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersistentDocumentsSheet(
  store: PermanentDocumentStore,
  onDismiss: () -> Unit,
  onAttachClicked: (PermanentDocumentRef) -> Unit = {},
  onAddDocuments: (List<DocumentAttachment>) -> Unit = {},
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  var docs by remember { mutableStateOf<List<PermanentDocumentRef>?>(null) }
  var refreshTick by remember { mutableIntStateOf(0) }
  var pendingDelete by remember { mutableStateOf<PermanentDocumentRef?>(null) }

  LaunchedEffect(refreshTick) {
    docs = withContext(Dispatchers.IO) { store.list() }
  }

  // File picker para añadir nuevos documentos permanentes desde este propio panel.
  val pickDocuments = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    if (uris.isEmpty()) return@rememberLauncherForActivityResult
    val resolver = context.contentResolver
    val newDocs = uris.mapNotNull { uri ->
      try {
        resolver.takePersistableUriPermission(
          uri,
          android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      } catch (_: Throwable) { /* picker URIs are read-allowed for the session */ }

      var displayName = uri.lastPathSegment ?: "document"
      var size = -1L
      try {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && !cursor.isNull(nameIdx)) displayName = cursor.getString(nameIdx)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
          }
        }
      } catch (t: Throwable) {
        Log.w("PersistentDocsSheet", "Could not query metadata for $uri", t)
      }

      val ext = displayName.substringAfterLast('.', "").lowercase()
      val mime = resolver.getType(uri)
      val supported = ext in DocumentTextExtractor.SUPPORTED_EXTENSIONS ||
        mime?.startsWith("text/") == true ||
        mime == "application/json" ||
        mime == "application/xml" ||
        mime == "application/rtf" ||
        mime == "application/pdf"
      if (!supported) return@mapNotNull null
      DocumentAttachment(
        uri = uri,
        displayName = displayName,
        sizeBytes = size,
        mimeType = mime,
        scope = AttachmentScope.PERSISTENT,
      )
    }
    if (newDocs.isNotEmpty()) {
      onAddDocuments(newDocs)
      // Refrescar la lista cuando el indexado complete. Como `preIngest` corre en
      // background y `upsert` se hace al final, sondeamos brevemente.
      scope.launch {
        repeat(20) {
          kotlinx.coroutines.delay(500)
          docs = withContext(Dispatchers.IO) { store.list() }
        }
      }
    }
  }

  ModalBottomSheet(
    sheetState = sheetState,
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            "Documentos permanentes",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            "Toca un documento para adjuntarlo a tu próximo mensaje.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = {
          pickDocuments.launch(DocumentTextExtractor.PICKER_MIME_TYPES)
        }) {
          Icon(
            Icons.Rounded.Add,
            contentDescription = "Añadir documento permanente",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
      Spacer(Modifier.height(12.dp))

      val current = docs
      when {
        current == null -> {
          Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
          }
        }
        current.isEmpty() -> {
          Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              "No hay documentos guardados.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        else -> {
          LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(current, key = { it.id }) { doc ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable {
                    onAttachClicked(doc)
                    onDismiss()
                  }
                  .padding(2.dp)
                  .padding(horizontal = 12.dp, vertical = 10.dp),
              ) {
                Icon(
                  Icons.Outlined.Description,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    doc.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                  )
                  Text(
                    formatDocSize(doc.textLength),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { pendingDelete = doc }) {
                  Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Borrar documento",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  pendingDelete?.let { doc ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text("Borrar documento") },
      text = { Text("¿Seguro que quieres borrar “${doc.displayName}” de la memoria permanente?") },
      confirmButton = {
        TextButton(onClick = {
          pendingDelete = null
          scope.launch {
            withContext(Dispatchers.IO) { store.remove(doc.id) }
            refreshTick += 1
          }
        }) { Text("Borrar") }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
      },
    )
  }
}

private fun formatDocSize(chars: Int): String {
  if (chars < 1_000) return "$chars caracteres"
  val k = chars / 1_000.0
  if (k < 1_000.0) return String.format(java.util.Locale.getDefault(), "%.1fK caracteres", k)
  val m = k / 1_000.0
  return String.format(java.util.Locale.getDefault(), "%.2fM caracteres", m)
}
