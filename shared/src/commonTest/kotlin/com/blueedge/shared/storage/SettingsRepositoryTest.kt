/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.storage

import com.blueedge.shared.testing.newSettings
import com.blueedge.shared.ui.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoryTest {

  @Test
  fun defaults_are_sensible() {
    val s = newSettings()
    assertEquals(ThemeMode.AUTO, s.themeMode)
    assertEquals(emptyList(), s.textInputHistory)
    assertEquals(false, s.tosAccepted)
    assertEquals(false, s.gemmaTermsAccepted)
    assertNull(s.lastLoadedModelPath)
  }

  @Test
  fun roundtrips_typed_accessors() {
    val s = newSettings()
    s.themeMode = ThemeMode.DARK
    s.tosAccepted = true
    s.gemmaTermsAccepted = true
    s.lastLoadedModelPath = "/tmp/models/gemma.task"
    s.textInputHistory = listOf("hello", "world")

    assertEquals(ThemeMode.DARK, s.themeMode)
    assertTrue(s.tosAccepted)
    assertTrue(s.gemmaTermsAccepted)
    assertEquals("/tmp/models/gemma.task", s.lastLoadedModelPath)
    assertEquals(listOf("hello", "world"), s.textInputHistory)
  }

  @Test
  fun lastLoadedModelPath_clears_on_null_or_blank() {
    val s = newSettings()
    s.lastLoadedModelPath = "/tmp/x.task"
    s.lastLoadedModelPath = null
    assertNull(s.lastLoadedModelPath)
    s.lastLoadedModelPath = ""
    assertNull(s.lastLoadedModelPath)
  }
}

