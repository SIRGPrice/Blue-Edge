/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.ui.benchmark

import com.blueedge.shared.runtime.LlmEvent
import com.blueedge.shared.runtime.LlmGenerationConfig
import com.blueedge.shared.runtime.LlmModelDescriptor
import com.blueedge.shared.testing.FakeLlmEngine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkRunnerTest {

  @Test
  fun counts_tokens_and_reports_metrics() = runTest {
    val engine = FakeLlmEngine(tokens = listOf("a", "b", "c", "d"))
    val runner = BenchmarkRunner(engine)
    val summary = runner.run(LlmModelDescriptor("/tmp/x.task"), prompt = "hi")
    assertEquals(4, summary.outputTokens)
    assertTrue(summary.prefillMs >= 0L)
    assertTrue(summary.decodeMs >= 0L)
  }

  @Test
  fun rejects_unavailable_model() = runTest {
    val engine = FakeLlmEngine(available = false)
    val runner = BenchmarkRunner(engine)
    var threw = false
    runCatching { runner.run(LlmModelDescriptor("/tmp/x.task")) }
      .onFailure { threw = true }
    assertTrue(threw, "Expected runner.run to throw when engine is unavailable")
  }
}

