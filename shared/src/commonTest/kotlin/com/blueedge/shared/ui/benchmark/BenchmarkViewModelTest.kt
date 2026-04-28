/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.ui.benchmark

import com.blueedge.shared.domain.ModelFile
import com.blueedge.shared.testing.FakeLlmEngine
import com.blueedge.shared.testing.FakeModelStorage
import com.blueedge.shared.testing.newSettings
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BenchmarkViewModelTest {

  private fun fileAt(name: String, path: String) =
    ModelFile(name = name, absolutePath = path, sizeInBytes = 1024L, isDirectory = false)

  @Test
  fun preselects_last_loaded_path() = runTest {
    val storage = FakeModelStorage(initial = listOf(
      fileAt("a.task", "/tmp/a.task"),
      fileAt("b.task", "/tmp/b.task"),
    ))
    val settings = newSettings().also { it.lastLoadedModelPath = "/tmp/b.task" }
    val vm = BenchmarkViewModel(
      runner = BenchmarkRunner(FakeLlmEngine()),
      storage = storage,
      settings = settings,
      scope = TestScope(UnconfinedTestDispatcher()),
    )
    assertEquals("/tmp/b.task", vm.state.value.selectedModelPath)
  }

  @Test
  fun run_persists_path_and_records_summary() = runTest {
    val storage = FakeModelStorage(initial = listOf(fileAt("m.task", "/tmp/m.task")))
    val settings = newSettings()
    val vm = BenchmarkViewModel(
      runner = BenchmarkRunner(FakeLlmEngine(tokens = listOf("a", "b"))),
      storage = storage,
      settings = settings,
      scope = TestScope(UnconfinedTestDispatcher()),
    )
    vm.run()
    val s = vm.state.value
    assertNotNull(s.latest)
    assertEquals(2, s.latest!!.outputTokens)
    assertEquals("/tmp/m.task", settings.lastLoadedModelPath)
  }

  @Test
  fun run_without_selection_emits_error() = runTest {
    val storage = FakeModelStorage(initial = emptyList())
    val vm = BenchmarkViewModel(
      runner = BenchmarkRunner(FakeLlmEngine()),
      storage = storage,
      settings = newSettings(),
      scope = TestScope(UnconfinedTestDispatcher()),
    )
    vm.run()
    val msg = vm.state.value.errorMessage
    assertNotNull(msg)
    vm.dismissError()
    assertNull(vm.state.value.errorMessage)
  }
}

