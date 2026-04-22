/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers / unregisters triggers in the Android platform. Keeps runtime side-effects outside the
 * engine so the engine stays pure and testable.
 */
@Singleton
class TriggerScheduler @Inject constructor(
  @ApplicationContext private val ctx: Context,
  private val repo: WorkflowRepository,
) {
  fun syncAll() {
    repo.workflows.value.forEach { wf ->
      if (wf.enabled) schedule(wf) else cancel(wf)
    }
  }

  fun schedule(workflow: Workflow) {
    cancel(workflow)
    if (!workflow.enabled) return
    workflow.triggers.forEach { t ->
      when (t) {
        is WorkflowTrigger.Periodic -> schedulePeriodic(workflow.id, t)
        is WorkflowTrigger.Alarm -> scheduleAlarm(workflow.id, t)
        is WorkflowTrigger.BootCompleted, is WorkflowTrigger.Manual -> Unit
      }
    }
  }

  fun cancel(workflow: Workflow) {
    WorkManager.getInstance(ctx).cancelUniqueWork(workTag(workflow.id))
    cancelAlarm(workflow.id)
  }

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

