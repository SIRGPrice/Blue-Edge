/*
 * Copyright 2026 Blue Edge contributors.
 *
 * View-model for the multiplatform Home screen. Pure logic over the built-in
 * task catalog: no platform/IO dependencies, fully unit-testable.
 *
 * Surfaces a [HomeUiState] with categories, tasks-per-category, the current
 * filter (selected category + free-text query) and helpers for the UI layer.
 */
package com.blueedge.shared.ui.home

import com.blueedge.shared.domain.CategoryInfo
import com.blueedge.shared.domain.Task
import com.blueedge.shared.domain.builtInTasks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
  val categories: List<CategoryInfo> = emptyList(),
  val selectedCategoryId: String? = null,
  val query: String = "",
  val visibleTasks: List<Task> = emptyList(),
)

class HomeViewModel(
  private val tasks: List<Task> = builtInTasks(),
) {
  private val _state = MutableStateFlow(initial())
  val state: StateFlow<HomeUiState> = _state.asStateFlow()

  private fun initial(): HomeUiState {
    val cats = tasks.map { it.category }.distinctBy { it.id }
    val firstId = cats.firstOrNull()?.id
    return HomeUiState(
      categories = cats,
      selectedCategoryId = firstId,
      query = "",
      visibleTasks = tasks.filter { it.category.id == firstId },
    )
  }

  fun selectCategory(categoryId: String) {
    _state.value = recompute(_state.value.copy(selectedCategoryId = categoryId))
  }

  fun setQuery(query: String) {
    _state.value = recompute(_state.value.copy(query = query))
  }

  private fun recompute(s: HomeUiState): HomeUiState {
    val byCat = if (s.selectedCategoryId == null) tasks
    else tasks.filter { it.category.id == s.selectedCategoryId }
    val q = s.query.trim().lowercase()
    val filtered = if (q.isEmpty()) byCat
    else byCat.filter { t ->
      t.label.lowercase().contains(q) ||
        t.shortDescription.lowercase().contains(q) ||
        t.description.lowercase().contains(q)
    }
    return s.copy(visibleTasks = filtered)
  }
}

