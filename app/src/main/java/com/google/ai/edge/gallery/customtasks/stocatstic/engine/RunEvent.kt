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

