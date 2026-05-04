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

package com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ValueKind
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Workflow
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.WorkflowNode
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.RunEvent
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.StocatsticViewModel
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.BunnySheets
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.CharacterCatalog
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.PathCatalog
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.SpriteAssets
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.SpritePaths
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.SpriteSheet
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.drawSpriteFrame
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.AssetType
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.gallery.AssetGallerySheet
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.theme.PixelPalette
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** ONE CELL = ONE TILE = the minimum interaction unit. */
private const val CELL = 48f
private const val NODE_SIZE = 48f       // task sprite side in world units; matches CELL so tasks snap to grid.
private const val CAT_SIZE = 72f
/** Zoom-out is clamped so no less than this many cells fit along the LONGEST screen side. */
private const val MAX_CELLS_LONG_SIDE = 30f
/** Initial fit guarantees at least this many cells along the SHORT side (so we always see
 *  a proper 30×N grid at startup regardless of aspect ratio). */
private const val MIN_CELLS_SHORT_SIDE = 20f
private const val MAX_ZOOM = 3f
/** Distance threshold (world units) used to decide whether the cat was "on" the moved task. */
private const val CAT_FOLLOW_THRESHOLD = CELL * 2f

private val TopBarBackTint = Color(0xFFF6D368)
private val TopBarGalleryTint = Color(0xFF6EE7FF)
private val TopBarHistoryTint = Color(0xFFFF6FB1)

/**
 * Capability ids that need a permission-flow prompt even if their [Capability.requiredPermissions]
 * is empty or already covered by the manifest — typically special-access toggles that can't be
 * granted via `RequestMultiplePermissions` (Notification Listener, Accessibility, ...).
 */
private val PERMISSION_BOUND_CAP_IDS = setOf(
  "trigger.sms", "trigger.whatsapp", "trigger.telegram", "trigger.discord",
  "trigger.email", "trigger.missed_call",
  "action.reply.sms", "action.reply.whatsapp", "action.reply.telegram",
  "action.reply.discord", "action.reply.email",
)

/** Which action the palette should perform when a capability is picked. */
private sealed class PaletteTarget {
  data class NewFlow(val worldSpawn: Offset) : PaletteTarget()
  data class AppendAfter(val flowId: String, val afterNodeId: String) : PaletteTarget()
}

/**
 * Single, infinite pixel scene that IS the StoCATstic apartado.
 *
 *   • Terrain is one continuous grass layer with scattered flower sprites.
 *   • Flows between milestones are rasterized as dirt-path sprites — never as vector strokes.
 *   • Each task is a fixed prop sprite (TaskSpriteRegistry maps capabilityId → tile region).
 *   • Menus are contextual: they only appear after tapping a grid cell. Everything done via
 *     those menus (add task, delete task, delete flow, edit, run) is scoped to that cell.
 *   • Long-pressing a task picks it up; dragging moves it and the paths reconnect live.
 */
@Composable
fun InfiniteSceneScreen(
  vm: StocatsticViewModel,
  externalGalleryRequestCount: Int = 0,
  onNavigateUp: () -> Unit = {},
) {
  val ctx = LocalContext.current
  val flows by vm.workflows.collectAsState()
  val prefs by vm.preferences.collectAsState()
  /** Gallery sheet visibility. On very first run it opens automatically (see LaunchedEffect). */
  var galleryOpen by remember { mutableStateOf(false) }
  var historyOpen by remember { mutableStateOf(false) }
  /** Capability awaiting permission grant before being added. Pair of (cap, doAdd-lambda). */
  var pendingAddCapability by remember {
    mutableStateOf<Pair<com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability, () -> Unit>?>(null)
  }
  var onboardingMode by remember { mutableStateOf(false) }
  // Gallery search persistence: the last scoped-search target survives sheet dismissals so
  // reopening the gallery lands on the same task / asset until another search overrides it.
  // Picking a task sets the first field, picking an asset sets the second — they are
  // independent so you can combine both (task first, then asset: both positions restored).
  var pinnedGalleryCapId by rememberSaveable { mutableStateOf<String?>(null) }
  var pinnedGalleryAssetId by rememberSaveable { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) {
    if (!prefs.firstRunCompleted) {
      onboardingMode = true
      galleryOpen = true
    }
  }
  var handledExternalGalleryRequest by rememberSaveable { mutableStateOf(0) }
  LaunchedEffect(externalGalleryRequestCount) {
    if (externalGalleryRequestCount > handledExternalGalleryRequest) {
      handledExternalGalleryRequest = externalGalleryRequestCount
      onboardingMode = false
      galleryOpen = true
    }
  }

  // ---- Camera ---------------------------------------------------------------------------------
  var scale by rememberSaveable { mutableStateOf(1f) }
  var camX by rememberSaveable { mutableStateOf(0f) }
  var camY by rememberSaveable { mutableStateOf(0f) }
  /** False until the viewport is first measured and the camera is fit to show 50 cells. */
  var hasInitialFit by rememberSaveable { mutableStateOf(false) }

  // ---- Selection ------------------------------------------------------------------------------
  var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
  var selectedFlowId by remember { mutableStateOf<String?>(null) }
  var selectedNodeId by remember { mutableStateOf<String?>(null) }
  var showInspector by remember { mutableStateOf(false) }
  var paletteTarget by remember { mutableStateOf<PaletteTarget?>(null) }
  var draggingNode by remember { mutableStateOf<Pair<String, String>?>(null) }
  /** World-space offset added to the dragged node for live preview (ghost). Not committed. */
  var dragGhostDelta by remember { mutableStateOf(Offset.Zero) }
  /** Current viewport in screen px — needed to derive the 50-cell zoom clamp and initial fit. */
  var viewportSize by remember { mutableStateOf(IntSize.Zero) }
  val minScale = remember(viewportSize) {
    val longSidePx = maxOf(viewportSize.width, viewportSize.height).toFloat()
    val shortSidePx = minOf(viewportSize.width, viewportSize.height).toFloat()
    if (longSidePx <= 0f) 0.3f
    else minOf(
      longSidePx / (MAX_CELLS_LONG_SIDE * CELL),
      shortSidePx / (MIN_CELLS_SHORT_SIDE * CELL),
    )
  }

  // First time the canvas is measured, start fully zoomed-out (≥30 cells long side,
  // ≥20 cells short side → always a populated 30×N grid at startup) and anchor the
  // camera at the world origin (0,0 world = top-left of the viewport). rememberSaveable
  // persists this across rotations.
  LaunchedEffect(viewportSize) {
    if (!hasInitialFit && viewportSize.width > 0 && viewportSize.height > 0) {
      scale = minScale
      camX = 0f
      camY = 0f
      hasInitialFit = true
    }
  }

  // ---- Active nodes per flow (for cat targets and node highlights) ---------------------------
  val activeByFlow = remember { mutableStateMapOf<String, String?>() }
  /**
   * One [CatActor] per currently-live subflow, keyed by `"$workflowId|$branchId"`. A fresh
   * branch id is minted by [WorkflowEngine] whenever execution forks (parallel fan-out, or
   * the TRUE_BRANCH of a conditional). Each entry is independent: when its subflow finishes
   * the cat stays on the last task until the next `Started` event for the same workflow
   * clears every cat of that workflow and starts fresh.
   *
   * Idle cats (shown at the root of every flow when nothing is running) are kept in
   * [idleCats], a separate map so their position persists across recompositions: when the
   * user drags the root task to a new cell the bunny must RUN to the new location instead
   * of teleporting there, which would be the behaviour of a frame-rebuilt CatActor.
   */

  val branchCats = remember { mutableStateMapOf<String, CatActor>() }
  /**
   * Persistent "waiting" cat per workflow, keyed by flow id. Created on first sight of the
   * flow and reused across recompositions so that moving the root task animates the cat
   * (walking along a Manhattan path to the new cell) rather than snapping it instantly.
   * Entries are pruned when their flow is deleted.
   */
  val idleCats = remember { mutableStateMapOf<String, CatActor>() }
  /**
   * Tracks the LAST node each branch-cat has visited. Keyed exactly like [branchCats]. We
   * use this to force the bunny to walk through every intermediate node's cells when it
   * falls behind — e.g. when subsequent tasks fire while it was still animating the
   * previous segment — so the user always sees it traverse the entire chain.
   */
  val catCurrentNode = remember { mutableStateMapOf<String, String>() }
  val deletedDecorations by vm.deletedDecorations.collectAsState()
  /** Decoration the user long-pressed; the small delete menu targets this cell. */
  var selectedDecoration by remember { mutableStateOf<Pair<Int, Int>?>(null) }

  fun branchKey(wfId: String, branchId: String) = "$wfId|$branchId"
  fun removeCatsOfFlow(wfId: String) {
    val prefix = "$wfId|"
    branchCats.keys.filter { it.startsWith(prefix) }.forEach {
      branchCats.remove(it); catCurrentNode.remove(it)
    }
  }

  // ---- Sprite sheets --------------------------------------------------------------------------
  // The Little Dreamyland autotile is the outdoor grass tileset. We use a fixed pure-grass
  // cell from it ([GrassAutotile.GRASS_BASE]) tiled flat across the world, AND re-use the same
  // sheet for dirt-path cells (edges) via [GrassAutotile.DIRT_VARIANTS].
  val floorSheet = remember { SpriteAssets.load(ctx, SpritePaths.TILE_GRASS_AUTOTILE) }
    ?.let { SpriteSheet(it, cols = it.width / 16, rows = it.height / 16) }
  val dirtSheet = floorSheet
  // Character sheets are keyed on the user-selected character id; swapping in the gallery
  // remounts these `remember` blocks so the on-screen runner changes immediately.
  val charEntry = remember(prefs.characterId) { CharacterCatalog.byId(prefs.characterId) }
  val bunnyIdle   = remember(charEntry.id) { SpriteAssets.load(ctx, charEntry.idle) }
    ?.let { SpriteSheet(it, cols = charEntry.idleCols, rows = BunnySheets.ROWS) }
  val bunnyRun    = remember(charEntry.id) { SpriteAssets.load(ctx, charEntry.run) }
    ?.let { SpriteSheet(it, cols = charEntry.runCols, rows = BunnySheets.ROWS) }
  val bunnyHoe    = remember(charEntry.id) { SpriteAssets.load(ctx, charEntry.hoe) }
    ?.let { SpriteSheet(it, cols = charEntry.hoeCols, rows = BunnySheets.ROWS) }
  val bunnyWater  = remember(charEntry.id) { SpriteAssets.load(ctx, charEntry.water) }
    ?.let { SpriteSheet(it, cols = charEntry.waterCols, rows = BunnySheets.ROWS) }
  val bunnySword  = remember(charEntry.id) { SpriteAssets.load(ctx, charEntry.sword) }
    ?.let { SpriteSheet(it, cols = charEntry.swordCols, rows = BunnySheets.ROWS) }
  val bunnyScythe = remember(charEntry.id) { SpriteAssets.load(ctx, charEntry.scythe) }
    ?.let { SpriteSheet(it, cols = charEntry.scytheCols, rows = BunnySheets.ROWS) }

  /** On-demand cache of tileset sheets indexed by asset path (for per-capability task props). */
  val taskSheetCache = remember { HashMap<String, SpriteSheet?>() }
  fun taskSheetFor(path: String): SpriteSheet? {
    taskSheetCache[path]?.let { return it }
    if (taskSheetCache.containsKey(path)) return null  // previously failed.
    val bmp = SpriteAssets.load(ctx, path)
    val sh = bmp?.let { SpriteSheet(it, cols = it.width / 16, rows = it.height / 16) }
    taskSheetCache[path] = sh
    return sh
  }

  // ---- Animation clock ------------------------------------------------------------------------
  var frameTime by remember { mutableStateOf(0L) }
  LaunchedEffect(Unit) {
    var last = System.nanoTime()
    while (true) {
      val now = System.nanoTime()
      val dt = (now - last) / 1e9f
      last = now
      branchCats.values.forEach { it.tick(dt) }
      idleCats.values.forEach { it.tick(dt) }
      frameTime = now
      delay(16L)
    }
  }

  // ---- Engine event pump ----------------------------------------------------------------------
  LaunchedEffect(Unit) {
    vm.events.collect { ev ->
      val wfId = ev.workflowId
      val wf = vm.repository.get(wfId) ?: return@collect
      when (ev) {
        is RunEvent.Started -> {
          // A new run for this workflow starts: discard every lingering subflow cat from
          // the previous execution (they were kept on their last node until now) and let
          // `NodeStarted` spawn fresh per-branch cats.
          removeCatsOfFlow(wfId)
          activeByFlow[wfId] = null
        }
        is RunEvent.NodeStarted -> {
          val node = wf.nodes.firstOrNull { it.id == ev.nodeId } ?: return@collect
          val key = branchKey(wfId, ev.branchId)
          fun rectOfId(id: String): CellRect? {
            val nd = wf.nodes.firstOrNull { it.id == id } ?: return null
            return footprintForNode(wf, nd, prefs, CELL)
          }
          val destRect = rectOfId(ev.nodeId) ?: return@collect
          val (destEntryX, destEntryY) = entryCellOf(destRect)
          val destCenter = centerBelow(destRect, CELL)

          val cat = branchCats[key] ?: run {
            // First task of this branch: spawn the bunny right next to that task so it
            // visually "appears beside" the first NodeStarted event.
            val fresh = CatActor(destCenter).also { it.jumpTo(destCenter) }
            branchCats[key] = fresh
            fresh
          }
          // Reconstruct the list of intermediate nodes the bunny must walk THROUGH before
          // reaching the new target. Since subflows enforce a single predecessor per node
          // (see StocatsticViewModel.connect), walking backwards from the target until we
          // find the cat's current node yields a unique chain A → … → B. If the current
          // node isn't an ancestor (e.g. cross-branch jump), we fall back to a direct
          // Manhattan walk from the cat's current cell.
          val fromId = catCurrentNode[key]
          val chainIds: List<String> = if (fromId == null || fromId == ev.nodeId) {
            listOf(ev.nodeId)
          } else {
            val chain = ArrayDeque<String>()
            var cur: String? = ev.nodeId
            while (cur != null && cur != fromId) {
              chain.addFirst(cur)
              cur = wf.predecessors(cur).firstOrNull()?.fromNode
            }
            if (cur == fromId) chain.addFirst(fromId)
            chain.toList()
          }

          // Build the waypoint list: every edge goes exit(from) → entry(to), and every
          // intermediate node is traversed along its bottom row from entry to exit so the
          // bunny never cuts corners across a multi-cell task.
          val waypoints = ArrayList<Offset>()
          fun cellCenter(cx: Int, cy: Int) =
            Offset(cx * CELL + CELL / 2f, cy * CELL + CELL / 2f)
          if (chainIds.size >= 2) {
            for (i in 0 until chainIds.size - 1) {
              val fromRect = rectOfId(chainIds[i]) ?: continue
              val toRect = rectOfId(chainIds[i + 1]) ?: continue
              val (ax, ay) = exitCellOf(fromRect)
              val (bx, by) = entryCellOf(toRect)
              val segCells = manhattanPathCells(ax, ay, bx, by)
              segCells.forEachIndexed { idx, (cx, cy) ->
                // Drop the first cell of every segment except the very first so we don't
                // insert a duplicate waypoint at each corner.
                if (i > 0 && idx == 0) return@forEachIndexed
                waypoints += cellCenter(cx, cy)
              }
              // Multi-cell intermediate node: walk along its bottom row to its exit cell
              // before starting the next segment.
              if (i < chainIds.size - 2) {
                val (nx, ny) = exitCellOf(toRect)
                if (nx != bx || ny != by) {
                  val stepX = if (nx > bx) 1 else -1
                  var cx = bx + stepX
                  while ((stepX > 0 && cx <= nx) || (stepX < 0 && cx >= nx)) {
                    waypoints += cellCenter(cx, ny)
                    cx += stepX
                  }
                }
              }
            }
            // Replace the last inserted waypoint with the visual centre below the target.
            if (waypoints.isNotEmpty()) waypoints[waypoints.lastIndex] = destCenter
            else waypoints += destCenter
          } else {
            // Single-node chain: walk from wherever the cat is, through the target's entry
            // cell, to the visual centre below it.
            val curCx = kotlin.math.floor(cat.position.x / CELL).toInt()
            val curCy = kotlin.math.floor(cat.position.y / CELL).toInt()
            val cells = manhattanPathCells(curCx, curCy, destEntryX, destEntryY)
            cells.forEachIndexed { idx, (cx, cy) ->
              waypoints += if (idx == cells.lastIndex) destCenter else cellCenter(cx, cy)
            }
          }
          cat.walkPath(
            points = waypoints,
            workingCapabilityId = if (vm.registry.get(ev.capabilityId)?.instantaneous == true) null
              else ev.capabilityId,
            // When the task is instantaneous we pass a null assetType so the character simply
            // walks THROUGH the cell to the next waypoint without playing a work animation.
            workAssetType = if (vm.registry.get(ev.capabilityId)?.instantaneous == true) null
              else resolveWorkAssetTypeForNode(node, prefs),
          )
          catCurrentNode[key] = ev.nodeId
          activeByFlow[wfId] = ev.nodeId
          val cap = vm.registry.get(ev.capabilityId)
          android.widget.Toast.makeText(
            ctx, "Ejecutando: ${cap?.label ?: ev.capabilityId}",
            android.widget.Toast.LENGTH_SHORT,
          ).show()
        }
        is RunEvent.NodeCompleted -> {
          branchCats[branchKey(wfId, ev.branchId)]?.finishWork()
          if (activeByFlow[wfId] == ev.nodeId) activeByFlow[wfId] = null
          if (!ev.success) {
            android.widget.Toast.makeText(
              ctx, "Error: ${ev.message.ifBlank { "fallo al ejecutar" }}",
              android.widget.Toast.LENGTH_LONG,
            ).show()
          }
        }
        is RunEvent.Finished -> activeByFlow[wfId] = null
      }
    }
  }

  // ---- Idle-cat syncing -----------------------------------------------------------------------
  // The "waiting" bunny at every flow's root must never teleport when the root task is moved,
  // added, or when the user drags the whole flow: it always RUNS to the new spot along a
  // Manhattan path. Spawning is the only moment we allow a jumpTo (there is no prior position
  // to walk from). Flows removed from the workspace drop their idle cat so the map can't leak.
  LaunchedEffect(flows, prefs) {
    val liveIds = HashSet<String>(flows.size)
    flows.forEach { wf ->
      liveIds += wf.id
      val root = wf.roots().firstOrNull() ?: return@forEach
      val rootRect = footprintForNode(wf, root, prefs, CELL)
      val rootCenter = centerBelow(rootRect, CELL)
      val existing = idleCats[wf.id]
      if (existing == null) {
        idleCats[wf.id] = CatActor(rootCenter).also { it.jumpTo(rootCenter) }
      } else if (existing.target != rootCenter) {
        val curCx = kotlin.math.floor(existing.position.x / CELL).toInt()
        val curCy = kotlin.math.floor(existing.position.y / CELL).toInt()
        // Route through the root's entry cell (left face of the bottom-left cell) so the
        // bunny always slides in from the side, even when the user drags the root task.
        val (entryX, entryY) = entryCellOf(rootRect)
        val cells = manhattanPathCells(curCx, curCy, entryX, entryY)
        val wps = cells.mapIndexed { i, (cx, cy) ->
          if (i == cells.lastIndex) rootCenter
          else Offset(cx * CELL + CELL / 2f, cy * CELL + CELL / 2f)
        }
        existing.walkPath(wps)
      }
    }
    // Prune idle cats belonging to flows that no longer exist.
    (idleCats.keys - liveIds).toList().forEach { idleCats.remove(it) }
  }

  fun worldFromScreen(p: Offset): Offset = Offset((p.x - camX) / scale, (p.y - camY) / scale)

  /** Cell (cx, cy) that [world] falls into, using CELL as the minimum interaction unit. */
  fun cellOf(world: Offset): Pair<Int, Int> =
    kotlin.math.floor(world.x / CELL).toInt() to kotlin.math.floor(world.y / CELL).toInt()

  /** Cell currently occupied by a node (its top-left is snapped to multiples of CELL). */
  fun cellOfNode(wf: Workflow, n: WorkflowNode): Pair<Int, Int> =
    footprintForNode(wf, n, prefs, CELL).let { it.left to it.top }

  /** Returns the node whose cell matches [cell], if any. Cells are the minimum selection unit. */
  fun nodeAtCell(cell: Pair<Int, Int>): Pair<Workflow, WorkflowNode>? {
    for (wf in flows.asReversed()) {
      for (n in wf.nodes.asReversed()) {
        if (footprintForNode(wf, n, prefs, CELL).contains(cell.first, cell.second)) return wf to n
      }
    }
    return null
  }

  fun selectCell(cell: Pair<Int, Int>) {
    selectedCell = cell
    val hit = nodeAtCell(cell)
    selectedFlowId = hit?.first?.id
    selectedNodeId = hit?.second?.id
  }

  fun clearSelection() {
    selectedCell = null
    selectedFlowId = null
    selectedNodeId = null
    showInspector = false
  }

  // Path rasterization and node occupancy only change when the workflows change —
  // NOT every animation frame. Memoizing them keeps per-frame cost at O(visible_cells).
  val pathCells = remember(flows, prefs) {
    rasterizeWorkflowPaths(flows = flows, cellSize = CELL) { wf, n ->
      footprintForNode(wf, n, prefs, CELL)
    }
  }
  val nodeCells = remember(flows, prefs) {
    val set = HashSet<Long>()
    flows.forEach { wf ->
      wf.nodes.forEach { n ->
        footprintForNode(wf, n, prefs, CELL).packedCells().forEach { set.add(it) }
      }
    }
    set
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(PixelPalette.nightSky)
      // Pinch + single-finger pan of the camera.
      .pointerInput(minScale) {
        detectTransformGestures { centroid, pan, zoom, _ ->
          val newScale = (scale * zoom).coerceIn(minScale, MAX_ZOOM)
          camX = centroid.x - (centroid.x - camX) * (newScale / scale)
          camY = centroid.y - (centroid.y - camY) * (newScale / scale)
          scale = newScale
          camX += pan.x; camY += pan.y
        }
      }
      // Long-press a CELL → pick up whatever node lives there → drag (with snap-to-cell ghost).
      // Long-pressing does NOT open the contextual menu (that's reserved for simple taps); any
      // menu currently open is dismissed at drag start. On release the node is moved to the
      // target cell, but only if the cell is free; otherwise the ghost is discarded (the node
      // snaps back). If the cat was standing on the dragged node, it walks to the new spot.
      .pointerInput(flows) {
        detectDragGesturesAfterLongPress(
          onDragStart = { screen ->
            val cell = cellOf(worldFromScreen(screen))
            val hit = nodeAtCell(cell)
            if (hit != null) {
              draggingNode = hit.first.id to hit.second.id
              dragGhostDelta = Offset.Zero
              // Hide any previously-opened contextual menu during the drag.
              selectedCell = null
              selectedFlowId = null
              selectedNodeId = null
              selectedDecoration = null
              showInspector = false
            } else {
              // Not on a task — check if the user long-pressed a random decoration.
              // Decorations are deterministic, so we can re-run the sampler here with the
              // exact same `forbidden` predicate used by the terrain renderer.
              val (cx, cy) = cell
              if (!deletedDecorations.contains(packCell(cx, cy))) {
                val hasDeco = DecorationSampler.sample(cx, cy) { x, y ->
                  val k = packCell(x, y)
                  nodeCells.contains(k) || pathCells.contains(k)
                } != null
                if (hasDeco) {
                  selectedDecoration = cell
                  selectedCell = null
                  selectedFlowId = null
                  selectedNodeId = null
                  showInspector = false
                }
              }
            }
          },
          onDrag = { change, drag ->
            if (draggingNode == null) return@detectDragGesturesAfterLongPress
            change.consume()
            dragGhostDelta += Offset(drag.x / scale, drag.y / scale)
            // The destination marker is drawn from draggingNode + dragGhostDelta directly; we
            // intentionally DON'T touch `selectedCell` here so the edit menu stays hidden.
          },
          onDragEnd = {
            val d = draggingNode
            if (d != null) {
              val wf = flows.firstOrNull { it.id == d.first }
              val n = wf?.nodes?.firstOrNull { it.id == d.second }
              if (wf != null && n != null) {
                val spec = resolveNodeSpriteEntry(wf, n, prefs)
                val gx = wf.originX + n.x + dragGhostDelta.x
                val gy = wf.originY + n.y + dragGhostDelta.y
                val targetCx = kotlin.math.floor((gx + CELL / 2f) / CELL).toInt()
                val targetCy = kotlin.math.floor((gy + CELL / 2f) / CELL).toInt()
                val moved = vm.moveNodeToCell(
                  flowId = wf.id, nodeId = n.id,
                  targetCellX = targetCx, targetCellY = targetCy,
                  cellSize = CELL, allFlows = flows,
                )
                if (moved) {
                  // Any branch-cat whose "current node" is the one just moved must run to
                  // the new location — including the cat currently executing it. We key the
                  // decision by `catCurrentNode` (authoritative) rather than proximity, so
                  // the bunny follows even when the user drags a distant task.
                  val prefix = "${wf.id}|"
                  val newRect = footprintForCell(targetCx, targetCy, spec)
                  val newCenter = centerBelow(newRect, CELL)
                  val (entryX, entryY) = entryCellOf(newRect)
                  branchCats.entries
                    .filter { it.key.startsWith(prefix) &&
                      catCurrentNode[it.key] == n.id }
                    .forEach { (_, cat) ->
                      val curCx = kotlin.math.floor(cat.position.x / CELL).toInt()
                      val curCy = kotlin.math.floor(cat.position.y / CELL).toInt()
                      val cells = manhattanPathCells(curCx, curCy, entryX, entryY)
                      val wps = cells.mapIndexed { i, (cx, cy) ->
                        if (i == cells.lastIndex) newCenter
                        else Offset(cx * CELL + CELL / 2f, cy * CELL + CELL / 2f)
                      }
                      cat.walkPath(wps, cat.workingCapabilityId)
                    }
                }
              }
            }
            draggingNode = null
            dragGhostDelta = Offset.Zero
          },
          onDragCancel = {
            draggingNode = null
            dragGhostDelta = Offset.Zero
          },
        )
      }
      // Tap always selects the underlying CELL (the minimum interaction unit). If that cell
      // happens to contain a node, the node is selected too. The contextual menu reads both
      // `selectedCell` and `selectedNodeId` to decide which actions to expose.
      .pointerInput(flows) {
        detectTapGestures(
          onTap = { p ->
            selectedDecoration = null
            selectCell(cellOf(worldFromScreen(p)))
          },
        )
      }
  ) {
    // ---------- WORLD CANVAS ----------
    Canvas(
      Modifier.fillMaxSize().graphicsLayer {
        translationX = camX; translationY = camY
        scaleX = scale; scaleY = scale
        transformOrigin = TransformOrigin(0f, 0f)
      }
    ) {
      // Capture viewport size so the zoom clamp (≤ 25 cells on the longest side) has real data.
      if (size.width.toInt() != viewportSize.width || size.height.toInt() != viewportSize.height) {
        viewportSize = IntSize(size.width.toInt(), size.height.toInt())
      }

      val x0 = -camX / scale
      val y0 = -camY / scale
      val x1 = x0 + size.width / scale
      val y1 = y0 + size.height / scale

      // Terrain. Solid green backdrop + dirt only on path cells + viewport-scoped
      // decorations. pathCells / nodeCells are memoised above (recomputed only when
      // `flows` changes), so this layer is effectively O(visible_cells) per frame.
      drawContinuousTerrain(
        floor = floorSheet, dirt = dirtSheet,
        decorationResolver = { path -> taskSheetFor(path) },
        pathCells = pathCells, nodeCells = nodeCells,
        deletedDecorations = deletedDecorations,
        cellSize = CELL,
        animationFrame = ((frameTime / 250_000_000L) and 0x7FFFFFFF).toInt(),
        x0 = x0, y0 = y0, x1 = x1, y1 = y1,
        fallbackColor = PixelPalette.grassGreen,
        pathAsset = PathCatalog.byId(prefs.pathAssetId).assetPath,
        pathCol = PathCatalog.byId(prefs.pathAssetId).col,
        pathRow = PathCatalog.byId(prefs.pathAssetId).row,
      )

      // Selected-cell highlight (visible even when a task sits on top, so the user sees the grid).
      selectedCell?.let { (cx, cy) ->
        val tl = Offset(cx * CELL, cy * CELL)
        drawRect(
          color = PixelPalette.moon.copy(alpha = 0.18f),
          topLeft = tl, size = Size(CELL, CELL),
        )
        drawRect(
          color = PixelPalette.moon,
          topLeft = tl, size = Size(CELL, CELL),
          style = Stroke(width = 2f),
        )
      }

      // Tasks as fixed sprites.
      flows.forEach { wf ->
        val activeNodeId = activeByFlow[wf.id]
        wf.nodes.forEach { n ->
          val cap = vm.registry.get(n.capabilityId)
          val spec = resolveNodeSpriteEntry(wf, n, prefs)
          val rect = footprintForNode(wf, n, prefs, CELL)
          val tl = Offset(rect.left * CELL, rect.top * CELL)
          val sh = taskSheetFor(spec.assetPath)
          if (sh != null) {
            drawImage(
              image = sh.image,
              srcOffset = IntOffset(spec.col * sh.frameW, spec.row * sh.frameH),
              srcSize = IntSize(spec.widthCells * sh.frameW, spec.heightCells * sh.frameH),
              dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()),
              dstSize = IntSize((rect.width * CELL).toInt(), (rect.height * CELL).toInt()),
              filterQuality = FilterQuality.None,
            )
          } else {
            // Fallback: coloured square with category tint.
            drawRoundRect(
              color = (cap?.category?.color ?: PixelPalette.softGlow).copy(alpha = 0.92f),
              topLeft = tl, size = Size(rect.width * CELL, rect.height * CELL),
              cornerRadius = CornerRadius(8f, 8f),
            )
          }
          val selected = n.id == selectedNodeId && wf.id == selectedFlowId
          val active = n.id == activeNodeId
          if (selected || active) {
            drawRoundRect(
              color = if (active) PixelPalette.moon else PixelPalette.onDark,
              topLeft = tl, size = Size(rect.width * CELL, rect.height * CELL),
              cornerRadius = CornerRadius(6f, 6f),
              style = Stroke(width = 2f),
            )
          }
        }
      }

      // Drag ghost + destination cell marker (only while long-press-dragging a task).
      draggingNode?.let { (flowId, nodeId) ->
        val wf = flows.firstOrNull { it.id == flowId }
        val n = wf?.nodes?.firstOrNull { it.id == nodeId }
        if (wf != null && n != null) {
          val spec = resolveNodeSpriteEntry(wf, n, prefs)
          val gx = wf.originX + n.x + dragGhostDelta.x
          val gy = wf.originY + n.y + dragGhostDelta.y
          val targetCx = kotlin.math.floor((gx + CELL / 2f) / CELL).toInt()
          val targetCy = kotlin.math.floor((gy + CELL / 2f) / CELL).toInt()
          val targetRect = footprintForCell(targetCx, targetCy, spec)
          // Re-check occupation excluding the node being dragged.
          val occupied = flows.any { other ->
            other.nodes.any {
              if (other.id == flowId && it.id == nodeId) return@any false
              targetRect.isTooCloseTo(footprintForNode(other, it, prefs, CELL))
            }
          }
          val markerColor =
            if (occupied) PixelPalette.failure else PixelPalette.moon
          drawRect(
            color = markerColor.copy(alpha = 0.30f),
            topLeft = Offset(targetRect.left * CELL, targetRect.top * CELL),
            size = Size(targetRect.width * CELL, targetRect.height * CELL),
          )
          drawRect(
            color = markerColor,
            topLeft = Offset(targetRect.left * CELL, targetRect.top * CELL),
            size = Size(targetRect.width * CELL, targetRect.height * CELL),
            style = Stroke(width = 2f),
          )
          // Ghost task sprite at the pointer's current (unsnapped) position, translucent.
          val sh = taskSheetFor(spec.assetPath)
          if (sh != null) {
            drawImage(
              image = sh.image,
              srcOffset = IntOffset(spec.col * sh.frameW, spec.row * sh.frameH),
              srcSize = IntSize(spec.widthCells * sh.frameW, spec.heightCells * sh.frameH),
              dstOffset = IntOffset(gx.toInt(), gy.toInt()),
              dstSize = IntSize((spec.widthCells * CELL).toInt(), (spec.heightCells * CELL).toInt()),
              filterQuality = FilterQuality.None,
              alpha = 0.6f,
            )
          }
        }
      }

      // Cats: one per live subflow (branch) if the flow is executing; otherwise a single
      // "idle" cat synthesised at the flow's ROOT task so the user always sees where a run
      // will start. Idle cats are NOT stored in `branchCats` — they're re-created every
      // frame from the current workflow geometry, which is cheap and guarantees they
      // disappear the instant the first NodeStarted event fires for the flow.
      flows.forEach { wf ->
        val prefix = "${wf.id}|"
        val liveKeys = branchCats.keys.filter { it.startsWith(prefix) }
        val catsToDraw: List<CatActor> = if (liveKeys.isNotEmpty()) {
          liveKeys.mapNotNull { branchCats[it] }
        } else {
          // Persistent idle bunny: kept in `idleCats` so moving the root task animates
          // the walk instead of teleporting. Falls back to a fresh actor on the very first
          // frame before the syncing LaunchedEffect has had a chance to populate the map.
          val idle = idleCats[wf.id] ?: run {
            val root = wf.roots().firstOrNull() ?: return@forEach
            val pos = centerBelow(footprintForNode(wf, root, prefs, CELL), CELL)
            CatActor(pos)
          }
          listOf(idle)
        }
        catsToDraw.forEach { cat ->
          val sheet = when (cat.state) {
            // Work animation is driven by the asset type of the task the cat is on:
            //   • PLANT  → watering can (continuous)
            //   • SOLID  → hoe / mining (continuous)
            //   • NORMAL → idle frames while the cat orbits the prop (see CatActor.tickWork)
            CatActor.State.WORK -> when (cat.workAssetType) {
              AssetType.PLANT  -> bunnyWater  ?: bunnyIdle
              AssetType.SOLID  -> bunnyHoe    ?: bunnyIdle
              AssetType.NORMAL -> bunnyIdle
              null -> bunnyIdle
            }
            CatActor.State.WALK -> bunnyRun ?: bunnyIdle
            CatActor.State.IDLE -> bunnyIdle
          }
          if (sheet != null) {
            val frameIdx = ((frameTime / 120_000_000L) % sheet.cols.toLong()).toInt()
            drawSpriteFrame(
              sheet = sheet,
              frame = frameIdx,
              dir = cat.dir,
              destCenter = cat.position,
              destSize = Size(CAT_SIZE, CAT_SIZE),
              flipX = false,
            )
          } else {
            drawCatFallback(cat.position)
          }
        }
      }
    }

    // ---------- SCREEN-SPACE TASK LABELS ----------
    flows.forEach { wf ->
      wf.nodes.forEach { n ->
        val cap = vm.registry.get(n.capabilityId)
        val sx = (wf.originX + n.x) * scale + camX
        val rect = footprintForNode(wf, n, prefs, CELL)
        val sy = ((rect.top + rect.height) * CELL) * scale + camY
        val labelFont = (10f * scale.coerceIn(0.8f, 1.3f)).sp
        Text(
          text = n.label ?: cap?.label ?: n.capabilityId,
          color = Color.Black, fontSize = labelFont, maxLines = 1,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.offset { IntOffset(sx.toInt() + 2, sy.toInt() + 2) },
        )
      }
    }

    // ---------- CONTEXTUAL CELL MENU (only when a cell is selected) ----------
    selectedCell?.let { cell ->
      CellActionBar(
        vm = vm,
        selectedCell = cell,
        selectedFlowId = selectedFlowId,
        selectedNodeId = selectedNodeId,
        onEdit = { showInspector = true },
        onAddTarget = { paletteTarget = it },
        onDismiss = { clearSelection() },
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(16.dp),
       )
     }

    // ---------- INSPECTOR (only when explicitly requested) ----------
    if (showInspector) {
      InspectorSheet(
        vm = vm,
        selectedFlowId = selectedFlowId,
        selectedNodeId = selectedNodeId,
        onClose = { showInspector = false },
      )
    }

    // ---------- PALETTE (only while picking a capability) ----------
    paletteTarget?.let { target ->
      PaletteSheet(
        vm = vm,
        target = target,
        onPicked = { capId ->
          val cap = vm.registry.get(capId)
          val add = addTask@{
            when (target) {
              is PaletteTarget.NewFlow -> {
                val wf = vm.newWorkflowAt(target.worldSpawn)
                vm.addNodeAfter(wf.id, capId, x = 0f, y = 0f, afterNodeId = null)
              }
              is PaletteTarget.AppendAfter -> {
                val wf = vm.repository.get(target.flowId) ?: return@addTask
                val prev = wf.nodes.firstOrNull { it.id == target.afterNodeId }
                val nx = (prev?.x ?: 0f) + CELL * 2
                val ny = prev?.y ?: 0f
                vm.addNodeAfter(target.flowId, capId, x = nx, y = ny,
                  afterNodeId = target.afterNodeId)
              }
            }
            paletteTarget = null
          }
          if (cap == null || (cap.requiredPermissions.isEmpty() && cap.id !in PERMISSION_BOUND_CAP_IDS)) {
            add()
          } else {
            pendingAddCapability = cap to add
          }
        },
        onDismiss = { paletteTarget = null },
      )
    }

    // ---------- DECORATION DELETE BAR (long-press on a random decoration) ----------
    selectedDecoration?.let { cell ->
      DecorationActionBar(
        cell = cell,
        onDelete = {
          vm.deleteDecoration(cell.first, cell.second)
          selectedDecoration = null
        },
        onDismiss = { selectedDecoration = null },
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
      )
    }


    // ---------- GALLERY SHEET (onboarding forces selection before closing) ----------
    if (galleryOpen) {
      AssetGallerySheet(
        vm = vm,
        onboarding = onboardingMode,
        initialScrollToCapId = pinnedGalleryCapId,
        initialHighlightAssetId = pinnedGalleryAssetId,
        onPinnedCapIdChanged = { pinnedGalleryCapId = it },
        onPinnedAssetIdChanged = { pinnedGalleryAssetId = it },
        onDismiss = {
          galleryOpen = false
          if (onboardingMode) {
            vm.preferencesStore.markFirstRunCompleted()
            vm.seedDemoWorkflowIfEmpty()
            onboardingMode = false
          }
        },
      )
    }

    // ---------- FLOATING MENU ICON COLUMN (top-left, over the scene) ----------
    // Replaces the removed global top app bar: back, gallery, history stacked vertically
    // inside a single rounded surface that matches the rest of the stoCATstic UI
    // (CellActionBar / ActionChip / InspectorSheet all use the same deepSky surface
    // with white iconography and 18 dp rounded corners).
    Surface(
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(start = 8.dp, top = 8.dp),
      shape = RoundedCornerShape(18.dp),
      color = PixelPalette.deepSky.copy(alpha = 0.92f),
      tonalElevation = 6.dp,
      shadowElevation = 8.dp,
    ) {
      Column(
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        IconButton(onClick = onNavigateUp) {
          Icon(
            Icons.Outlined.ArrowBack,
            contentDescription = "Volver",
            tint = PixelPalette.onDark,
          )
        }
        IconButton(
          onClick = { onboardingMode = false; galleryOpen = true },
        ) {
          Icon(
            Icons.Outlined.Collections,
            contentDescription = "Abrir galería de assets",
            tint = PixelPalette.onDark,
          )
        }
        IconButton(onClick = { historyOpen = true }) {
          Icon(
            Icons.Outlined.Notifications,
            contentDescription = "Historial de acciones",
            tint = PixelPalette.onDark,
          )
        }
      }
    }
    if (historyOpen) {
      val log = remember { com.google.ai.edge.gallery.customtasks.stocatstic.data.DangerousActionLog.shared }
      if (log != null) {
        com.google.ai.edge.gallery.customtasks.stocatstic.ui.history.DangerousActionHistorySheet(
          log = log, onDismiss = { historyOpen = false },
        )
      } else historyOpen = false
    }

    // ---------- PERMISSION PROMPT AT TASK CREATION ----------
    // Captured when the user picks a capability that requires permissions or special access;
    // consumed by the requester composable below which shows the system dialog and only then
    // runs the "add node" lambda.
    pendingAddCapability?.let { (cap, add) ->
      val requester = com.google.ai.edge.gallery.customtasks.stocatstic.ui.permissions
        .rememberCapabilityPermissionRequester(
          onDenied = { pendingAddCapability = null },
          onGranted = {
            pendingAddCapability = null
            add()
          },
        )
      LaunchedEffect(cap.id) { requester(cap) }
    }
  }
}

// -------------------------------------------------------------------------------------------
// HUD composables (only shown on demand)
// -------------------------------------------------------------------------------------------

/**
 * Contextual action bar surfaced after a simple tap on a grid cell. Presented as a compact
 * floating card with a header (cell coordinates + task name) and two action groups:
 * primary (edit / next / run) and destructive (delete task / delete flow). The action row is
 * horizontally scrollable so no chip ever clips on narrow devices.
 */
@Composable
private fun CellActionBar(
  vm: StocatsticViewModel,
  selectedCell: Pair<Int, Int>,
  selectedFlowId: String?,
  selectedNodeId: String?,
  onEdit: () -> Unit,
  onAddTarget: (PaletteTarget) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier,
) {
  val flows by vm.workflows.collectAsState()
  val wf = selectedFlowId?.let { id -> flows.firstOrNull { it.id == id } }
  val node = selectedNodeId?.let { nId -> wf?.nodes?.firstOrNull { it.id == nId } }
  val cap = node?.let { vm.registry.get(it.capabilityId) }
  val title = when {
    cap != null -> node.label ?: cap.label
    else -> "Celda vacía"
  }
  val subtitle = "(${selectedCell.first}, ${selectedCell.second})" +
    (wf?.let { " · ${it.name}" } ?: "")

  Card(
    modifier = modifier.widthIn(max = 560.dp).fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = PixelPalette.deepSky),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
      // Header -------------------------------------------------------------------------------
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (cap != null) {
          Box(
            Modifier
              .size(30.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(cap.category.color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(cap.icon, null, tint = cap.category.color, modifier = Modifier.size(18.dp))
          }
        } else {
          Box(
            Modifier
              .size(30.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(PixelPalette.softGlow),
            contentAlignment = Alignment.Center,
          ) {
            Icon(Icons.Outlined.AccountTree, null, tint = PixelPalette.onDarkMuted,
              modifier = Modifier.size(18.dp))
          }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
          Text(
            title, color = PixelPalette.onDark, maxLines = 1,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
          )
          Text(
            subtitle, color = PixelPalette.onDarkMuted, maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, null, tint = PixelPalette.onDarkMuted)
        }
      }
      HorizontalDivider(
        Modifier.padding(vertical = 6.dp),
        color = PixelPalette.softGlow.copy(alpha = 0.6f),
      )
      // Actions ------------------------------------------------------------------------------
      Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (selectedNodeId != null && selectedFlowId != null) {
          ActionChip("Editar", Icons.Outlined.Edit) { onEdit() }
          ActionChip("Siguiente", Icons.Outlined.Add) {
            onAddTarget(PaletteTarget.AppendAfter(selectedFlowId, selectedNodeId))
          }
          ActionChip("Ejecutar", Icons.Outlined.PlayArrow, backgroundColor = PixelPalette.success.copy(alpha = 0.45f)) {
            vm.runNode(selectedFlowId, selectedNodeId)
          }
          Spacer(Modifier.width(4.dp))
          // Every task exposes "Borrar tarea".
          ActionChip("Borrar tarea", Icons.Outlined.Delete, backgroundColor = PixelPalette.failure.copy(alpha = 0.5f)) {
            vm.deleteNode(selectedFlowId, selectedNodeId); onDismiss()
          }
          // Only the flow's ROOT (initial task) exposes the additional "Borrar flujo
          // completo" chip: it removes the whole workflow, its edges, AND every bunny
          // (branch + idle) associated with it in the scene.
          val isRoot = node != null && wf != null &&
            wf.predecessors(node.id).isEmpty()
          if (isRoot) {
            ActionChip("Borrar flujo", Icons.Outlined.DeleteSweep, backgroundColor = PixelPalette.failure.copy(alpha = 0.5f)) {
              vm.delete(selectedFlowId); onDismiss()
            }
          }
        } else {
          val spawn = Offset(selectedCell.first * CELL, selectedCell.second * CELL)
          ActionChip("Crear flujo aquí", Icons.Outlined.AccountTree,
            backgroundColor = PixelPalette.catBlueDeep.copy(alpha = 0.6f)) {
            onAddTarget(PaletteTarget.NewFlow(spawn))
          }
        }
      }
    }
  }
}

@Composable
private fun ActionChip(
  label: String,
  icon: ImageVector,
  backgroundColor: Color = PixelPalette.softGlow.copy(alpha = 0.55f),
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = backgroundColor,
    modifier = Modifier.clickable { onClick() },
  ) {
    Row(
      Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(6.dp))
      Text(
        label, color = Color.White, maxLines = 1,
        fontSize = 13.sp, fontWeight = FontWeight.Medium,
      )
    }
  }
}

/**
 * Inspector bottom-sheet that opens from the action bar's "Editar" button. Fully expanded to
 * give parameter editors room to breathe, with a scrollable body, sticky footer for primary
 * actions, and OutlinedTextField-style inputs that never collapse under the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectorSheet(
  vm: StocatsticViewModel,
  selectedFlowId: String?,
  selectedNodeId: String?,
  onClose: () -> Unit,
) {
  if (selectedFlowId == null || selectedNodeId == null) return
  val flows by vm.workflows.collectAsState()
  val wf = flows.firstOrNull { it.id == selectedFlowId } ?: return
  val node = wf.nodes.firstOrNull { it.id == selectedNodeId } ?: return
  val cap = vm.registry.get(node.capabilityId) ?: return
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  // Key of the SPECIAL param currently being edited through the dedicated AI sheet (null = none).
  var aiEditingKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
  ModalBottomSheet(
    onDismissRequest = onClose,
    sheetState = sheetState,
    containerColor = PixelPalette.deepSky,
    dragHandle = null,
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
      // Header card ---------------------------------------------------------------------------
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PixelPalette.softGlow),
      ) {
        Row(
          Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(cap.category.color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(cap.icon, null, tint = cap.category.color,
              modifier = Modifier.size(26.dp))
          }
          Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
              cap.label, color = PixelPalette.onDark,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold, maxLines = 2,
            )
            Text(
              "${wf.name} · ${cap.category.label}",
              color = PixelPalette.onDarkMuted,
              style = MaterialTheme.typography.labelMedium, maxLines = 1,
            )
          }
          IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, null, tint = PixelPalette.onDarkMuted)
          }
        }
      }
      if (cap.description.isNotBlank()) {
        Text(
          cap.description, color = PixelPalette.onDarkMuted,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        )
      }
      // Params (scrollable, never clipped) ----------------------------------------------------
      Text(
        "Parámetros", color = PixelPalette.onDark,
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
      )
      LazyColumn(
        Modifier
          .fillMaxWidth()
          .heightIn(min = 100.dp, max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (cap.params.isEmpty()) {
          item {
            Text(
              "Esta tarea no requiere parámetros.",
              color = PixelPalette.onDarkMuted,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        } else {
          items(cap.params) { p ->
            val cur = node.config[p.key]
            com.google.ai.edge.gallery.customtasks.stocatstic.ui.inspector.ParamField(
              spec = p,
              value = cur,
              onChange = { newVal ->
                vm.updateNodeConfig(
                  wf.id, node.id, JsonObject(node.config + (p.key to newVal)),
                )
              },
              onSpecial = { aiEditingKey = p.key },
            )
          }
        }
      }
      // AI multimodal editor -----------------------------------------------------------------
      aiEditingKey?.let { key ->
        val initial = (node.config[key] as? JsonObject) ?: JsonObject(emptyMap())
        com.google.ai.edge.gallery.customtasks.stocatstic.ui.ai.AiNodeConfigSheet(
          initialConfig = initial,
          onSave = { saved ->
            vm.updateNodeConfig(
              wf.id, node.id, JsonObject(node.config + (key to saved)),
            )
          },
          onDismiss = { aiEditingKey = null },
        )
      }
      // Sticky footer -------------------------------------------------------------------------
      HorizontalDivider(
        Modifier.padding(vertical = 14.dp),
        color = PixelPalette.softGlow,
      )
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onClose,
          modifier = Modifier.weight(1f),
        ) { Text("Cerrar") }
        Button(
          onClick = { vm.runNow(wf.id); onClose() },
          colors = ButtonDefaults.buttonColors(containerColor = PixelPalette.catBlueDeep),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Ejecutar flujo")
        }
      }
    }
  }
}

/**
 * Capability palette used when creating a new flow or appending a task. Includes a search box
 * that filters by label and description, a fully expanded bottom sheet, and touch-friendly row
 * cards that never compress their text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteSheet(
  vm: StocatsticViewModel,
  target: PaletteTarget,
  onPicked: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val title = when (target) {
    is PaletteTarget.NewFlow -> "Nuevo flujo"
    is PaletteTarget.AppendAfter -> "Añadir tarea siguiente"
  }
  val subtitle = when (target) {
    is PaletteTarget.NewFlow -> "Elige la primera tarea del flujo"
    is PaletteTarget.AppendAfter -> "Elige la tarea que se ejecutará a continuación"
  }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var query by remember { mutableStateOf("") }
  val all = remember { vm.registry.all() }
  val filtered = remember(query, all) {
    if (query.isBlank()) all
    else all.filter { c ->
      c.label.contains(query, ignoreCase = true) ||
        c.description.contains(query, ignoreCase = true) ||
        c.category.label.contains(query, ignoreCase = true)
    }
  }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PixelPalette.deepSky,
    dragHandle = null,
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
      // Header --------------------------------------------------------------------------------
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
            title, color = PixelPalette.onDark,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
          )
          Text(
            subtitle, color = PixelPalette.onDarkMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, null, tint = PixelPalette.onDarkMuted)
        }
      }
      // Search --------------------------------------------------------------------------------
      Row(
        Modifier
          .fillMaxWidth()
          .padding(top = 12.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(PixelPalette.softGlow.copy(alpha = 0.65f))
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Outlined.Search, null, tint = PixelPalette.onDarkMuted,
          modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
          if (query.isEmpty()) {
            Text(
              "Buscar tareas…",
              color = PixelPalette.onDarkMuted,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          BasicTextField(
            value = query,
            onValueChange = { query = it },
            textStyle = TextStyle(color = PixelPalette.onDark, fontSize = 15.sp),
            cursorBrush = SolidColor(PixelPalette.moon),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (query.isNotEmpty()) {
          IconButton(onClick = { query = "" }, modifier = Modifier.size(22.dp)) {
            Icon(Icons.Outlined.Close, null, tint = PixelPalette.onDarkMuted,
              modifier = Modifier.size(18.dp))
          }
        }
      }
      // Grouped list --------------------------------------------------------------------------
      val grouped = filtered.groupBy { it.category }
      LazyColumn(
        Modifier
          .fillMaxWidth()
          .padding(top = 14.dp)
          .heightIn(min = 200.dp, max = 540.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (filtered.isEmpty()) {
          item {
            Text(
              "Sin resultados para “$query”.",
              color = PixelPalette.onDarkMuted,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(vertical = 24.dp),
            )
          }
        }
        grouped.forEach { (category, caps) ->
          item(key = "hdr-${category.label}") {
            Row(
              Modifier.padding(top = 4.dp, bottom = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                Modifier
                  .size(8.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(category.color),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                category.label.uppercase(),
                color = PixelPalette.onDarkMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
              )
            }
          }
          items(caps, key = { it.id }) { cap ->
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = PixelPalette.softGlow),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onPicked(cap.id) },
            ) {
              Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cap.category.color.copy(alpha = 0.22f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(cap.icon, null, tint = cap.category.color,
                    modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                  Text(
                    cap.label, color = PixelPalette.onDark,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                  )
                  if (cap.description.isNotBlank()) {
                    Text(
                      cap.description, color = PixelPalette.onDarkMuted,
                      style = MaterialTheme.typography.bodySmall,
                      maxLines = 3,
                      modifier = Modifier.padding(top = 2.dp),
                    )
                  }
                }
                FilledTonalButton(
                  onClick = { onPicked(cap.id) },
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 6.dp,
                  ),
                ) {
                  Text("Elegir", fontSize = 13.sp)
                }
              }
            }
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------------------------------------
// Fallback drawing (used when the paid pack is not installed yet)
// -------------------------------------------------------------------------------------------

/**
 * Minimal floating card surfaced after a long-press on a random decoration cell. Exposes a
 * single destructive action ("Borrar decoración") that persistently hides the prop at that
 * cell (see [StocatsticViewModel.deleteDecoration]).
 */
@Composable
private fun DecorationActionBar(
  cell: Pair<Int, Int>,
  onDelete: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier,
) {
  Card(
    modifier = modifier.widthIn(max = 360.dp),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = PixelPalette.deepSky),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Row(
      Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(
          "Decoración", color = PixelPalette.onDark,
          style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        )
        Text(
          "(${cell.first}, ${cell.second})", color = PixelPalette.onDarkMuted,
          style = MaterialTheme.typography.labelSmall,
        )
      }
      ActionChip("Borrar", Icons.Outlined.Delete, backgroundColor = PixelPalette.failure.copy(alpha = 0.5f)) { onDelete() }
      IconButton(onClick = onDismiss) {
        Icon(Icons.Outlined.Close, null, tint = PixelPalette.onDarkMuted)
      }
    }
  }
}

private fun DrawScope.drawBackgroundFallback(x0: Float, y0: Float, x1: Float, y1: Float) {
  val step = CELL
  var y = kotlin.math.floor(y0 / step) * step
  while (y < y1) {
    var x = kotlin.math.floor(x0 / step) * step
    while (x < x1) {
      drawRect(color = PixelPalette.deepSky,
        topLeft = Offset(x, y), size = Size(step, step))
      drawRect(color = Color.Black.copy(alpha = 0.06f),
        topLeft = Offset(x, y), size = Size(step, 1.5f))
      x += step
    }
    y += step
  }
}

private fun DrawScope.drawCatFallback(center: Offset) {
  val px = 5f
  val map = listOf(
    "..bb...bb..", ".bddb.bddb.", ".bbbbbbbbb.", "bbebbbbebb.",
    "bbbbbbbbbbb", "bbbbggbbbbb", ".bbbbbbbbb.", ".bbbbbbbbb.",
    ".b.bb.bb.b.", ".b.b..bb.b.",
  )
  val w = map[0].length * px
  val ox = center.x - w / 2f
  val oy = center.y - map.size * px
  map.forEachIndexed { r, line ->
    line.forEachIndexed { c, ch ->
      val color = when (ch) {
        'b' -> PixelPalette.catBlue
        'd' -> PixelPalette.catBlueDeep
        'g' -> PixelPalette.catGold
        'e' -> Color(0xFF102040)
        else -> null
      }
      if (color != null) drawRect(
        color = color, topLeft = Offset(ox + c * px, oy + r * px),
        size = Size(px, px),
      )
    }
  }
}

