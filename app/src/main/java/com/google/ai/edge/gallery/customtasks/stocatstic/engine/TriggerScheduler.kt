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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.ai.edge.gallery.customtasks.stocatstic.data.WorkflowRepository
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Workflow
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.WorkflowTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Registers / unregisters triggers in the Android platform. Keeps runtime side-effects outside the
 * engine so the engine stays pure and testable.
 */
@Singleton
class TriggerScheduler @Inject constructor(
  @ApplicationContext private val ctx: Context,
  private val repo: WorkflowRepository,
  private val bus: ReactiveEventBus,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  /** Active reactive-collector jobs keyed by workflow id (one wrapper job per workflow). */
  private val reactiveJobs = ConcurrentHashMap<String, Job>()

  fun syncAll() {
    repo.workflows.value.forEach { wf ->
      if (wf.enabled) schedule(wf) else cancel(wf)
    }
  }

  fun schedule(workflow: Workflow) {
    cancel(workflow)
    if (!workflow.enabled) return
    // Explicit triggers declared on the workflow.
    workflow.triggers.forEach { t ->
      when (t) {
        is WorkflowTrigger.Periodic -> schedulePeriodic(workflow.id, t)
        is WorkflowTrigger.Alarm -> scheduleAlarm(workflow.id, t)
        is WorkflowTrigger.BootCompleted, is WorkflowTrigger.Manual -> Unit
        is WorkflowTrigger.SmsReceived,
        is WorkflowTrigger.NotificationMatched,
        is WorkflowTrigger.CallMissed -> scheduleReactive(workflow, t)
      }
    }
    // Ergonomic auto-wiring: if the workflow has no explicit reactive trigger but its root
    // node is a reactive "Esperar …" capability, derive the trigger from the node's config.
    // This lets the user simply drop a "Esperar SMS" node at the start of the flow and the
    // workflow will spring to life whenever a matching event arrives.
    if (workflow.triggers.none { it.isReactive() }) {
      derivedReactiveTrigger(workflow)?.let { scheduleReactive(workflow, it) }
    }
  }

  fun cancel(workflow: Workflow) {
    WorkManager.getInstance(ctx).cancelUniqueWork(workTag(workflow.id))
    cancelAlarm(workflow.id)
    reactiveJobs.remove(workflow.id)?.cancel()
  }

  // ----- Reactive triggers --------------------------------------------------------------------

  private fun scheduleReactive(workflow: Workflow, t: WorkflowTrigger) {
    val wfId = workflow.id
    val job = scope.launch {
      bus.events
        .filter { ev -> matches(t, ev) }
        .collect {
          val engine = WorkflowRunner.engine ?: return@collect
          val repo = WorkflowRunner.repository ?: return@collect
          val wf = repo.get(wfId) ?: return@collect
          engine.run(wf)
        }
    }
    // Merge multiple reactive triggers into a single supervisor wrapper so cancel() is trivial.
    reactiveJobs.compute(wfId) { _, existing ->
      existing?.cancel()
      job
    }
  }

  private fun matches(t: WorkflowTrigger, ev: ReactiveEvent): Boolean = when {
    t is WorkflowTrigger.SmsReceived && ev is ReactiveEvent.Sms ->
      matchesSender(ev.sender, t.mode, t.senders)
    t is WorkflowTrigger.NotificationMatched && ev is ReactiveEvent.Notification ->
      (ev.packageName in t.packageNames) && matchesSender(ev.sender, t.mode, t.senders)
    t is WorkflowTrigger.CallMissed && ev is ReactiveEvent.CallMissed ->
      matchesSender(ev.sender, t.mode, t.numbers) && (!t.onlyWhenScreenOff || ev.wasScreenOff)
    else -> false
  }

  private fun WorkflowTrigger.isReactive(): Boolean =
    this is WorkflowTrigger.SmsReceived ||
      this is WorkflowTrigger.NotificationMatched ||
      this is WorkflowTrigger.CallMissed

  /**
   * If the workflow's first node is one of the `trigger.*` "Esperar …" capabilities, build the
   * matching reactive [WorkflowTrigger] from its `mode + senders` config. This way the user
   * doesn't have to separately declare a trigger: placing the capability on the graph is enough.
   */
  private fun derivedReactiveTrigger(workflow: Workflow): WorkflowTrigger? {
    val root = workflow.roots().firstOrNull() ?: return null
    val (mode, allowed) = readModeAndSenders(root.config)
    return when (root.capabilityId) {
      "trigger.sms" -> WorkflowTrigger.SmsReceived(mode, allowed)
      "trigger.whatsapp" -> WorkflowTrigger.NotificationMatched(
        packageNames = listOf("com.whatsapp", "com.whatsapp.w4b"),
        mode = mode, senders = allowed,
      )
      "trigger.telegram" -> WorkflowTrigger.NotificationMatched(
        packageNames = listOf("org.telegram.messenger", "org.telegram.messenger.web"),
        mode = mode, senders = allowed,
      )
      "trigger.discord" -> WorkflowTrigger.NotificationMatched(
        packageNames = listOf("com.discord"),
        mode = mode, senders = allowed,
      )
      "trigger.email" -> {
        val providers = (root.config["providers"] as? kotlinx.serialization.json.JsonArray)
          ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
          ?: listOf("com.google.android.gm", "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail", "net.thunderbird.android")
        WorkflowTrigger.NotificationMatched(providers, mode, allowed)
      }
      "trigger.missed_call" -> {
        val onlyOff = (root.config["onlyWhenScreenOff"]
          as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: true
        WorkflowTrigger.CallMissed(mode, allowed, onlyOff)
      }
      else -> null
    }
  }

  private fun readModeAndSenders(cfg: kotlinx.serialization.json.JsonObject):
      Pair<com.google.ai.edge.gallery.customtasks.stocatstic.domain.MatchMode, List<String>> {
    val mode = runCatching {
      com.google.ai.edge.gallery.customtasks.stocatstic.domain.MatchMode.valueOf(
        (cfg["mode"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty(),
      )
    }.getOrDefault(com.google.ai.edge.gallery.customtasks.stocatstic.domain.MatchMode.ANY)
    val list = (cfg["senders"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull {
      (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty)
    }
    return mode to list
  }

  private fun kotlinx.serialization.json.JsonArray?.orEmpty() =
    this ?: kotlinx.serialization.json.JsonArray(emptyList())

  private fun schedulePeriodic(id: String, t: WorkflowTrigger.Periodic) {
    val interval = t.intervalMinutes.coerceAtLeast(15)
    val req = PeriodicWorkRequestBuilder<WorkflowWorker>(interval, TimeUnit.MINUTES)
      .setInputData(workDataOf(WorkflowWorker.KEY_WORKFLOW_ID to id))
      .setConstraints(Constraints.Builder().build())
      .build()
    WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
      workTag(id), ExistingPeriodicWorkPolicy.UPDATE, req)
  }

  private fun scheduleAlarm(id: String, t: WorkflowTrigger.Alarm) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val trigger = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, t.hour); set(Calendar.MINUTE, t.minute)
      set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
      if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
    am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger, AlarmManager.INTERVAL_DAY,
      alarmPendingIntent(id))
  }

  private fun cancelAlarm(id: String) {
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    am.cancel(alarmPendingIntent(id))
  }

  private fun alarmPendingIntent(id: String): PendingIntent {
    val i = Intent(ctx, AlarmReceiver::class.java).apply {
      action = AlarmReceiver.ACTION_FIRE
      putExtra(AlarmReceiver.EXTRA_WORKFLOW_ID, id)
    }
    return PendingIntent.getBroadcast(ctx, id.hashCode(), i,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }

  private fun workTag(id: String) = "stocatstic-$id"
}

