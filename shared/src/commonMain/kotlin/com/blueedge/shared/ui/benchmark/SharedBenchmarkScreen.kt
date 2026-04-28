/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Shared benchmark screen foundation. The heavy Android benchmark runner is
 * still in `:app`; this screen provides the cross-platform destination and
 * result model used by the Voyager route.
 */
package com.blueedge.shared.ui.benchmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
  latest: BenchmarkSummary? = null,
  modifier: Modifier = Modifier,
) {
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
    if (latest == null) {
      EmptyBenchmarkCard()
    } else {
      BenchmarkSummaryCard(latest)
    }
  }
}

@Composable
private fun EmptyBenchmarkCard() {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("No benchmark result yet", style = MaterialTheme.typography.titleMedium)
      Text(
        "The shared screen is ready. The next step is wiring model selection and the Android/iOS runner.",
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

private fun oneDecimal(value: Double): String {
  val rounded = kotlin.math.round(value * 10.0) / 10.0
  val raw = rounded.toString()
  return if (raw.endsWith(".0")) raw.dropLast(2) else raw
}

