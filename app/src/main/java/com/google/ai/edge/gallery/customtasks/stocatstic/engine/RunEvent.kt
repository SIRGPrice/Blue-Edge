/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.engine

import com.google.ai.edge.gallery.customtasks.stocatstic.domain.DynValue

/** Events emitted by the engine for UI consumption (cat animation, timeline, alarms). */
sealed class RunEvent {
  abstract val workflowId: String
  abstract val runId: String
  abstract val ts: Long

  data class Started(override val workflowId: String, override val runId: String,
    override val ts: Long = System.currentTimeMillis()) : RunEvent()

  data class NodeStarted(override val workflowId: String, override val runId: String,
    val nodeId: String, val capabilityId: String,
    /** Identifies the subflow branch executing this node. Parallel fan-outs and
     *  TRUE_BRANCH conditional fan-outs mint a fresh id per successor so the UI can
     *  render one independent bunny per concurrent subflow. */
    val branchId: String,
    override val ts: Long = System.currentTimeMillis()) : RunEvent()

  data class NodeCompleted(override val workflowId: String, override val runId: String,
    val nodeId: String, val success: Boolean, val message: String,
    val outputs: Map<String, DynValue>,
    val branchId: String,
    override val ts: Long = System.currentTimeMillis()) : RunEvent()

  data class Finished(override val workflowId: String, override val runId: String,
    val success: Boolean, override val ts: Long = System.currentTimeMillis()) : RunEvent()
}

