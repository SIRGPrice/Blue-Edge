/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Lightweight shared model manager. It lists first-level files/directories
 * under the platform model storage root and gives iOS/Android a common
 * destination while the full Android-only model-management UI is migrated.
 */
package com.blueedge.shared.ui.modelmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blueedge.shared.domain.ModelFile
import com.blueedge.shared.domain.ModelStorage
import com.blueedge.shared.domain.provideModelStorage

@Composable
fun SharedModelManagerScreen(
  storage: ModelStorage = remember { provideModelStorage() },
  modifier: Modifier = Modifier,
) {
  var files by remember { mutableStateOf(storage.listModelFiles()) }
  LaunchedEffect(storage) {
    files = storage.listModelFiles()
  }
  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    Text("Model storage", style = MaterialTheme.typography.titleLarge)
    Text(
      storage.baseModelsDir,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
    )
    if (files.isEmpty()) {
      EmptyModelStorageMessage()
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(files, key = { it.absolutePath }) { file -> ModelFileRow(file) }
      }
    }
  }
}

@Composable
private fun EmptyModelStorageMessage() {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("No local models found", style = MaterialTheme.typography.titleMedium)
      Text(
        "Downloaded or imported models will appear here.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}

@Composable
private fun ModelFileRow(file: ModelFile) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        if (file.isDirectory) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
          if (file.isDirectory) "Folder" else formatBytes(file.sizeInBytes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes < 1024) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024) return "${oneDecimal(kb)} KB"
  val mb = kb / 1024.0
  if (mb < 1024) return "${oneDecimal(mb)} MB"
  return "${oneDecimal(mb / 1024.0)} GB"
}

private fun oneDecimal(value: Double): String {
  val rounded = kotlin.math.round(value * 10.0) / 10.0
  val raw = rounded.toString()
  return if (raw.endsWith(".0")) raw.dropLast(2) else raw
}



