/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.scene

import androidx.compose.ui.geometry.Offset
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.AssetType

/**
 * One cat per active subflow. The cat walks through an ORDERED list of waypoints so it
 * visibly follows the rasterized dirt trail instead of teleporting in a straight line.
 *
 * Facing convention (matches Little Dreamyland row order):
 *   • 0 = front (looking at the user, i.e. moving DOWN)
 *   • 1 = left
 *   • 2 = right
 *   • 3 = back (moving UP)
 *
 * Facing rules enforced by [tick]/[finishWork]/[jumpTo]:
 *   • IDLE (no pending waypoints, no work) → always front (dir = 0).
 *   • WALK vertical segment → back when going up, front when going down.
 *     Vertical motion DOMINATES over horizontal so "any action" shows front/back whenever
 *     there is a vertical component.
 *   • WALK pure-horizontal segment → left/right.
 *   • WORK state inherits whatever dir the cat had on arrival; [AssetType.NORMAL] causes
 *     the cat to orbit around the arrival position, updating dir every tick based on the
 *     tangent vector (again with vertical dominance).
 */
class CatActor(var position: Offset = Offset.Zero) {
  enum class State { IDLE, WALK, WORK }

  var state: State = State.IDLE
  /** 0 down/front, 1 left, 2 right, 3 up/back. */
  var dir: Int = 0
  /** Capability id currently executing (used by the scene to decide overlay effects). */
  var workingCapabilityId: String? = null
  /** Behavioural tag of the task the cat is working on (drives the WORK animation choice). */
  var workAssetType: AssetType? = null

  // --- Orbit (used only for AssetType.NORMAL) ------------------------------------------------
  private var orbitCenter: Offset? = null
  private var orbitAngle: Float = 0f
  private val orbitRadius: Float = 28f   // ~0.6 cell; keeps the cat visually close to the prop.
  private val orbitAngularSpeedRadPerSec: Float = (2f * Math.PI / 2.2f).toFloat() // 1 turn / 2.2s

  private val waypoints: ArrayDeque<Offset> = ArrayDeque()

  /** Advance one simulation step with the given frame delta (seconds). */
  fun tick(dtSeconds: Float, speedWorldUnitsPerSec: Float = 240f) {
    val next = waypoints.firstOrNull()
      ?: run {
        // No pending route. Transition out of WALK into WORK or IDLE.
        if (state == State.WALK) {
          if (workingCapabilityId != null) {
            state = State.WORK
            // When entering WORK for an orbit-type asset, remember the anchor point.
            if (workAssetType == AssetType.NORMAL && orbitCenter == null) {
              orbitCenter = position
              orbitAngle = 0f
            }
          } else {
            state = State.IDLE
            dir = 0 // rule: idle always faces the user.
          }
        } else if (state == State.IDLE) {
          dir = 0 // ensure we stay facing the user even if something mutated dir.
        } else if (state == State.WORK) {
          tickWork(dtSeconds)
        }
        return
      }
    val dx = next.x - position.x
    val dy = next.y - position.y
    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (dist < 2f) {
      position = next
      waypoints.removeFirst()
      return
    }
    val step = (speedWorldUnitsPerSec * dtSeconds).coerceAtMost(dist)
    position = Offset(position.x + dx / dist * step, position.y + dy / dist * step)
    // Facing rule: vertical component always wins → back/front whenever |dy| > 0.
    dir = when {
      kotlin.math.abs(dy) > 0.01f -> if (dy > 0) 0 else 3
      dx > 0 -> 2
      dx < 0 -> 1
      else -> dir
    }
    state = State.WALK
  }

  /** Orbit update for [AssetType.NORMAL] work. No-op for other types (stationary). */
  private fun tickWork(dtSeconds: Float) {
    if (workAssetType != AssetType.NORMAL) return
    val center = orbitCenter ?: position.also { orbitCenter = it }
    orbitAngle += orbitAngularSpeedRadPerSec * dtSeconds
    val px = center.x + kotlin.math.cos(orbitAngle) * orbitRadius
    val py = center.y + kotlin.math.sin(orbitAngle) * orbitRadius
    val prev = position
    position = Offset(px, py)
    // Facing follows tangent with vertical dominance (matches the walk rule).
    val tdx = position.x - prev.x
    val tdy = position.y - prev.y
    dir = when {
      kotlin.math.abs(tdy) > 0.01f -> if (tdy > 0) 0 else 3
      tdx > 0 -> 2
      tdx < 0 -> 1
      else -> dir
    }
  }

  /** Replace the pending route with the given ordered list of world points. */
  fun walkPath(
    points: List<Offset>,
    workingCapabilityId: String? = null,
    workAssetType: AssetType? = null,
  ) {
    waypoints.clear()
    points.forEach { waypoints.addLast(it) }
    this.workingCapabilityId = workingCapabilityId
    this.workAssetType = workAssetType
    // Moving to a new target ⇒ clear any orbit anchor from the previous WORK.
    this.orbitCenter = null
    this.orbitAngle = 0f
    if (waypoints.isNotEmpty()) state = State.WALK
  }

  /** Convenience: single-waypoint route. */
  fun walkTo(
    worldPoint: Offset,
    workingCapabilityId: String? = null,
    workAssetType: AssetType? = null,
  ) = walkPath(listOf(worldPoint), workingCapabilityId, workAssetType)

  /** Teleport (no animation). Clears any pending route. */
  fun jumpTo(pos: Offset) {
    position = pos
    waypoints.clear()
    orbitCenter = null
    state = if (workingCapabilityId != null) State.WORK else State.IDLE
    if (state == State.IDLE) dir = 0
  }

  fun finishWork() {
    workingCapabilityId = null
    workAssetType = null
    orbitCenter = null
    if (waypoints.isEmpty()) {
      state = State.IDLE
      dir = 0
    }
  }

  /** Current end of the pending route (= where the cat will end up), or [position] if idle. */
  val target: Offset
    get() = waypoints.lastOrNull() ?: position
}
