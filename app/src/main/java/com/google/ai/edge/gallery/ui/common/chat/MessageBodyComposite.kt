package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBodyComposite(message: ChatMessageComposite) {
  Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
    // Text content
    if (message.content.isNotEmpty()) {
      Text(
        text = message.content,
        style = MaterialTheme.typography.bodyLarge,
      )
    }

    // Thumbnails row for images and audio
    if (message.bitmaps.isNotEmpty() || message.audioClips.isNotEmpty()) {
      FlowRow(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        // Image thumbnails
        for (bitmap in message.bitmaps) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
              .size(48.dp)
              .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
          )
        }

        // Audio clip thumbnails
        for (audioClip in message.audioClips) {
          AudioPlaybackPanel(
            audioData = audioClip.audioData,
            sampleRate = audioClip.sampleRate,
            isRecording = false,
            modifier = Modifier.size(width = 80.dp, height = 36.dp),
            onDarkBg = true,
          )
        }
      }
    }
  }
}

