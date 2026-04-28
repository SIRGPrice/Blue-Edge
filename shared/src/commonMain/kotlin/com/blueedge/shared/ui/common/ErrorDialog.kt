/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform mirror of `:app/.../ui/common/ErrorDialog.kt`.
 */
package com.blueedge.shared.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ErrorDialog(error: String, onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          "Error",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
          error,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          Button(onClick = onDismiss) { Text("Close") }
        }
      }
    }
  }
}

