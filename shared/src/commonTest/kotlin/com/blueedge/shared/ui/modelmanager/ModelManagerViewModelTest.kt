/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.ui.modelmanager

import com.blueedge.shared.domain.ModelFile
import com.blueedge.shared.testing.FakeLlmEngine
import com.blueedge.shared.testing.FakeModelImporter
import com.blueedge.shared.testing.FakeModelStorage
import com.blueedge.shared.testing.newSettings
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelManagerViewModelTest {

  private fun fileAt(name: String, path: String, size: Long = 100L) =
    ModelFile(name = name, absolutePath = path, sizeInBytes = size, isDirectory = false)

  @Test
  fun initial_state_lists_models_and_marks_active() = runTest {
    val storage = FakeModelStorage(initial = listOf(
      fileAt("a.task", "/tmp/a.task"),
      fileAt("b.task", "/tmp/b.task"),
    ))
    val settings = newSettings().also { it.lastLoadedModelPath = "/tmp/b.task" }
    val vm = ModelManagerViewModel(
      storage = storage,
      engine = FakeLlmEngine(),
      settings = settings,
      importer = FakeModelImporter(isSupported = false),
      scope = TestScope(UnconfinedTestDispatcher()),
    )
    val s = vm.state.value
    assertEquals(2, s.files.size)
    assertEquals("/tmp/b.task", s.activeModelPath)
    assertEquals(false, s.canImport)
  }

  @Test
  fun useModel_persists_and_loads_engine() = runTest {
    val storage = FakeModelStorage(initial = listOf(fileAt("m.task", "/tmp/m.task")))
    val settings = newSettings()
    val engine = FakeLlmEngine()
    val vm = ModelManagerViewModel(
      storage = storage,
      engine = engine,
      settings = settings,
      importer = FakeModelImporter(),
      scope = TestScope(UnconfinedTestDispatcher()),
    )
    vm.useModel(storage.files.first())
    assertEquals("/tmp/m.task", settings.lastLoadedModelPath)
    assertEquals("/tmp/m.task", engine.loadedDescriptor?.modelPath)
    assertEquals("/tmp/m.task", vm.state.value.activeModelPath)
  }

  @Test
  fun importModel_no_op_when_unsupported() = runTest {
    val storage = FakeModelStorage()
    val importer = FakeModelImporter(isSupported = false)
    val vm = ModelManagerViewModel(
      storage = storage,
      engine = FakeLlmEngine(),
      settings = newSettings(),
      importer = importer,
      scope = TestScope(UnconfinedTestDispatcher()),
    )
    vm.importModel()
    assertEquals(0, importer.calls)
    assertNull(vm.state.value.message)
  }

  @Test
  fun importModel_refreshes_on_success() = runTest {
    val storage = FakeModelStorage()
    val importer = FakeModelImporter(isSupported = true, nextResult = listOf("/tmp/new.task"))
    val vm = ModelManagerViewModel(
      storage = storage,
      engine = FakeLlmEngine(),
      settings = newSettings(),
      importer = importer,
      scope = TestScope(UnconfinedTestDispatcher()),
    )
    storage.files.add(fileAt("new.task", "/tmp/new.task"))
    vm.importModel()
    assertEquals(1, importer.calls)
    assertTrue(vm.state.value.files.any { it.absolutePath == "/tmp/new.task" })
  }
}

