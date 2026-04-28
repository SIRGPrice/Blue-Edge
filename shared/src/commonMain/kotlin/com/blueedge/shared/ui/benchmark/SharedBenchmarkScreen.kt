/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Shared benchmark screen. Lets the user pick one of the locally available
 * models, run a fixed prompt against the platform [LlmEngine] and inspect
 * prefill/decode/throughput metrics.
 */
package com.blueedge.shared.ui.benchmark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

data class BenchmarkSummary(
  val prefillMs: Long = 0L,
  val decodeMs: Long = 0L,
  val outputTokens: Int = 0,
) {
  val tokensPerSecond: Double
    get() = if (decodeMs <= 0L || outputTokens <= 0) 0.0 else outputTokens * 1000.0 / decodeMs
}

@Composable
fun SharedBenchmarkScreen(
  viewModel: BenchmarkViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  Column(
    modifier = modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Text("Benchmark", style = MaterialTheme.typography.titleLarge)
    }
    Text(
      "Run latency and throughput comparisons for local models.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    state.errorMessage?.let { msg ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(msg, modifier = Modifier.weight(1f).padding(8.dp))
          TextButton(onClick = { viewModel.dismissError() }) { Text("Dismiss") }
        }
      }
    }

    OutlinedTextField(
      value = state.prompt,
      onValueChange = viewModel::setPrompt,
      enabled = !state.isRunning,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Prompt") },
    )

    ModelPicker(
      models = state.availableModels,
      selectedPath = state.selectedModelPath,
      enabled = !state.isRunning,
      onSelect = viewModel::selectModel,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Button(
        enabled = !state.isRunning && state.selectedModelPath != null,
        onClick = { viewModel.run() },
      ) {
        if (state.isRunning) {
          CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp))
          Spacer(Modifier.width(8.dp))
          Text("Running…")
        } else {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null)
          Spacer(Modifier.width(4.dp))
          Text("Run benchmark")
        }
      }
      if (state.isRunning) {
        OutlinedButton(onClick = { viewModel.cancel() }) { Text("Cancel") }
      }
      OutlinedButton(enabled = !state.isRunning, onClick = { viewModel.refreshModels() }) { Text("Refresh") }
    }

    val latest = state.latest
    if (latest == null) EmptyBenchmarkCard() else BenchmarkSummaryCard(latest)
  }
}

@Composable
private fun ModelPicker(
  models: List<ModelFile>,
  selectedPath: String?,
  enabled: Boolean,
  onSelect: (String) -> Unit,
) {
  if (models.isEmpty()) {
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(12.dp)) {
        Text("No local models found", style = MaterialTheme.typography.titleSmall)
        Text(
          "Download or import a model from the Models screen to enable benchmarking.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    return
  }
  LazyColumn(
    modifier = Modifier.fillMaxWidth().height(220.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    items(models, key = { it.absolutePath }) { file ->
      val selected = file.absolutePath == selectedPath
      Card(
        colors = if (selected) {
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else CardDefaults.cardColors(),
        modifier = Modifier
          .fillMaxWidth()
          .let { if (enabled) it.clickable { onSelect(file.absolutePath) } else it },
      ) {
        Column(Modifier.padding(10.dp)) {
          Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            formatBytes(file.sizeInBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyBenchmarkCard() {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("No benchmark result yet", style = MaterialTheme.typography.titleMedium)
      Text(
        "Pick a model and tap Run benchmark to measure prefill, decode and throughput.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun BenchmarkSummaryCard(summary: BenchmarkSummary) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      MetricRow("Prefill", "${summary.prefillMs} ms")
      MetricRow("Decode", "${summary.decodeMs} ms")
      MetricRow("Output tokens", summary.outputTokens.toString())
      MetricRow("Throughput", "${oneDecimal(summary.tokensPerSecond)} tok/s")
    }
  }
}

@Composable
private fun MetricRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleSmall)
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
