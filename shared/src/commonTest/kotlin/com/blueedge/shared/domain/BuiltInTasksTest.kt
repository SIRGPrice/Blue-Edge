/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuiltInTasksTest {

  @Test
  fun catalogIsNotEmpty() {
    assertTrue(builtInTasks().isNotEmpty(), "Built-in catalog must not be empty")
  }

  @Test
  fun taskIdsAreUnique() {
    val ids = builtInTasks().map { it.id }
    assertEquals(ids.size, ids.distinct().size, "Task ids must be unique")
  }

  @Test
  fun everyTaskHasIconAndShortDescription() {
    builtInTasks().forEach { t ->
      assertNotNull(t.iconKey, "Task ${t.id} missing iconKey")
      assertTrue(t.shortDescription.isNotBlank(), "Task ${t.id} missing shortDescription")
    }
  }

  @Test
  fun findBuiltInTaskRoundTrip() {
    val first = builtInTasks().first()
    assertEquals(first, findBuiltInTask(first.id))
    assertEquals(null, findBuiltInTask("nonexistent_task_id"))
  }

  @Test
  fun tasksByCategoryCoversAllTasks() {
    val total = builtInTasks().size
    val grouped = tasksByCategory().values.sumOf { it.size }
    assertEquals(total, grouped)
  }
}

