/*
 * Copyright 2026 Blue Edge contributors.
 *
 * First-run consent screen — gates the rest of the navigator until both the
 * Blue Edge Terms of Service and the Gemma Terms of Use have been accepted.
 * The acceptance is persisted through [SettingsRepository] so the gate is
 * shown only once per install (and stays in sync with the Android app).
 */
package com.blueedge.shared.ui.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConsentScreen(
  onAccepted: () -> Unit,
) {
  var tos by remember { mutableStateOf(false) }
  var gemma by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(24.dp),
  ) {
    Text("Welcome to Blue Edge", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
      "On-device AI on your terms. Before you continue, please review and " +
        "accept the following.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    ConsentBlock(
      title = "Blue Edge Terms of Service",
      body = "Blue Edge runs models locally on your device. You are responsible " +
        "for the prompts you submit and the content you generate. The app does " +
        "not transmit prompts or generations to Blue Edge servers.",
      checked = tos,
      onCheckedChange = { tos = it },
      checkboxLabel = "I have read and accept the Blue Edge Terms of Service.",
    )
    Spacer(Modifier.height(20.dp))
    ConsentBlock(
      title = "Gemma Terms of Use",
      body = "Some downloadable models (e.g., Gemma) are provided by third " +
        "parties under their own license. Downloading and using a Gemma model " +
        "implies you accept Google's Gemma Terms of Use available at " +
        "ai.google.dev/gemma/terms.",
      checked = gemma,
      onCheckedChange = { gemma = it },
      checkboxLabel = "I accept the Gemma Terms of Use for any Gemma model I run.",
    )
    Spacer(Modifier.height(32.dp))

    Button(
      onClick = onAccepted,
      enabled = tos && gemma,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Continue")
    }
  }
}

@Composable
private fun ConsentBlock(
  title: String,
  body: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  checkboxLabel: String,
) {
  Column {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Checkbox(checked = checked, onCheckedChange = onCheckedChange)
      Text(checkboxLabel, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

