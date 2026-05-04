/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.ui.home

import com.blueedge.shared.domain.Category
import com.blueedge.shared.domain.Task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeViewModelTest {

  private val sample = listOf(
    Task(id = "a", label = "Alpha", category = Category.LLM, iconKey = "chat",
      description = "alpha desc", shortDescription = "alpha"),
    Task(id = "b", label = "Beta", category = Category.LLM, iconKey = "chat",
      description = "beta desc", shortDescription = "beta"),
    Task(id = "c", label = "Gamma", category = Category.EXPERIMENTAL, iconKey = "image",
      description = "experimental gamma", shortDescription = "gamma"),
  )

  @Test
  fun initialStateSelectsFirstCategory() {
    val vm = HomeViewModel(sample)
    val s = vm.state.value
    assertEquals(Category.LLM.id, s.selectedCategoryId)
    assertEquals(2, s.visibleTasks.size)
    assertTrue(s.visibleTasks.all { it.category.id == Category.LLM.id })
  }

  @Test
  fun selectCategoryFiltersTasks() {
    val vm = HomeViewModel(sample)
    vm.selectCategory(Category.EXPERIMENTAL.id)
    val s = vm.state.value
    assertEquals(1, s.visibleTasks.size)
    assertEquals("c", s.visibleTasks.single().id)
  }

  @Test
  fun queryFiltersWithinCategory() {
    val vm = HomeViewModel(sample)
    vm.setQuery("bet")
    val s = vm.state.value
    assertEquals(1, s.visibleTasks.size)
    assertEquals("b", s.visibleTasks.single().id)
  }

  @Test
  fun emptyQueryRestoresCategoryListing() {
    val vm = HomeViewModel(sample)
    vm.setQuery("alp")
    vm.setQuery("")
    assertEquals(2, vm.state.value.visibleTasks.size)
  }
}

