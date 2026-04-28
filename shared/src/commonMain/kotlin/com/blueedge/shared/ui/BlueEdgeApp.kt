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
import com.blueedge.shared.ui.navigation.RootNavigator
import com.blueedge.shared.ui.theme.BlueEdgeTheme

@Composable
fun BlueEdgeApp() {
  BlueEdgeTheme {
    Surface(modifier = Modifier.fillMaxSize()) {
      RootNavigator()
    }
  }
}


