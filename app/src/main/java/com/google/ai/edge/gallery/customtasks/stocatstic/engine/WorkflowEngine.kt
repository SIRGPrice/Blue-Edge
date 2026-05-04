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

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.CapabilityRegistry
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.DynValue
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ExecutionContext
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.NodeResult
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Workflow
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.WorkflowEdge
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.WorkflowNode
import com.google.ai.edge.gallery.runtime.ModelLifecycleManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

private const val TAG = "StoCATsticEngine"
private const val AI_CAPABILITY_ID = "ai.llm"

/**
 * Deterministic DAG executor. Nodes run in topological order; siblings (multiple successors of the
 * same port) execute in parallel with coroutines. Branch nodes write a boolean into
 * `outputs["branch"]` that selects TRUE_BRANCH/FALSE_BRANCH successors.
 */
@Singleton
class WorkflowEngine @Inject constructor(
  @ApplicationContext private val appContext: Context,
  private val registry: CapabilityRegistry,
  private val lifecycleManager: ModelLifecycleManager,
) {
  private val _events = MutableSharedFlow<RunEvent>(extraBufferCapacity = 128)
  val events: SharedFlow<RunEvent> = _events.asSharedFlow()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /** All currently-running run jobs (keyed by runId) that contain at least one AI node. */
  private val aiJobs = ConcurrentHashMap<String, Job>()

  init {
    // Let the lifecycle manager cancel all in-flight AI runs when the user taps "Pause" in the
    // ActiveWorkflowPauseDialog shown on the chat screen.
    lifecycleManager.cancelAllRunningAi = { cancelAllAiRuns() }
  }

  /** Fire and forget. */
  fun run(workflow: Workflow) {
    scope.launch { runBlocking(workflow) }
  }

  suspend fun runBlocking(workflow: Workflow): Boolean = runFrom(workflow, startNodeId = null)

  /**
   * Executes [workflow] starting from [startNodeId] (or all roots when null). Useful for the
   * "Ejecutar" action on a single selected task — the user sees that specific task run even
   * when it is not the root of the flow.
   */
  suspend fun runFrom(workflow: Workflow, startNodeId: String?): Boolean {
    val runId = UUID.randomUUID().toString()
    val hasAi = workflow.nodes.any { it.capabilityId == AI_CAPABILITY_ID }
    _events.emit(RunEvent.Started(workflow.id, runId))
    if (hasAi) {
      lifecycleManager.acquireWorkflow()
      // Track this coroutine as an AI run so it can be cancelled from outside.
      val j = currentCoroutineContext()[Job]
      if (j != null) aiJobs[runId] = j
    }
    val variables: MutableMap<String, DynValue> = ConcurrentHashMap()
    val outputs: MutableMap<String, Map<String, DynValue>> = ConcurrentHashMap()
    val starts: List<WorkflowNode> = if (startNodeId != null) {
      workflow.nodes.filter { it.id == startNodeId }
    } else {
      workflow.roots()
    }
    val overallOk = try {
      coroutineScope {
        starts.map { s ->
          val branchId = UUID.randomUUID().toString()
          async { runNode(workflow, s, runId, branchId, variables, outputs) }
        }.awaitAll().all { it }
      }
    } catch (t: Throwable) {
      Log.e(TAG, "run failed", t); false
    } finally {
      if (hasAi) {
        aiJobs.remove(runId)
        lifecycleManager.releaseWorkflow()
      }
    }
    _events.emit(RunEvent.Finished(workflow.id, runId, overallOk))
    return overallOk
  }

  /** Cancels every in-flight workflow run that contains an AI node. */
  fun cancelAllAiRuns() {
    val snapshot = aiJobs.values.toList()
    aiJobs.clear()
    snapshot.forEach { runCatching { it.cancel() } }
  }

  private suspend fun runNode(
    workflow: Workflow,
    node: WorkflowNode,
    runId: String,
    branchId: String,
    variables: MutableMap<String, DynValue>,
    outputs: MutableMap<String, Map<String, DynValue>>,
  ): Boolean {
    val cap = registry.get(node.capabilityId)
      ?: run {
        _events.emit(RunEvent.NodeCompleted(workflow.id, runId, node.id, false,
          "Capability desconocida ${node.capabilityId}", emptyMap(), branchId))
        return false
      }
    _events.emit(RunEvent.NodeStarted(workflow.id, runId, node.id, cap.id, branchId))

    // Collect inputs from predecessor outputs (last wins for simplicity; MVP).
    val inputs: Map<String, DynValue> = buildMap {
      workflow.predecessors(node.id).forEach { edge ->
        outputs[edge.fromNode]?.get(edge.fromPort)?.let { put(edge.toPort, it) }
      }
    }
    val ctx = object : ExecutionContext {
      override val androidContext: Context = appContext
      override val inputs: Map<String, DynValue> = inputs
      override val variables: MutableMap<String, DynValue> = variables
      override fun log(message: String) { Log.d(TAG, "[${workflow.id}] $message") }
    }

    val result: NodeResult = try {
      cap.execute(ctx, node.config)
    } catch (t: Throwable) {
      NodeResult.fail(t.message ?: "excepción")
    }
    outputs[node.id] = result.outputs + ("out" to (result.outputs["out"]
      ?: (inputs["in"] ?: JsonPrimitive(""))))
    _events.emit(RunEvent.NodeCompleted(workflow.id, runId, node.id, result.success,
      result.message, result.outputs, branchId))
    if (!result.success) return false

    // Pick successor edges respecting branch decision.
    val branchChoice = (result.outputs["branch"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
    val successors = workflow.successors(node.id).filter { edge ->
      when (edge.kind) {
        WorkflowEdge.EdgeKind.TRUE_BRANCH -> branchChoice == true
        WorkflowEdge.EdgeKind.FALSE_BRANCH -> branchChoice == false
        else -> true
      }
    }
    if (successors.isEmpty()) return true
    // Branch policy for the UI bunny:
    //   • Conditional node → only the TRUE_BRANCH successor(s) get a bunny.
    //   • Non-conditional fan-out → every parallel successor gets its own bunny.
    // A fresh branchId is minted when the parent has >1 effective successor (or when
    // the single successor is a TRUE_BRANCH, so the bunny visually forks at the branch).
    // Otherwise the current branchId is reused (straight chain → same bunny).
    val forks = successors.size > 1 ||
      successors.any { it.kind == WorkflowEdge.EdgeKind.TRUE_BRANCH }
    return coroutineScope {
      successors.map { edge ->
        val next = workflow.nodes.first { it.id == edge.toNode }
        val nextBranch = if (forks) UUID.randomUUID().toString() else branchId
        async { runNode(workflow, next, runId, nextBranch, variables, outputs) }
      }.awaitAll().all { it }
    }
  }
}
