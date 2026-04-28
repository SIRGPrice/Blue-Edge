/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Lightweight shared model manager. Lists first-level files/directories under
 * the platform model storage root and lets the user pin which `.task` bundle
 * the chat screen should boot into next time.
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blueedge.shared.domain.ModelFile

@Composable
fun SharedModelManagerScreen(
  viewModel: ModelManagerViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("Model storage", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
      if (state.canImport) {
        FilledTonalButton(
          onClick = { viewModel.importModel() },
          enabled = !state.isImporting && !state.isLoading,
        ) { Text(if (state.isImporting) "Picking…" else "Import") }
      }
      OutlinedButton(onClick = { viewModel.refresh() }, enabled = !state.isLoading) { Text("Refresh") }
    }
    Text(
      state.baseDir,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )

    state.message?.let { msg ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (state.isLoading) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(8.dp))
          }
          Text(msg, modifier = Modifier.weight(1f).padding(8.dp))
          TextButton(onClick = { viewModel.dismissMessage() }) { Text("OK") }
        }
      }
    }

    if (state.files.isEmpty()) {
      EmptyModelStorageMessage()
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.files, key = { it.absolutePath }) { file ->
          ModelFileRow(
            file = file,
            isActive = file.absolutePath == state.activeModelPath,
            isBusy = state.isLoading,
            onUse = { viewModel.useModel(file) },
          )
        }
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
private fun ModelFileRow(
  file: ModelFile,
  isActive: Boolean,
  isBusy: Boolean,
  onUse: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = if (isActive) {
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    } else CardDefaults.cardColors(),
  ) {
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
        Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          if (file.isDirectory) "Folder" else formatBytes(file.sizeInBytes),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (isActive) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
      }
      if (!file.isDirectory) {
        FilledTonalButton(onClick = onUse, enabled = !isBusy && !isActive) {
          Text(if (isActive) "In use" else "Use")
        }
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
