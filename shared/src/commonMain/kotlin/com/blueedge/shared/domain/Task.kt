/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform mirror of `:app/.../data/Tasks.kt` and `BuiltInTaskId`.
 *
 * Differences vs the Android original:
 *  - No `ImageVector` / `iconVectorResourceId`. Use `iconKey: String` that
 *    each platform maps to its own icon registry.
 *  - No `MutableState` `updateTrigger` (Compose Multiplatform compatibility
 *    is fine, but the trigger is an Android-side concern wired via the VM).
 *  - `models` is an immutable `List<Model>` here; mutation happens through
 *    the `ModelManagerViewModel` once that screen migrates.
 *  - Drops `@StringRes` placeholders in favor of `String?` keys.
 */
package com.blueedge.shared.domain

import kotlinx.serialization.Serializable

object BuiltInTaskId {
  const val LLM_CHAT = "llm_chat"
  const val LLM_PROMPT_LAB = "llm_prompt_lab"
  const val LLM_ASK_IMAGE = "llm_ask_image"
  const val LLM_ASK_AUDIO = "llm_ask_audio"
  const val LLM_MOBILE_ACTIONS = "llm_mobile_actions"
  const val LLM_TINY_GARDEN = "llm_tiny_garden"
  const val MP_SCRAPBOOK = "mp_scrapbook"
  const val LLM_AGENT_CHAT = "llm_agent_chat"
}

@Serializable
data class Task(
  val id: String,
  val label: String,
  val category: CategoryInfo,
  /** Platform-resolvable icon key (e.g. "chat", "image", "audio"). */
  val iconKey: String? = null,
  val description: String,
  val shortDescription: String = "",
  val docUrl: String = "",
  val sourceCodeUrl: String = "",
  val models: List<Model> = emptyList(),
  val modelNames: List<String> = emptyList(),
  val handleModelConfigChangesInTask: Boolean = false,
  val experimental: Boolean = false,
  val newFeature: Boolean = false,
  val useThemeColor: Boolean = false,
  val defaultSystemPrompt: String = "",
  val agentNameKey: String = "chat_generic_agent_name",
  val textInputPlaceholderKey: String = "chat_textinput_placeholder",
  val index: Int = -1,
) {
  fun allowThinking(): Boolean =
    id == BuiltInTaskId.LLM_CHAT ||
      id == BuiltInTaskId.LLM_ASK_IMAGE ||
      id == BuiltInTaskId.LLM_ASK_AUDIO ||
      id == BuiltInTaskId.LLM_AGENT_CHAT ||
      id == BuiltInTaskId.LLM_TINY_GARDEN
}

private val legacyTaskIds: Set<String> = setOf(
  BuiltInTaskId.LLM_CHAT,
  BuiltInTaskId.LLM_PROMPT_LAB,
  BuiltInTaskId.LLM_ASK_IMAGE,
  BuiltInTaskId.LLM_ASK_AUDIO,
  BuiltInTaskId.LLM_AGENT_CHAT,
)

fun isLegacyTasks(id: String): Boolean = legacyTaskIds.contains(id)

