/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Test fakes shared between common-test cases.
 */
package com.blueedge.shared.testing

import com.blueedge.shared.domain.ModelFile
import com.blueedge.shared.domain.ModelImporter
import com.blueedge.shared.domain.ModelStorage
import com.blueedge.shared.domain.Model
import com.blueedge.shared.runtime.LlmEngine
import com.blueedge.shared.runtime.LlmEvent
import com.blueedge.shared.runtime.LlmGenerationConfig
import com.blueedge.shared.runtime.LlmModelDescriptor
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLlmEngine(
  var available: Boolean = true,
  var loadDelayMs: Long = 0L,
  val tokens: List<String> = listOf("Hello", " ", "world", "!"),
  var failOnGenerate: Boolean = false,
) : LlmEngine {
  var loadedDescriptor: LlmModelDescriptor? = null
  var closed: Boolean = false

  override suspend fun isAvailable(descriptor: LlmModelDescriptor) = available

  override suspend fun load(descriptor: LlmModelDescriptor) {
    loadedDescriptor = descriptor
  }

  override fun generate(
    prompt: String,
    config: LlmGenerationConfig,
    images: List<ByteArray>,
  ): Flow<LlmEvent> = flow {
    if (failOnGenerate) {
      emit(LlmEvent.Error("forced failure"))
      return@flow
    }
    tokens.forEach { emit(LlmEvent.Token(it)) }
    emit(LlmEvent.Done)
  }

  override suspend fun close() { closed = true }
}

class FakeModelStorage(
  override val baseModelsDir: String = "/tmp/models",
  initial: List<ModelFile> = emptyList(),
) : ModelStorage {
  var files: MutableList<ModelFile> = initial.toMutableList()
  override fun resolvePath(model: Model, fileName: String) = "$baseModelsDir/$fileName"
  override fun listModelFiles(): List<ModelFile> = files.toList()
}

class FakeModelImporter(
  override val isSupported: Boolean = true,
  var nextResult: List<String> = emptyList(),
) : ModelImporter {
  var calls: Int = 0
  override suspend fun pickAndImport(): List<String> {
    calls += 1
    return nextResult
  }
}

fun newSettings() = com.blueedge.shared.storage.SettingsRepository(InMemorySettings())

/** Minimal in-memory `Settings` for unit tests (avoids the optional
 *  `multiplatform-settings-test` dependency). */
class InMemorySettings : Settings {
  private val data = mutableMapOf<String, Any?>()

  override val keys: Set<String> get() = data.keys.toSet()
  override val size: Int get() = data.size
  override fun clear() = data.clear()
  override fun remove(key: String) { data.remove(key) }
  override fun hasKey(key: String): Boolean = data.containsKey(key)

  override fun putInt(key: String, value: Int) { data[key] = value }
  override fun getInt(key: String, defaultValue: Int): Int = (data[key] as? Int) ?: defaultValue
  override fun getIntOrNull(key: String): Int? = data[key] as? Int

  override fun putLong(key: String, value: Long) { data[key] = value }
  override fun getLong(key: String, defaultValue: Long): Long = (data[key] as? Long) ?: defaultValue
  override fun getLongOrNull(key: String): Long? = data[key] as? Long

  override fun putString(key: String, value: String) { data[key] = value }
  override fun getString(key: String, defaultValue: String): String = (data[key] as? String) ?: defaultValue
  override fun getStringOrNull(key: String): String? = data[key] as? String

  override fun putFloat(key: String, value: Float) { data[key] = value }
  override fun getFloat(key: String, defaultValue: Float): Float = (data[key] as? Float) ?: defaultValue
  override fun getFloatOrNull(key: String): Float? = data[key] as? Float

  override fun putDouble(key: String, value: Double) { data[key] = value }
  override fun getDouble(key: String, defaultValue: Double): Double = (data[key] as? Double) ?: defaultValue
  override fun getDoubleOrNull(key: String): Double? = data[key] as? Double

  override fun putBoolean(key: String, value: Boolean) { data[key] = value }
  override fun getBoolean(key: String, defaultValue: Boolean): Boolean = (data[key] as? Boolean) ?: defaultValue
  override fun getBooleanOrNull(key: String): Boolean? = data[key] as? Boolean
}



