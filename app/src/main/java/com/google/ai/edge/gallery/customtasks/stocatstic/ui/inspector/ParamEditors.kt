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

package com.google.ai.edge.gallery.customtasks.stocatstic.ui.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.MatchMode
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ParamSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ValueKind
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.theme.PixelPalette
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Renders a single [ParamSpec] of a capability. Supports every [ValueKind] uniformly so both
 * existing and newly-added tasks get a richer UI for free.
 *
 * @param onSpecial invoked when a parameter of kind [ValueKind.SPECIAL] wants to open its
 *                  dedicated editor (e.g. the multi-modal AI configuration sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParamField(
  spec: ParamSpec,
  value: JsonElement?,
  onChange: (JsonElement) -> Unit,
  onSpecial: () -> Unit = {},
) {
  Column(Modifier.fillMaxWidth()) {
    Text(
      spec.label, color = PixelPalette.onDark,
      style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
    )
    if (spec.help.isNotBlank()) {
      Text(
        spec.help,
        color = PixelPalette.onDarkMuted,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
      )
    }
    Spacer(Modifier.padding(top = 4.dp))
    when (spec.kind) {
      ValueKind.BOOL -> {
        val b = (value as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false
        Switch(checked = b, onCheckedChange = { onChange(JsonPrimitive(it)) })
      }
      ValueKind.ENUM -> EnumDropdown(spec.enumValues, (value as? JsonPrimitive)?.contentOrNull) {
        onChange(JsonPrimitive(it))
      }
      ValueKind.STRING_LIST -> StringListEditor(value as? JsonArray, onChange)
      ValueKind.SPECIAL -> {
        OutlinedButton(onClick = onSpecial, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Outlined.Tune, null)
          Spacer(Modifier.width(6.dp))
          Text("Configurar…")
        }
      }
      else -> FlatTextField(
        text = (value as? JsonPrimitive)?.contentOrNull.orEmpty(),
        onChange = { newText ->
          onChange(
            when (spec.kind) {
              ValueKind.INT, ValueKind.LONG, ValueKind.DURATION_MS ->
                newText.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(newText)
              ValueKind.DOUBLE ->
                newText.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(newText)
              else -> JsonPrimitive(newText)
            },
          )
        },
      )
    }
  }
}

// =========================================================================================
// Internal editors
// =========================================================================================

@Composable
private fun FlatTextField(text: String, onChange: (String) -> Unit) {
  Box(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(PixelPalette.softGlow.copy(alpha = 0.75f))
      .padding(horizontal = 12.dp, vertical = 10.dp),
  ) {
    BasicTextField(
      value = text,
      onValueChange = onChange,
      textStyle = TextStyle(color = PixelPalette.onDark, fontSize = 14.sp),
      cursorBrush = SolidColor(PixelPalette.moon),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(values: List<String>, current: String?, onPick: (String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val label = when (current) {
    null, "" -> values.firstOrNull().orEmpty()
    MatchMode.ANY.name -> "Cualquiera"
    MatchMode.ONE.name -> "Uno concreto"
    MatchMode.LIST.name -> "Varios concretos"
    else -> current
  }
  val menuVerticalOffset = (-((values.size * 52) + 12)).dp
  Box {
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
      Text(label, modifier = Modifier.weight(1f))
      Icon(Icons.Outlined.ArrowDropDown, null)
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      offset = DpOffset(x = 0.dp, y = menuVerticalOffset),
    ) {
      values.forEach { v ->
        val display = when (v) {
          MatchMode.ANY.name -> "Cualquiera"
          MatchMode.ONE.name -> "Uno concreto"
          MatchMode.LIST.name -> "Varios concretos"
          else -> v
        }
        DropdownMenuItem(text = { Text(display) }, onClick = { expanded = false; onPick(v) })
      }
    }
  }
}

@Composable
private fun StringListEditor(value: JsonArray?, onChange: (JsonArray) -> Unit) {
  val items = remember(value) {
    (value ?: JsonArray(emptyList())).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
      .toMutableList()
  }
  var draft by remember { mutableStateOf("") }
  Column(Modifier.fillMaxWidth()) {
    items.forEachIndexed { idx, s ->
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
          Modifier
            .padding(vertical = 2.dp)
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(PixelPalette.softGlow.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        ) { Text(s, color = PixelPalette.onDark) }
        IconButton(onClick = {
          val next = items.toMutableList().also { it.removeAt(idx) }
          onChange(JsonArray(next.map { JsonPrimitive(it) }))
        }) { Icon(Icons.Outlined.Close, null) }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Box(
        Modifier
          .weight(1f)
          .clip(RoundedCornerShape(10.dp))
          .background(PixelPalette.softGlow.copy(alpha = 0.75f))
          .padding(horizontal = 10.dp, vertical = 8.dp),
      ) {
        BasicTextField(
          value = draft, onValueChange = { draft = it },
          textStyle = TextStyle(color = PixelPalette.onDark, fontSize = 14.sp),
          cursorBrush = SolidColor(PixelPalette.moon),
        )
      }
      IconButton(onClick = {
        val v = draft.trim()
        if (v.isNotEmpty()) {
          val next = items + v
          onChange(JsonArray(next.map { JsonPrimitive(it) }))
          draft = ""
        }
      }) { Icon(Icons.Outlined.Add, null) }
    }
  }
}

/** Helper: merge/replace a single key in a config [JsonObject]. */
fun JsonObject.withKey(key: String, element: JsonElement): JsonObject =
  JsonObject(this + (key to element))


