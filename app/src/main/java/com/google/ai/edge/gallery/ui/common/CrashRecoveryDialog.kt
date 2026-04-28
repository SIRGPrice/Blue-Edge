/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.common.CrashReporter

/**
 * Diálogo que se muestra al arrancar la app cuando se detecta un crash previo
 * persistido por [CrashReporter]. Explica al usuario:
 *  - Qué pasó (categoría + mensaje real de la excepción).
 *  - Por qué pudo pasar (motivo legible).
 *  - Cómo resolverlo (pasos accionables).
 *
 * Acciones:
 *  - Copiar el informe completo al portapapeles.
 *  - Ver el stack trace completo (toggle).
 *  - Cerrar (borra el reporte persistido para que no vuelva a aparecer).
 */
@Composable
fun CrashRecoveryDialog(report: CrashReporter.Report, onDismiss: () -> Unit) {
  val context = LocalContext.current
  var showStack by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(20.dp),
    title = {
      Text(
        text = "⚠ ${report.category.title}",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.error,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SectionLabel("¿Qué ha pasado?")
        Text(
          text = report.category.reason,
          style = MaterialTheme.typography.bodyMedium,
        )

        SectionLabel("Detalles técnicos")
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            if (report.timestamp.isNotEmpty()) {
              Text(
                "Fecha: ${report.timestamp}",
                style = MaterialTheme.typography.bodySmall,
              )
            }
            Text(
              "Excepción: ${report.exceptionClass}",
              style = MaterialTheme.typography.bodySmall,
            )
            if (!report.message.isNullOrEmpty()) {
              Text(
                "Mensaje: ${report.message}",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }

        SectionLabel("Cómo resolverlo")
        Text(
          text = report.category.resolution,
          style = MaterialTheme.typography.bodyMedium,
        )

        if (showStack) {
          SectionLabel("Stack trace")
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = report.fullStack,
              modifier = Modifier.padding(8.dp),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }

        Spacer(Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(onClick = { showStack = !showStack }) {
            Text(if (showStack) "Ocultar detalles" else "Ver stack trace")
          }
          OutlinedButton(onClick = { copyToClipboard(context, report.fullStack) }) {
            Text("Copiar informe")
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Entendido") }
    },
  )
}

@Composable
private fun SectionLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
  )
}

private fun copyToClipboard(context: Context, text: String) {
  try {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("BlueEdge crash report", text))
  } catch (_: Throwable) {
    // No bloqueamos la UI por un fallo del portapapeles.
  }
}

