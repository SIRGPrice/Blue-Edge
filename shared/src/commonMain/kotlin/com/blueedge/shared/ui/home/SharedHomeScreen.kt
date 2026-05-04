/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform Home screen. Renders the built-in [Task] catalog as a
 * category-tabbed grid. Tap a tile to open its dedicated screen via the
 * provided callbacks.
 *
 * Pure Compose Multiplatform — no Android-specific APIs. All icons resolve
 * through `IconRegistry.iconFor(...)`.
 */
package com.blueedge.shared.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.blueedge.shared.domain.Task
import com.blueedge.shared.ui.common.iconFor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedHomeScreen(
  viewModel: HomeViewModel,
  onOpenTask: (Task) -> Unit,
  onOpenModelManager: () -> Unit,
  onOpenBenchmark: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val ui by viewModel.state.collectAsState()
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Blue Edge", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
              "On-device AI",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        actions = {
          IconButton(onClick = onOpenModelManager) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = "Models")
          }
          IconButton(onClick = onOpenBenchmark) {
            Icon(Icons.Rounded.Analytics, contentDescription = "Benchmark")
          }
          IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      OutlinedTextField(
        value = ui.query,
        onValueChange = viewModel::setQuery,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        placeholder = { Text("Search tasks") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      )
      if (ui.categories.size > 1) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ui.categories.forEach { cat ->
            val selected = cat.id == ui.selectedCategoryId
            FilterChip(
              selected = selected,
              onClick = { viewModel.selectCategory(cat.id) },
              label = { Text(cat.label ?: cat.id.replaceFirstChar { it.uppercase() }) },
            )
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        items(ui.visibleTasks, key = { it.id }) { task ->
          TaskTile(
            task = task,
            onClick = {
              if (task.experimental) {
                scope.launch { snackbar.showSnackbar("${task.label} — coming soon") }
              } else {
                onOpenTask(task)
              }
            },
          )
        }
      }
    }
  }
}

@Composable
private fun TaskTile(task: Task, onClick: () -> Unit) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(end = 8.dp),
      ) {
        Icon(
          imageVector = iconFor(task.iconKey),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(10.dp),
        )
      }
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            task.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
          )
        }
        if (task.shortDescription.isNotBlank()) {
          Text(
            task.shortDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp).alpha(0.9f),
            maxLines = 2,
          )
        }
        Row(
          modifier = Modifier.padding(top = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (task.newFeature) {
            AssistChip(
              onClick = onClick,
              label = { Text("New") },
              colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
              ),
            )
          }
          if (task.experimental) {
            AssistChip(
              onClick = onClick,
              label = { Text("Experimental") },
            )
          }
        }
      }
    }
  }
}

