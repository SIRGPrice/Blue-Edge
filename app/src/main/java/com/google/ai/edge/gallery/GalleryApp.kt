/*
 * Copyright 2025 Google LLC
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

package com.google.ai.edge.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.ai.edge.gallery.common.CrashReporter
import com.google.ai.edge.gallery.ui.common.CrashRecoveryDialog
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.navigation.GalleryNavHost

/** Top level composable representing the main screen of the application. */
@Composable
fun GalleryApp(
  navController: NavHostController = rememberNavController(),
  modelManagerViewModel: ModelManagerViewModel,
) {
  GalleryNavHost(navController = navController, modelManagerViewModel = modelManagerViewModel)

  // Si el arranque previo terminó en crash, mostramos un diálogo no-bloqueante con
  // causa y pasos de resolución. Se carga en una corrutina aparte para no penalizar
  // el primer frame.
  val context = LocalContext.current
  var crashReport by remember { mutableStateOf<CrashReporter.Report?>(null) }
  LaunchedEffect(Unit) {
    crashReport = try {
      CrashReporter.readLast(context)
    } catch (_: Throwable) {
      null
    }
  }
  crashReport?.let { report ->
    CrashRecoveryDialog(report = report) {
      crashReport = null
      CrashReporter.clear(context)
    }
  }
}
