/*
 * Copyright 2026 SIRGPrice and Blue Edge contributors
 * Part of Blue Edge, a heavily modified app fork based on Google AI Edge Gallery.
 * Upstream project originally published by Google LLC:
 * https://github.com/google-ai-edge/gallery
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.customtasks.stocatstic.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.customtasks.stocatstic.data.DangerousActionEntry
import com.google.ai.edge.gallery.customtasks.stocatstic.data.DangerousActionKind
import com.google.ai.edge.gallery.customtasks.stocatstic.data.DangerousActionLog
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.theme.PixelPalette
import java.text.DateFormat
import java.util.Date

/**
 * Sheet listing every outbound (dangerous) action performed by any workflow — sent SMS, messaging
 * replies and emails. Data is read from the shared [DangerousActionLog] singleton.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DangerousActionHistorySheet(
  log: DangerousActionLog,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val entries by log.entries.collectAsState()
  val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PixelPalette.deepSky,
    dragHandle = null,
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Notifications, null, tint = PixelPalette.moon)
        Spacer(Modifier.width(8.dp))
        Text(
          "Historial de acciones", color = PixelPalette.onDark,
          style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        if (entries.isNotEmpty()) {
          IconButton(onClick = { log.clear() }) {
            Icon(Icons.Outlined.DeleteSweep, null, tint = PixelPalette.onDarkMuted)
          }
        }
      }
      Text(
        "Envíos (mensajes, correos, SMS…) realizados automáticamente por tus flujos.",
        color = PixelPalette.onDarkMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
      )

      if (entries.isEmpty()) {
        Text(
          "Aún no se ha realizado ninguna acción.",
          color = PixelPalette.onDarkMuted,
          modifier = Modifier.padding(vertical = 24.dp),
        )
      } else {
        LazyColumn(
          Modifier.fillMaxWidth().heightIn(max = 520.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(entries) { e -> EntryRow(e, dateFmt.format(Date(e.timestamp))) }
        }
      }
    }
  }
}

@Composable
private fun EntryRow(e: DangerousActionEntry, dateStr: String) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = PixelPalette.softGlow.copy(alpha = 0.6f)),
  ) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
      Icon(
        if (e.success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
        null,
        tint = if (e.success) PixelPalette.success else PixelPalette.failure,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
      )
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(
          "${kindLabel(e.kind)} → ${e.recipient.ifBlank { "(sin destinatario)" }}",
          color = PixelPalette.onDark,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          dateStr, color = PixelPalette.onDarkMuted,
          style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.padding(top = 4.dp))
        Text(
          e.body.take(300),
          color = PixelPalette.onDark,
          style = MaterialTheme.typography.bodySmall,
        )
        if (e.message.isNotBlank()) {
          Text(
            e.message,
            color = PixelPalette.onDarkMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp),
          )
        }
      }
    }
  }
}

private fun kindLabel(k: DangerousActionKind): String = when (k) {
  DangerousActionKind.SMS -> "SMS"
  DangerousActionKind.WHATSAPP -> "WhatsApp"
  DangerousActionKind.TELEGRAM -> "Telegram"
  DangerousActionKind.DISCORD -> "Discord"
  DangerousActionKind.EMAIL -> "Correo"
  DangerousActionKind.SHARE -> "Compartir"
  DangerousActionKind.CALL -> "Llamada"
}





