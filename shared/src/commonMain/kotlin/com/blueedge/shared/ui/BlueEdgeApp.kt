/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Compose Multiplatform entry point. Hosts the shared UI graph that will
 * progressively absorb the screens currently in `:app/src/main/java/.../ui`.
 */
package com.blueedge.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blueedge.shared.platform.providePlatform
import com.blueedge.shared.ui.chat.ChatScreen

@Composable
fun BlueEdgeApp() {
  MaterialTheme {
    Surface(modifier = Modifier.fillMaxSize()) {
      val platform = remember { providePlatform() }
      Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text(
          "Blue Edge — ${platform.name} ${platform.osVersion}",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ChatScreen()
      }
    }
  }
}


