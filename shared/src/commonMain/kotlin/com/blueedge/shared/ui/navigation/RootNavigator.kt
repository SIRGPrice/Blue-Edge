/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Voyager-based navigation skeleton. Replaces `:app/.../ui/navigation/
 * GalleryNavGraph.kt` (NavController + Compose Navigation) with a fully
 * multiplatform stack so Android and iOS share the exact same screen graph.
 *
 * Screens are intentionally minimal scaffolds — each one points to the
 * existing shared composable (or a placeholder until the corresponding
 * screen is migrated from `:app`). Wire heavier screens by replacing
 * `Content()` bodies as they land in `commonMain`.
 */
package com.blueedge.shared.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.blueedge.shared.chat.ChatViewModel
import com.blueedge.shared.storage.SettingsRepository
import com.blueedge.shared.ui.benchmark.BenchmarkViewModel
import com.blueedge.shared.ui.benchmark.SharedBenchmarkScreen
import com.blueedge.shared.ui.chat.ChatScreen as SharedChatScreen
import com.blueedge.shared.ui.consent.ConsentScreen
import com.blueedge.shared.ui.modelmanager.ModelManagerViewModel
import com.blueedge.shared.ui.modelmanager.SharedModelManagerScreen
import com.blueedge.shared.ui.settings.SettingsScreen as SharedSettingsScreen
import org.koin.mp.KoinPlatform

/** Root navigation entry point used by `BlueEdgeApp`. */
@Composable
fun RootNavigator() {
  val settings = remember { KoinPlatform.getKoin().get<SettingsRepository>() }
  var consented by remember {
    mutableStateOf(settings.tosAccepted && settings.gemmaTermsAccepted)
  }
  if (!consented) {
    ConsentScreen(onAccepted = {
      settings.tosAccepted = true
      settings.gemmaTermsAccepted = true
      consented = true
    })
    return
  }
  Navigator(screen = HomeScreen)
}

// ---------------------------------------------------------------------------
// Screen registry. Adding a new screen = new `Screen` object. Voyager handles
// back-stack, state preservation, and screen models.
// ---------------------------------------------------------------------------

object HomeScreen : Screen {
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    HomeContent(
      onOpenChat = { navigator.push(ChatRoute) },
      onOpenModelManager = { navigator.push(ModelManagerScreen) },
      onOpenBenchmark = { navigator.push(BenchmarkScreen) },
      onOpenSettings = { navigator.push(SettingsRoute) },
    )
  }
}

object ChatRoute : Screen {
  @Composable
  override fun Content() {
    BackScaffold(title = "Chat") {
      val viewModel = remember { KoinPlatform.getKoin().get<ChatViewModel>() }
      SharedChatScreen(viewModel = viewModel)
    }
  }
}

object ModelManagerScreen : Screen {
  @Composable
  override fun Content() {
    BackScaffold(title = "Models") {
      val viewModel = remember { KoinPlatform.getKoin().get<ModelManagerViewModel>() }
      SharedModelManagerScreen(viewModel = viewModel)
    }
  }
}

object BenchmarkScreen : Screen {
  @Composable
  override fun Content() {
    BackScaffold(title = "Benchmark") {
      val viewModel = remember { KoinPlatform.getKoin().get<BenchmarkViewModel>() }
      SharedBenchmarkScreen(viewModel = viewModel)
    }
  }
}

object SettingsRoute : Screen {
  @Composable
  override fun Content() {
    BackScaffold(title = "Settings") {
      val settings = remember { KoinPlatform.getKoin().get<SettingsRepository>() }
      SharedSettingsScreen(settings = settings)
    }
  }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

@Composable
private fun HomeContent(
  onOpenChat: () -> Unit,
  onOpenModelManager: () -> Unit,
  onOpenBenchmark: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  Scaffold { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
    ) {
      Text("Blue Edge", style = MaterialTheme.typography.headlineLarge)
      Text(
        "On-device AI",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 24.dp),
      )
      HomeAction("💬  Chat", onOpenChat)
      HomeAction("📦  Models", onOpenModelManager)
      HomeAction("📊  Benchmark", onOpenBenchmark)
      HomeAction("⚙️  Settings", onOpenSettings)
    }
  }
}

@Composable
private fun HomeAction(label: String, onClick: () -> Unit) {
  androidx.compose.material3.Card(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
  ) {
    Text(label, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(title: String, content: @Composable () -> Unit) {
  val navigator = LocalNavigator.currentOrThrow
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title) },
        navigationIcon = {
          IconButton(onClick = { navigator.pop() }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
  }
}
