/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform mirror of `:app/.../data/Config.kt`.
 *
 * Changes vs the Android original:
 *  - Drops `@StringRes` (Android-only). `BottomSheetSelectorConfig` now holds
 *    a `bottomSheetTitleKey: String?` resolved per-platform.
 *  - Uses `kotlin.math.abs` from common stdlib.
 */
package com.blueedge.shared.domain

import kotlin.math.abs

enum class ConfigEditorType {
  LABEL,
  NUMBER_SLIDER,
  BOOLEAN_SWITCH,
  SEGMENTED_BUTTON,
  BOTTOMSHEET_SELECTOR,
}

enum class ValueType {
  INT,
  FLOAT,
  DOUBLE,
  STRING,
  BOOLEAN,
}

data class ConfigKey(val id: String, val label: String)

object ConfigKeys {
  val MAX_TOKENS = ConfigKey("max_tokens", "Max tokens")
  val TOPK = ConfigKey("topk", "TopK")
  val TOPP = ConfigKey("topp", "TopP")
  val TEMPERATURE = ConfigKey("temperature", "Temperature")
  val DEFAULT_MAX_TOKENS = ConfigKey("default_max_tokens", "Default max tokens")
  val DEFAULT_TOPK = ConfigKey("default_topk", "Default TopK")
  val DEFAULT_TOPP = ConfigKey("default_topp", "Default TopP")
  val DEFAULT_TEMPERATURE = ConfigKey("default_temperature", "Default temperature")
  val SUPPORT_IMAGE = ConfigKey("support_image", "Support image")
  val SUPPORT_AUDIO = ConfigKey("support_audio", "Support audio")
  val SUPPORT_TINY_GARDEN = ConfigKey("support_tiny_garden", "Support tiny garden")
  val SUPPORT_MOBILE_ACTIONS = ConfigKey("support_mobile_actions", "Support mobile actions")
  val SUPPORT_THINKING = ConfigKey("support_thinking", "Support thinking")
  val ENABLE_THINKING = ConfigKey("enable_thinking", "Enable thinking")
  val MAX_RESULT_COUNT = ConfigKey("max_result_count", "Max result count")
  val USE_GPU = ConfigKey("use_gpu", "Use GPU")
  val ACCELERATOR = ConfigKey("accelerator", "Accelerator")
  val VISION_ACCELERATOR = ConfigKey("vision_accelerator", "Vision accelerator")
  val COMPATIBLE_ACCELERATORS = ConfigKey("compatible_accelerators", "Compatible accelerators")
  val WARM_UP_ITERATIONS = ConfigKey("warm_up_iterations", "Warm up iterations")
  val BENCHMARK_ITERATIONS = ConfigKey("benchmark_iterations", "Benchmark iterations")
  val ITERATIONS = ConfigKey("iterations", "Iterations")
  val THEME = ConfigKey("theme", "Theme")
  val NAME = ConfigKey("name", "Name")
  val MODEL_TYPE = ConfigKey("model_type", "Model type")
  val MODEL = ConfigKey("model", "Model")
  val RESET_CONVERSATION_TURN_COUNT =
    ConfigKey("reset_conversation_turn_count", "Number of turns before the conversation resets")
  val PREFILL_TOKENS = ConfigKey("prefill_tokens", "Prefill tokens")
  val DECODE_TOKENS = ConfigKey("decode_tokens", "Decode tokens")
  val NUMBER_OF_RUNS = ConfigKey("number_of_runs", "Number of runs")
}

open class Config(
  val type: ConfigEditorType,
  open val key: ConfigKey,
  open val defaultValue: Any,
  open val valueType: ValueType,
  open val needReinitialization: Boolean = true,
)

class LabelConfig(override val key: ConfigKey, override val defaultValue: String = "") :
  Config(
    type = ConfigEditorType.LABEL,
    key = key,
    defaultValue = defaultValue,
    valueType = ValueType.STRING,
  )

class NumberSliderConfig(
  override val key: ConfigKey,
  val sliderMin: Float,
  val sliderMax: Float,
  override val defaultValue: Float,
  override val valueType: ValueType,
  override val needReinitialization: Boolean = true,
) : Config(
  type = ConfigEditorType.NUMBER_SLIDER,
  key = key,
  defaultValue = defaultValue,
  valueType = valueType,
)

class BooleanSwitchConfig(
  override val key: ConfigKey,
  override val defaultValue: Boolean,
  override val needReinitialization: Boolean = true,
) : Config(
  type = ConfigEditorType.BOOLEAN_SWITCH,
  key = key,
  defaultValue = defaultValue,
  valueType = ValueType.BOOLEAN,
)

class SegmentedButtonConfig(
  override val key: ConfigKey,
  override val defaultValue: String,
  val options: List<String>,
  val allowMultiple: Boolean = false,
) : Config(
  type = ConfigEditorType.SEGMENTED_BUTTON,
  key = key,
  defaultValue = defaultValue,
  valueType = ValueType.STRING,
)

data class BottomSheetSelectorItem(val label: String)

class BottomSheetSelectorConfig(
  override val key: ConfigKey,
  override val defaultValue: String,
  val options: List<BottomSheetSelectorItem>,
  val bottomSheetTitleKey: String? = null,
) : Config(
  type = ConfigEditorType.BOTTOMSHEET_SELECTOR,
  key = key,
  defaultValue = defaultValue,
  valueType = ValueType.STRING,
)

fun convertValueToTargetType(value: Any, valueType: ValueType): Any {
  return when (valueType) {
    ValueType.INT -> when (value) {
      is Int -> value
      is Float -> value.toInt()
      is Double -> value.toInt()
      is String -> value.toIntOrNull() ?: ""
      is Boolean -> if (value) 1 else 0
      else -> ""
    }
    ValueType.FLOAT -> when (value) {
      is Int -> value.toFloat()
      is Float -> value
      is Double -> value.toFloat()
      is String -> value.toFloatOrNull() ?: ""
      is Boolean -> if (value) 1f else 0f
      else -> ""
    }
    ValueType.DOUBLE -> when (value) {
      is Int -> value.toDouble()
      is Float -> value.toDouble()
      is Double -> value
      is String -> value.toDoubleOrNull() ?: ""
      is Boolean -> if (value) 1.0 else 0.0
      else -> ""
    }
    ValueType.BOOLEAN -> when (value) {
      is Int -> value == 0
      is Boolean -> value
      is Float -> abs(value) > 1e-6
      is Double -> abs(value) > 1e-6
      is String -> value.isNotEmpty()
      else -> false
    }
    ValueType.STRING -> value.toString()
  }
}

fun createLlmChatConfigs(
  defaultMaxToken: Int = DEFAULT_MAX_TOKEN,
  defaultMaxContextLength: Int? = null,
  accelerators: List<Accelerator> = DEFAULT_ACCELERATORS,
  supportThinking: Boolean = false,
): List<Config> {
  var maxTokensConfig: Config =
    LabelConfig(key = ConfigKeys.MAX_TOKENS, defaultValue = "$defaultMaxToken")
  if (defaultMaxContextLength != null) {
    maxTokensConfig = NumberSliderConfig(
      key = ConfigKeys.MAX_TOKENS,
      sliderMin = 2000f,
      sliderMax = defaultMaxContextLength.toFloat(),
      defaultValue = defaultMaxToken.toFloat(),
      valueType = ValueType.INT,
    )
  }
  val configs = mutableListOf<Config>(
    maxTokensConfig,
    SegmentedButtonConfig(
      key = ConfigKeys.ACCELERATOR,
      defaultValue = accelerators[0].label,
      options = accelerators.map { it.label },
    ),
  )
  if (supportThinking) {
    configs.add(BooleanSwitchConfig(key = ConfigKeys.ENABLE_THINKING, defaultValue = false))
  }
  return configs
}

fun createLlmChatConfigsForNpuModel(
  defaultMaxToken: Int = DEFAULT_MAX_TOKEN,
  accelerators: List<Accelerator> = DEFAULT_ACCELERATORS,
): List<Config> = listOf(
  LabelConfig(key = ConfigKeys.MAX_TOKENS, defaultValue = "$defaultMaxToken"),
  SegmentedButtonConfig(
    key = ConfigKeys.ACCELERATOR,
    defaultValue = accelerators[0].label,
    options = accelerators.map { it.label },
  ),
)

fun createAICoreConfigs(
  defaultMaxToken: Int = DEFAULT_MAX_TOKEN,
  accelerators: List<Accelerator> = DEFAULT_ACCELERATORS,
): List<Config> = listOf(
  LabelConfig(key = ConfigKeys.MAX_TOKENS, defaultValue = "$defaultMaxToken"),
  SegmentedButtonConfig(
    key = ConfigKeys.ACCELERATOR,
    defaultValue = accelerators[0].label,
    options = accelerators.map { it.label },
  ),
)

fun getConfigValueString(value: Any, config: Config): String {
  // %.2f formatting differs subtly between JVM and Native; use a small helper.
  if (config.valueType == ValueType.FLOAT) {
    val f = (value as? Number)?.toFloat() ?: return value.toString()
    val rounded = (kotlin.math.round(f * 100f)) / 100f
    val s = rounded.toString()
    val dot = s.indexOf('.')
    if (dot < 0) return "$s.00"
    val frac = s.length - dot - 1
    return when {
      frac >= 2 -> s.substring(0, dot + 3)
      frac == 1 -> s + "0"
      else -> "$s.00"
    }
  }
  return value.toString()
}

