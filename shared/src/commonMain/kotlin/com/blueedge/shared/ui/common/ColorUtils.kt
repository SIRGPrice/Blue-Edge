/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform mirror of `:app/.../ui/common/ColorUtils.kt`.
 * Uses the shared `Task` from `com.blueedge.shared.domain`.
 */
package com.blueedge.shared.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.blueedge.shared.domain.Task
import com.blueedge.shared.ui.theme.customColors

@Composable
fun getTaskBgColor(task: Task): Color {
  val colors = MaterialTheme.customColors.taskBgColors
  if (colors.isEmpty()) return Color.Transparent
  return colors[task.index.coerceAtLeast(0) % colors.size]
}

@Composable
fun getTaskBgGradientColors(task: Task): List<Color> {
  val gradients = MaterialTheme.customColors.taskBgGradientColors
  if (gradients.isEmpty()) return emptyList()
  return gradients[task.index.coerceAtLeast(0) % gradients.size]
}

@Composable
fun getTaskIconColor(task: Task): Color = getTaskIconColor(task.index)

@Composable
fun getTaskIconColor(index: Int): Color {
  val colors = MaterialTheme.customColors.taskIconColors
  if (colors.isEmpty()) return Color.Transparent
  return colors[index.coerceAtLeast(0) % colors.size]
}

