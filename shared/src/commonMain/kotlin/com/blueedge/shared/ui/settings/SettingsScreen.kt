/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Shared settings screen — theme switcher and TOS reset for now.
 * Backed by `SettingsRepository` so changes are persisted across launches
 * on both Android (SharedPreferences) and iOS (NSUserDefaults).
 */
package com.blueedge.shared.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blueedge.shared.storage.SettingsRepository
import com.blueedge.shared.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
  settings: SettingsRepository,
  modifier: Modifier = Modifier,
) {
  var theme by remember { mutableStateOf(settings.themeMode) }
  Column(
    modifier = modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Settings", style = MaterialTheme.typography.titleLarge)

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp)) {
        Text("Theme", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ThemeMode.entries.forEach { mode ->
            FilterChip(
              selected = theme == mode,
              onClick = {
                theme = mode
                settings.themeMode = mode
              },
              label = { Text(mode.name.lowercase().replaceFirstChar { it.titlecase() }) },
            )
          }
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp)) {
        Text("Last loaded model", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
          settings.lastLoadedModelPath ?: "(none)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
          onClick = { settings.lastLoadedModelPath = null },
          enabled = settings.lastLoadedModelPath != null,
        ) { Text("Clear") }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(12.dp)) {
        Text("Privacy & terms", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text("Blue Edge ToS: ${if (settings.tosAccepted) "accepted" else "not accepted"}",
          style = MaterialTheme.typography.bodyMedium)
        Text("Gemma terms: ${if (settings.gemmaTermsAccepted) "accepted" else "not accepted"}",
          style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
          settings.tosAccepted = false
          settings.gemmaTermsAccepted = false
        }) { Text("Reset consent") }
      }
    }
  }
}

