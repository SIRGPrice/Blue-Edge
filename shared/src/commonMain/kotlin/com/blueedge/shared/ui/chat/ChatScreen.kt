/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Compose Multiplatform chat screen — first real shared UI. Re-implements,
 * in a platform-agnostic way, what the Android-only `ui/llmchat/LlmChatScreen.kt`
 * does. Future iterations will port the full feature set (model picker,
 * benchmark button, image input).
 */
package com.blueedge.shared.ui.chat

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

@Composable
fun ChatScreen(viewModel: ChatViewModel = remember { ChatViewModel() }) {
  val state by viewModel.state.collectAsState()
  val listState = rememberLazyListState()

  LaunchedEffect(state.messages.size) {
    if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
  }

  Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
    state.errorMessage?.let { msg ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      ) {
        Text(msg, modifier = Modifier.padding(8.dp))
      }
    }
    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(state.messages) { msg -> MessageBubble(msg) }
    }
    Spacer(Modifier.height(8.dp))
    Composer(onSend = viewModel::send)
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
private fun Composer(onSend: (String) -> Unit) {
  var input by remember { mutableStateOf("") }
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
      value = input,
      onValueChange = { input = it },
      modifier = Modifier.weight(1f),
      placeholder = { Text("Ask Blue Edge…") },
    )
    Spacer(Modifier.width(8.dp))
    Button(onClick = {
      val toSend = input.trim()
      if (toSend.isNotEmpty()) {
        onSend(toSend)
        input = ""
      }
    }) { Text("Send") }
  }
}

// `Modifier.weight` is provided by enclosing RowScope/ColumnScope; no shim needed.



