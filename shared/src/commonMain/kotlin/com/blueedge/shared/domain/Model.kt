/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform mirror of `app/src/main/java/com/google/ai/edge/gallery/data/Model.kt`,
 * cleaned of Android-specific dependencies (Context, java.io.File, Gson):
 *   - Kotlinx-serialization replaces Gson `@SerializedName` annotations.
 *   - Path resolution is delegated to a `ModelStorage` interface that has
 *     platform-specific actuals (Android = ContextCompat.getExternalFilesDir,
 *     iOS = NSFileManager / Application Support directory).
 *
 * The Android `:app` continues to use its existing `Model.kt`; this mirror
 * exists so the iOS UI can ship without waiting for the full migration.
 */
package com.blueedge.shared.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RuntimeType {
  @SerialName("unknown") UNKNOWN,
  @SerialName("litert_lm") LITERT_LM,
  @SerialName("aicore") AICORE,
  // iOS-only runtimes that the Android side does not have:
  @SerialName("mediapipe_genai_ios") MEDIAPIPE_GENAI_IOS,
  @SerialName("tflite_swift") TFLITE_SWIFT,
}

@Serializable
enum class Accelerator {
  @SerialName("cpu") CPU,
  @SerialName("gpu") GPU,
  /** CoreML delegate on Apple platforms. */
  @SerialName("coreml") COREML,
  /** NNAPI delegate on Android. */
  @SerialName("nnapi") NNAPI,
}

@Serializable
data class ModelDataFile(
  val name: String,
  val url: String,
  val downloadFileName: String,
  val sizeInBytes: Long,
)

@Serializable
data class PromptTemplate(
  val title: String,
  val description: String,
  val prompt: String,
)

@Serializable
data class Model(
  val name: String,
  val displayName: String = "",
  val info: String = "",
  val learnMoreUrl: String = "",
  val bestForTaskIds: List<String> = emptyList(),
  val minDeviceMemoryInGb: Int? = null,

  // Download
  val url: String = "",
  val sizeInBytes: Long = 0L,
  val downloadFileName: String = "_",
  val version: String = "_",
  val extraDataFiles: List<ModelDataFile> = emptyList(),

  val isLlm: Boolean = false,
  val runtimeType: RuntimeType = RuntimeType.UNKNOWN,

  // Local override
  val localFileRelativeDirPathOverride: String = "",
  val localModelFilePathOverride: String = "",

  val showRunAgainButton: Boolean = true,
  val showBenchmarkButton: Boolean = true,

  val isZip: Boolean = false,
  val unzipDir: String = "",

  val llmPromptTemplates: List<PromptTemplate> = emptyList(),
  val llmSupportImage: Boolean = false,
  val llmSupportAudio: Boolean = false,
  val llmSupportThinking: Boolean = false,
  val llmMaxToken: Int = 0,

  val accelerators: List<Accelerator> = emptyList(),
  val visionAccelerator: Accelerator = Accelerator.GPU,

  val imported: Boolean = false,
) {
  val normalizedName: String get() =
    name.replace(Regex("[^a-zA-Z0-9]"), "_")

  val totalBytes: Long get() =
    sizeInBytes + extraDataFiles.sumOf { it.sizeInBytes }
}

@Serializable
enum class ModelDownloadStatusType {
  NOT_DOWNLOADED, PARTIALLY_DOWNLOADED, IN_PROGRESS, UNZIPPING, SUCCEEDED, FAILED,
}

@Serializable
data class ModelDownloadStatus(
  val status: ModelDownloadStatusType,
  val totalBytes: Long = 0,
  val receivedBytes: Long = 0,
  val errorMessage: String = "",
  val bytesPerSecond: Long = 0,
  val remainingMs: Long = 0,
)

