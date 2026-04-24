/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** A positioned node in the graph. */
@Serializable
data class WorkflowNode(
  val id: String = UUID.randomUUID().toString(),
  /** Capability id (see CapabilityRegistry). */
  val capabilityId: String,
  val x: Float = 0f,
  val y: Float = 0f,
  /** User-provided parameter values for this node. Keyed by [ParamSpec.key]. */
  val config: JsonObject = JsonObject(emptyMap()),
  /** Optional display name overriding capability label. */
  val label: String? = null,
)

/** Directed edge between two node ports. */
@Serializable
data class WorkflowEdge(
  val id: String = UUID.randomUUID().toString(),
  val fromNode: String,
  val fromPort: String = "out",
  val toNode: String,
  val toPort: String = "in",
  val kind: EdgeKind = EdgeKind.SEQ,
) {
  @Serializable
  enum class EdgeKind {
    /** Sequential data/control edge. */
    SEQ,
    /** Fan-out branch executed in parallel with siblings. */
    PARALLEL,
    /** Conditional true branch (from Branch nodes). */
    TRUE_BRANCH,
    /** Conditional false branch. */
    FALSE_BRANCH,
  }
}

/** A trigger that kicks off the workflow. */
@Serializable
sealed class WorkflowTrigger {
  @Serializable
  data class Manual(val id: String = "manual") : WorkflowTrigger()

  /** Periodic trigger. Minimum guaranteed interval is 15 min (WorkManager). */
  @Serializable
  data class Periodic(val intervalMinutes: Long, val id: String = "periodic") : WorkflowTrigger()

  /** Alarm at a specific wall-clock time (HH:mm) on selected weekdays (0=Sun..6=Sat). */
  @Serializable
  data class Alarm(
    val hour: Int,
    val minute: Int,
    val weekdays: Set<Int> = setOf(0, 1, 2, 3, 4, 5, 6),
    val id: String = "alarm",
  ) : WorkflowTrigger()

  @Serializable
  data class BootCompleted(val id: String = "boot") : WorkflowTrigger()

  // --- Reactive triggers -------------------------------------------------------------------
  // Each reactive trigger mirrors the `mode + senders` shape exposed by its matching
  // `trigger.*` capability so the engine and scheduler can match events uniformly.

  /** Fires when an incoming SMS matches the filter. */
  @Serializable
  data class SmsReceived(
    val mode: MatchMode = MatchMode.ANY,
    val senders: List<String> = emptyList(),
    val id: String = "sms",
  ) : WorkflowTrigger()

  /**
   * Fires when a notification on [packageName] (whatsapp/telegram/discord/gmail/outlook/...)
   * matches the sender filter. Used by WhatsApp/Telegram/Discord/Email wait tasks.
   */
  @Serializable
  data class NotificationMatched(
    val packageNames: List<String>,
    val mode: MatchMode = MatchMode.ANY,
    val senders: List<String> = emptyList(),
    val id: String = "notification",
  ) : WorkflowTrigger()

  /** Fires when a missed call matches the filter (optionally only while user wasn't looking). */
  @Serializable
  data class CallMissed(
    val mode: MatchMode = MatchMode.ANY,
    val numbers: List<String> = emptyList(),
    val onlyWhenScreenOff: Boolean = true,
    val id: String = "missed_call",
  ) : WorkflowTrigger()
}

/** Top-level workflow aggregate. */
@Serializable
data class Workflow(
  val id: String = UUID.randomUUID().toString(),
  val name: String = "New Workflow",
  val enabled: Boolean = true,
  /** Workflow cluster origin in the shared infinite world. */
  val originX: Float = 0f,
  val originY: Float = 0f,
  val nodes: List<WorkflowNode> = emptyList(),
  val edges: List<WorkflowEdge> = emptyList(),
  val triggers: List<WorkflowTrigger> = listOf(WorkflowTrigger.Manual()),
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
) {
  fun successors(nodeId: String): List<WorkflowEdge> = edges.filter { it.fromNode == nodeId }
  fun predecessors(nodeId: String): List<WorkflowEdge> = edges.filter { it.toNode == nodeId }
  fun roots(): List<WorkflowNode> {
    val targets = edges.map { it.toNode }.toSet()
    return nodes.filter { it.id !in targets }
  }
  /** Absolute world position of a node inside this workflow. */
  fun worldPos(node: WorkflowNode): Pair<Float, Float> =
    (originX + node.x) to (originY + node.y)
}


