/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Compose Multiplatform chat screen — first real shared UI. Re-implements,
 * in a platform-agnostic way, what the Android-only `ui/llmchat/LlmChatScreen.kt`
 * does. Future iterations will port the full feature set (model picker,
 * benchmark button, image input).
 */
package com.blueedge.shared.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blueedge.shared.chat.ChatMessage
import com.blueedge.shared.chat.ChatViewModel
import com.blueedge.shared.chat.Role

/** Default suggestions shown when the conversation is empty. */
private val DEFAULT_QUICK_PROMPTS = listOf(
  "Explain quantum entanglement simply",
  "Summarize today's notes",
  "Write a haiku about the ocean",
  "Give me a recipe with chickpeas",
)

@Composable
fun ChatScreen(
  viewModel: ChatViewModel = remember { ChatViewModel() },
  quickPrompts: List<String> = DEFAULT_QUICK_PROMPTS,
) {
  val state by viewModel.state.collectAsState()
  val listState = rememberLazyListState()

  LaunchedEffect(state.messages.size) {
    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
  }

  Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
    // Toolbar: clear + stop. Hidden when irrelevant to keep the UI quiet.
    if (state.messages.isNotEmpty() || state.isGenerating) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (state.isGenerating) {
          OutlinedButton(onClick = { viewModel.cancelGeneration() }) { Text("Stop") }
          Spacer(Modifier.width(8.dp))
        }
        TextButton(
          onClick = { viewModel.clearMessages() },
          enabled = state.messages.isNotEmpty(),
        ) { Text("Clear") }
      }
    }

    state.errorMessage?.let { msg ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(msg, modifier = Modifier.weight(1f).padding(8.dp))
          TextButton(onClick = { viewModel.dismissError() }) { Text("Dismiss") }
        }
      }
    }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      if (state.messages.isEmpty() && !state.isGenerating) {
        QuickPromptsPanel(
          prompts = quickPrompts,
          onPick = { viewModel.send(it) },
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(state.messages) { msg -> MessageBubble(msg) }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Composer(
      enabled = !state.isGenerating,
      onSend = viewModel::send,
    )
  }
}

@Composable
private fun QuickPromptsPanel(
  prompts: List<String>,
  onPick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(8.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      "Try a prompt",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      prompts.forEach { p ->
        AssistChip(onClick = { onPick(p) }, label = { Text(p) })
      }
    }
  }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
  val isUser = msg.role == Role.USER
  val bg = if (isUser) MaterialTheme.colorScheme.primaryContainer
           else MaterialTheme.colorScheme.surfaceVariant
  val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
    Card(
      colors = CardDefaults.cardColors(containerColor = bg),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.widthIn(max = 480.dp),
    ) {
      Text(
        text = msg.text + if (msg.streaming) " ▍" else "",
        modifier = Modifier.padding(10.dp),
      )
    }
  }
}

@Composable
private fun Composer(
  enabled: Boolean,
  onSend: (String) -> Unit,
) {
  var input by remember { mutableStateOf("") }
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
      value = input,
      onValueChange = { input = it },
      enabled = enabled,
      modifier = Modifier.weight(1f),
      placeholder = { Text(if (enabled) "Ask Blue Edge…" else "Generating…") },
    )
    Spacer(Modifier.width(8.dp))
    Button(
      enabled = enabled,
      onClick = {
        val toSend = input.trim()
        if (toSend.isNotEmpty()) {
          onSend(toSend)
          input = ""
        }
      },
    ) { Text("Send") }
  }
}

// `Modifier.weight` is provided by enclosing RowScope/ColumnScope; no shim needed.


