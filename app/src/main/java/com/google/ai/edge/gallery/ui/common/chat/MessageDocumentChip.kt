/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.common.chat.rag.DocumentIndexingStatus
import com.google.ai.edge.gallery.ui.common.chat.rag.LocalChatAttachments
import kotlinx.coroutines.flow.MutableStateFlow

/** Verde brillante para señalizar "documento listo / 100 % procesado". Se mantiene
 *  fijo (no theme-dependent) para que el feedback sea inequívoco en cualquier modo. */
private val BrightGreen = Color(0xFF22C55E)

/**
 * Chip rendered inside a chat bubble to represent an attached document. Mirrors the
 * styling of the chips shown in the input area, but additionally displays a *realistic*
 * processing indicator (a circular progress wheel that animates from 0 % to 100 % using
 * coarse pipeline checkpoints) while the document is being extracted, chunked and
 * indexed by [com.google.ai.edge.gallery.ui.common.chat.rag.ChatAttachmentsCoordinator].
 *
 * Once the document is READY the wheel is replaced by a tiny check icon. If the
 * pipeline FAILED the wheel is replaced by an error icon.
 *
 * For messages restored from history (or rendered after the coordinator has discarded
 * its state) we simply show the static document icon — the document already finished
 * processing in the past.
 */
@Composable
fun MessageDocumentChip(doc: AttachedDocumentInfo) {
  val coordinator = LocalChatAttachments.current

  // When the coordinator isn't installed (previews / tests), fall back to empty flows so
  // the rest of the composable can read uniformly via collectAsState().
  val statesFlow = coordinator?.indexingStates
    ?: remember { MutableStateFlow(emptyMap<String, DocumentIndexingStatus>()) }
  val progressFlow = coordinator?.indexingProgress
    ?: remember { MutableStateFlow(emptyMap<String, Float>()) }
  val statesMap by statesFlow.collectAsState()
  val progressMap by progressFlow.collectAsState()

  val status: DocumentIndexingStatus? = statesMap[doc.uriKey]
  val rawProgress: Float = progressMap[doc.uriKey] ?: when (status) {
    DocumentIndexingStatus.READY -> 1f
    DocumentIndexingStatus.FAILED -> 0f
    DocumentIndexingStatus.INDEXING -> 0f
    null -> 1f // unknown / discarded → treat as done so we don't spin forever.
  }
  // Smoothly animate towards the latest checkpoint; this is what makes the spinner
  // feel "realistic" instead of jumping in big steps.
  val animatedProgress by animateFloatAsState(
    targetValue = rawProgress.coerceIn(0f, 1f),
    label = "doc-indexing-progress",
  )

  val bg =
    if (doc.isPersistent) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
  val fg =
    if (doc.isPersistent) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
      .clip(RoundedCornerShape(14.dp))
      .background(bg)
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp)
      .widthIn(max = 240.dp),
  ) {
    // Leading icon + progress/check overlay. El contenedor es algo mayor que la
    // miniatura para que el check quede claramente abajo-derecha de ella, no encima
    // del centro del documento.
    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
      Icon(
        Icons.Outlined.Description,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = fg,
      )
      when (status) {
        DocumentIndexingStatus.INDEXING -> {
          CircularProgressIndicator(
            progress = { animatedProgress.coerceAtLeast(0.02f) },
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.dp,
            color = fg,
            trackColor = fg.copy(alpha = 0.18f),
          )
        }
        DocumentIndexingStatus.READY, null -> {
          // Check verde brillante abajo-derecha de la miniatura. `null` representa
          // estados históricos/descartados; si el mensaje existe, el documento ya fue
          // procesado, así que también debe verse como listo.
          Box(
            modifier = Modifier
              .size(16.dp)
              .align(Alignment.BottomEnd)
              .clip(RoundedCornerShape(8.dp))
              .background(BrightGreen),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Rounded.Check,
              contentDescription = null,
              modifier = Modifier.size(12.dp),
              tint = Color.White,
            )
          }
        }
        DocumentIndexingStatus.FAILED -> {
          Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(14.dp).align(Alignment.BottomEnd),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }

    Column {
      Text(
        text = doc.displayName,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val subtitle = when (status) {
        DocumentIndexingStatus.INDEXING -> {
          val pct = (animatedProgress * 100f).toInt().coerceIn(0, 99)
          "Procesando $pct% · " + (if (doc.isPersistent) "permanente" else "temporal")
        }
        DocumentIndexingStatus.FAILED -> "Error al procesar"
        DocumentIndexingStatus.READY, null ->
          if (doc.isPersistent) "Permanente" else "Temporal"
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = fg.copy(alpha = 0.75f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
