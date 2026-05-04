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

package com.google.ai.edge.gallery.customtasks.stocatstic.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.customtasks.stocatstic.data.WorkflowRepository
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Workflow
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.WorkflowTrigger

/**
 * WorkManager worker that runs a workflow when its periodic trigger fires.
 *
 * Uses the process-global [WorkflowRunner] holder (set at app start by Hilt) to access engine and
 * repository without requiring androidx.hilt.work, keeping dependencies minimal.
 */
class WorkflowWorker(appContext: Context, params: WorkerParameters) :
  CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val id = inputData.getString(KEY_WORKFLOW_ID) ?: return Result.failure()
    val engine = WorkflowRunner.engine ?: return Result.retry()
    val repo = WorkflowRunner.repository ?: return Result.retry()
    val wf = repo.get(id) ?: return Result.failure()
    val ok = engine.runBlocking(wf)
    return if (ok) Result.success() else Result.retry()
  }

  companion object { const val KEY_WORKFLOW_ID = "workflow_id" }
}

/** Broadcast receiver for AlarmManager-scheduled triggers. */
class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_FIRE) return
    val id = intent.getStringExtra(EXTRA_WORKFLOW_ID) ?: return
    val engine = WorkflowRunner.engine ?: return
    val repo = WorkflowRunner.repository ?: return
    val wf = repo.get(id) ?: return
    engine.run(wf)
  }
  companion object {
    const val ACTION_FIRE = "com.google.ai.edge.gallery.stocatstic.ALARM_FIRE"
    const val EXTRA_WORKFLOW_ID = "workflow_id"
  }
}

/** Boot receiver to reschedule triggers after device reboot. */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
      intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
    val scheduler = WorkflowRunner.scheduler ?: return
    scheduler.syncAll()
    val engine = WorkflowRunner.engine ?: return
    val repo = WorkflowRunner.repository ?: return
    repo.workflows.value.filter { wf ->
      wf.enabled && wf.triggers.any { it is WorkflowTrigger.BootCompleted }
    }.forEach { engine.run(it) }
  }
}

/** Process-global entry points set by Hilt at Application start. */
object WorkflowRunner {
  @Volatile var engine: WorkflowEngine? = null
  @Volatile var repository: WorkflowRepository? = null
  @Volatile var scheduler: TriggerScheduler? = null
}

