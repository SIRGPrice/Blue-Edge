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

package com.google.ai.edge.gallery.customtasks.stocatstic.ui

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.CapabilityRegistry
import com.google.ai.edge.gallery.customtasks.stocatstic.data.DecorationStore
import com.google.ai.edge.gallery.customtasks.stocatstic.data.StocatsticPreferences
import com.google.ai.edge.gallery.customtasks.stocatstic.data.StocatsticPreferencesStore
import com.google.ai.edge.gallery.customtasks.stocatstic.data.WorkflowRepository
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Workflow
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.WorkflowEdge
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.WorkflowNode
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.WorkflowTrigger
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.RunEvent
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.TriggerScheduler
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.WorkflowEngine
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene.CellRect
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene.footprintForCell
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene.footprintForNode
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene.resolveCandidateSpriteEntry
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene.resolveNodeSpriteEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val SCENE_CELL_SIZE = 48f

/**
 * ViewModel backing the single infinite StoCATstic scene. Every mutation is autosaved and
 * reschedules triggers so the user never has to press "save".
 */
@HiltViewModel
class StocatsticViewModel @Inject constructor(
  val repository: WorkflowRepository,
  val registry: CapabilityRegistry,
  private val engine: WorkflowEngine,
  private val scheduler: TriggerScheduler,
  private val decorations: DecorationStore,
  val preferencesStore: StocatsticPreferencesStore,
) : ViewModel() {

  val workflows: StateFlow<List<Workflow>> = repository.workflows
  val events: SharedFlow<RunEvent> = engine.events
  /** Cells whose random decoration has been permanently hidden by the user. */
  val deletedDecorations: StateFlow<Set<Long>> = decorations.deleted
  val preferences: StateFlow<StocatsticPreferences> = preferencesStore.prefs

  /**
   * Seeds a simple demo workflow so the user has something concrete to inspect after
   * completing the onboarding gallery. Idempotent: if ANY workflow already exists in the
   * repository, does nothing. Produces a three-node chain: notification → wait → TTS.
   */
  fun seedDemoWorkflowIfEmpty() {
    if (repository.workflows.value.isNotEmpty()) return
    val wf = repository.save(
      Workflow(name = "Flujo de ejemplo", originX = 0f, originY = 0f)
    )
    // Cell = 48f; chain the three nodes horizontally on the same row.
    val notifyId = addNodeAfter(wf.id, "notify.push", x = 0f, y = 0f, afterNodeId = null)
    val delayId = notifyId?.let {
      addNodeAfter(wf.id, "control.delay", x = 48f * 2, y = 0f, afterNodeId = it)
    }
    delayId?.let {
      addNodeAfter(wf.id, "device.tts", x = 48f * 4, y = 0f, afterNodeId = it)
    }
    // Override default TTS text / notify body so the demo speaks instead of staying silent.
    val latest = repository.get(wf.id) ?: return
    latest.nodes.forEach { n ->
      when (n.capabilityId) {
        "notify.push" -> updateNodeConfig(wf.id, n.id, JsonObject(mapOf(
          "title" to JsonPrimitive("¡Hola!"),
          "body"  to JsonPrimitive("Este es tu primer flujo"),
          "alarm" to JsonPrimitive(false),
        )))
        "control.delay" -> updateNodeConfig(wf.id, n.id, JsonObject(mapOf(
          "ms" to JsonPrimitive(1000L),
        )))
        "device.tts" -> updateNodeConfig(wf.id, n.id, JsonObject(mapOf(
          "text" to JsonPrimitive("Bienvenido a StoCATstic"),
        )))
      }
    }
  }

  /** Delete the random decoration at (cellX, cellY). Persisted immediately. */
  fun deleteDecoration(cellX: Int, cellY: Int) = decorations.delete(cellX, cellY)

  private val _editing = MutableStateFlow<Workflow?>(null)
  val editing: StateFlow<Workflow?> = _editing.asStateFlow()

  private val _paletteOpen = MutableStateFlow(false)
  val paletteOpen: StateFlow<Boolean> = _paletteOpen.asStateFlow()

  fun openPalette() { _paletteOpen.value = true }
  fun closePalette() { _paletteOpen.value = false }

  fun openWorkflow(id: String) { _editing.value = repository.get(id) }
  fun closeEditor() { _editing.value = null }

  fun newWorkflowAt(origin: Offset): Workflow {
    val wf = Workflow(name = "Flujo", originX = origin.x, originY = origin.y)
    val saved = repository.save(wf)
    _editing.value = saved
    scheduler.schedule(saved)
    return saved
  }

  fun moveWorkflow(id: String, dx: Float, dy: Float) = mutate(id) {
    it.copy(originX = it.originX + dx, originY = it.originY + dy)
  }

  fun setEnabled(id: String, enabled: Boolean) = mutate(id) { it.copy(enabled = enabled) }
  fun rename(id: String, name: String) = mutate(id) { it.copy(name = name) }
  fun setTriggers(id: String, triggers: List<WorkflowTrigger>) = mutate(id) {
    it.copy(triggers = triggers)
  }

  fun addNodeAt(flowId: String, capabilityId: String, x: Float, y: Float): String? {
    val wf = repository.get(flowId) ?: return null
    val prefs = preferencesStore.prefs.value
    val sprite = resolveCandidateSpriteEntry(isRoot = wf.nodes.isEmpty(), capabilityId = capabilityId, prefs = prefs)
    val preferredCellX = kotlin.math.floor((wf.originX + x + SCENE_CELL_SIZE / 2f) / SCENE_CELL_SIZE).toInt()
    val preferredCellY = kotlin.math.floor((wf.originY + y + SCENE_CELL_SIZE / 2f) / SCENE_CELL_SIZE).toInt()
    val (cellX, cellY) = findAvailableCell(
      preferredCellX = preferredCellX,
      preferredCellY = preferredCellY,
      sprite = sprite,
      flowId = flowId,
      excludeNodeId = null,
      allFlows = workflows.value,
      prefs = prefs,
      cellSize = SCENE_CELL_SIZE,
    )
    var newId: String? = null
    mutate(flowId) { wf ->
      val node = WorkflowNode(
        capabilityId = capabilityId,
        x = cellX * SCENE_CELL_SIZE - wf.originX,
        y = cellY * SCENE_CELL_SIZE - wf.originY,
        config = defaultConfig(capabilityId),
      )
      newId = node.id
      wf.copy(nodes = wf.nodes + node)
    }
    return newId
  }

  /**
   * Adds a task to [flowId] and automatically connects it (SEQ edge) to the previous task in
   * the flow — defined as either [afterNodeId] when provided, or else the last node in
   * insertion order. The caller sees nodes as a chain without having to draw connections.
   */
  fun addNodeAfter(
    flowId: String,
    capabilityId: String,
    x: Float,
    y: Float,
    afterNodeId: String? = null,
  ): String? {
    val wf = repository.get(flowId) ?: return null
    val prefs = preferencesStore.prefs.value
    val sprite = resolveCandidateSpriteEntry(isRoot = wf.nodes.isEmpty(), capabilityId = capabilityId, prefs = prefs)
    val preferredCellX = kotlin.math.floor((wf.originX + x + SCENE_CELL_SIZE / 2f) / SCENE_CELL_SIZE).toInt()
    val preferredCellY = kotlin.math.floor((wf.originY + y + SCENE_CELL_SIZE / 2f) / SCENE_CELL_SIZE).toInt()
    val (cellX, cellY) = findAvailableCell(
      preferredCellX = preferredCellX,
      preferredCellY = preferredCellY,
      sprite = sprite,
      flowId = flowId,
      excludeNodeId = null,
      allFlows = workflows.value,
      prefs = prefs,
      cellSize = SCENE_CELL_SIZE,
    )
    var newId: String? = null
    mutate(flowId) { wf ->
      val node = WorkflowNode(
        capabilityId = capabilityId,
        x = cellX * SCENE_CELL_SIZE - wf.originX,
        y = cellY * SCENE_CELL_SIZE - wf.originY,
        config = defaultConfig(capabilityId),
      )
      newId = node.id
      val prev = afterNodeId ?: wf.nodes.lastOrNull()?.id
      val edges = if (prev != null && prev != node.id) {
        wf.edges + WorkflowEdge(fromNode = prev, toNode = node.id)
      } else wf.edges
      wf.copy(nodes = wf.nodes + node, edges = edges)
    }
    return newId
  }

  fun moveNode(flowId: String, nodeId: String, dx: Float, dy: Float) = mutate(flowId) { wf ->
    wf.copy(nodes = wf.nodes.map {
      if (it.id == nodeId) it.copy(x = it.x + dx, y = it.y + dy) else it
    })
  }

  /**
   * Moves [nodeId] so that its top-left world position falls exactly on grid cell
   * ([targetCellX], [targetCellY]) — where a cell is [cellSize] world units wide. The move is
   * rejected (the workflow is left untouched) if the destination cell is already occupied by a
   * different node of any flow in [allFlows]; callers are expected to snap the ghost back.
   *
   * Edge endpoints are stored by node id, so the workflow-path rasterizer automatically
   * reconnects the trail from predecessors → node → successors on the next frame.
   *
   * @return `true` when the node was moved, `false` when the drop was refused.
   */
  fun moveNodeToCell(
    flowId: String,
    nodeId: String,
    targetCellX: Int,
    targetCellY: Int,
    cellSize: Float,
    allFlows: List<Workflow> = workflows.value,
  ): Boolean {
    val wf = repository.get(flowId) ?: return false
    val node = wf.nodes.firstOrNull { it.id == nodeId } ?: return false
    val prefs = preferencesStore.prefs.value
    val sprite = resolveNodeSpriteEntry(wf, node, prefs)
    val candidate = footprintForCell(targetCellX, targetCellY, sprite)
    if (!isFootprintClear(
        candidate = candidate,
        flowId = flowId,
        excludeNodeId = nodeId,
        allFlows = allFlows,
        prefs = prefs,
        cellSize = cellSize,
      )) return false
    mutate(flowId) { wf ->
      val newX = targetCellX * cellSize - wf.originX
      val newY = targetCellY * cellSize - wf.originY
      wf.copy(nodes = wf.nodes.map {
        if (it.id == nodeId) it.copy(x = newX, y = newY) else it
      })
    }
    return true
  }

  fun updateNodeConfig(flowId: String, nodeId: String, config: JsonObject) = mutate(flowId) { wf ->
    wf.copy(nodes = wf.nodes.map { if (it.id == nodeId) it.copy(config = config) else it })
  }

  fun deleteNode(flowId: String, nodeId: String) = mutate(flowId) { wf ->
    wf.copy(
      nodes = wf.nodes.filter { it.id != nodeId },
      edges = wf.edges.filter { it.fromNode != nodeId && it.toNode != nodeId },
    )
  }

  fun connect(
    flowId: String, from: String, to: String,
    kind: WorkflowEdge.EdgeKind = WorkflowEdge.EdgeKind.SEQ,
  ) = mutate(flowId) { wf ->
    // Subflows are strictly independent: a node can only have ONE predecessor, so two
    // subflows can never merge. If the target already has an incoming edge, reject.
    val hasPred = wf.edges.any { it.toNode == to }
    if (from == to || hasPred || wf.edges.any { it.fromNode == from && it.toNode == to }) wf
    else wf.copy(edges = wf.edges + WorkflowEdge(fromNode = from, toNode = to, kind = kind))
  }

  fun disconnect(flowId: String, edgeId: String) = mutate(flowId) { wf ->
    wf.copy(edges = wf.edges.filter { it.id != edgeId })
  }

  fun delete(id: String) {
    repository.get(id)?.let { scheduler.cancel(it) }
    repository.delete(id)
    if (_editing.value?.id == id) _editing.value = null
  }

  fun runNow(id: String) {
    val wf = repository.get(id) ?: return
    viewModelScope.launch { engine.runBlocking(wf) }
  }

  /** Runs [flowId] starting from the single selected [nodeId] (and then its successors). */
  fun runNode(flowId: String, nodeId: String) {
    val wf = repository.get(flowId) ?: return
    viewModelScope.launch { engine.runFrom(wf, nodeId) }
  }

  private inline fun mutate(id: String, block: (Workflow) -> Workflow) {
    val cur = repository.get(id) ?: return
    val updated = repository.save(block(cur))
    if (_editing.value?.id == id) _editing.value = updated
    scheduler.schedule(updated)
  }

  private fun defaultConfig(capabilityId: String): JsonObject {
    val cap = registry.get(capabilityId) ?: return JsonObject(emptyMap())
    return JsonObject(cap.params.mapNotNull { p -> p.default?.let { p.key to it } }.toMap())
  }

  private fun findAvailableCell(
    preferredCellX: Int,
    preferredCellY: Int,
    sprite: com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.TaskSpriteRegistry.Entry,
    flowId: String,
    excludeNodeId: String?,
    allFlows: List<Workflow>,
    prefs: StocatsticPreferences,
    cellSize: Float,
  ): Pair<Int, Int> {
    for (dx in 0..512) {
      val candidate = footprintForCell(preferredCellX + dx, preferredCellY, sprite)
      if (isFootprintClear(candidate, flowId, excludeNodeId, allFlows, prefs, cellSize)) {
        return candidate.left to candidate.top
      }
    }
    return preferredCellX to preferredCellY
  }

  private fun isFootprintClear(
    candidate: CellRect,
    flowId: String,
    excludeNodeId: String?,
    allFlows: List<Workflow>,
    prefs: StocatsticPreferences,
    cellSize: Float,
  ): Boolean = allFlows.none { wf ->
    wf.nodes.any { node ->
      if (wf.id == flowId && node.id == excludeNodeId) return@any false
      val occupied = footprintForNode(wf, node, prefs, cellSize)
      candidate.isTooCloseTo(occupied)
    }
  }
}

