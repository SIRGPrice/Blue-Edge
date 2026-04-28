/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform mirror of `:app/.../data/Consts.kt`.
 * Drops Android `Build.SOC_MODEL` and `Compose.dp` units (those stay in :app
 * or are surfaced through expect/actual when actually needed by shared UI).
 */
package com.blueedge.shared.domain

// Keys used to send/receive data to background download workers.
const val KEY_MODEL_URL = "KEY_MODEL_URL"
const val KEY_MODEL_NAME = "KEY_MODEL_NAME"
const val KEY_MODEL_COMMIT_HASH = "KEY_MODEL_COMMIT_HASH"
const val KEY_MODEL_DOWNLOAD_MODEL_DIR = "KEY_MODEL_DOWNLOAD_MODEL_DIR"
const val KEY_MODEL_DOWNLOAD_FILE_NAME = "KEY_MODEL_DOWNLOAD_FILE_NAME"
const val KEY_MODEL_TOTAL_BYTES = "KEY_MODEL_TOTAL_BYTES"
const val KEY_MODEL_DOWNLOAD_RECEIVED_BYTES = "KEY_MODEL_DOWNLOAD_RECEIVED_BYTES"
const val KEY_MODEL_DOWNLOAD_RATE = "KEY_MODEL_DOWNLOAD_RATE"
const val KEY_MODEL_DOWNLOAD_REMAINING_MS = "KEY_MODEL_DOWNLOAD_REMAINING_SECONDS"
const val KEY_MODEL_DOWNLOAD_ERROR_MESSAGE = "KEY_MODEL_DOWNLOAD_ERROR_MESSAGE"
const val KEY_MODEL_DOWNLOAD_ACCESS_TOKEN = "KEY_MODEL_DOWNLOAD_ACCESS_TOKEN"
const val KEY_MODEL_EXTRA_DATA_URLS = "KEY_MODEL_EXTRA_DATA_URLS"
const val KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES = "KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES"
const val KEY_MODEL_IS_ZIP = "KEY_MODEL_IS_ZIP"
const val KEY_MODEL_UNZIPPED_DIR = "KEY_MODEL_UNZIPPED_DIR"
const val KEY_MODEL_START_UNZIPPING = "KEY_MODEL_START_UNZIPPING"

// Default values for LLM models.
const val DEFAULT_MAX_TOKEN = 1024
const val DEFAULT_TOPK = 64
const val DEFAULT_TOPP = 0.95f
const val DEFAULT_TEMPERATURE = 1.0f
val DEFAULT_ACCELERATORS: List<Accelerator> = listOf(Accelerator.GPU)
val DEFAULT_VISION_ACCELERATOR: Accelerator = Accelerator.GPU

const val MAX_IMAGE_COUNT = 10
const val MAX_IMAGE_COUNT_AI_CORE = 1
const val MAX_AUDIO_CLIP_COUNT = 1
const val MAX_AUDIO_CLIP_DURATION_SEC = 30
const val SAMPLE_RATE = 16000
const val MODEL_INFO_ICON_SIZE_DP = 18
const val TMP_FILE_EXT = "gallerytmp"

