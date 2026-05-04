/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Built-in catalog of [Task] entries shown on the Home screen.
 *
 * Multiplatform-only: no Android resources, no ImageVector. Each task carries
 * an `iconKey` resolved by `IconRegistry.iconFor(...)` per platform target.
 *
 * Adding a task = appending an entry here. The order of [builtInTasks] is the
 * display order within its [CategoryInfo]; consumers may sort/filter further.
 */
package com.blueedge.shared.domain

private fun task(
  id: String,
  label: String,
  category: CategoryInfo,
  iconKey: String,
  shortDescription: String,
  description: String = shortDescription,
  experimental: Boolean = false,
  newFeature: Boolean = false,
  defaultSystemPrompt: String = "",
): Task = Task(
  id = id,
  label = label,
  category = category,
  iconKey = iconKey,
  shortDescription = shortDescription,
  description = description,
  experimental = experimental,
  newFeature = newFeature,
  defaultSystemPrompt = defaultSystemPrompt,
)

/** Single source of truth for the home-screen catalog. */
fun builtInTasks(): List<Task> = listOf(
  task(
    id = BuiltInTaskId.LLM_CHAT,
    label = "AI Chat",
    category = Category.LLM,
    iconKey = "chat",
    shortDescription = "Chat with an on-device LLM",
    description = "Free-form streaming chat backed by the locally loaded model.",
  ),
  task(
    id = BuiltInTaskId.LLM_PROMPT_LAB,
    label = "Prompt Lab",
    category = Category.LLM,
    iconKey = "auto_fix",
    shortDescription = "One-shot prompts and templates",
    description = "Iterate on single-turn prompts with templated system messages.",
    defaultSystemPrompt = "You are a concise assistant. Reply with a single, well-formed answer.",
  ),
  task(
    id = BuiltInTaskId.LLM_AGENT_CHAT,
    label = "Agent Chat",
    category = Category.LLM,
    iconKey = "smart_toy",
    shortDescription = "Tool-using assistant",
    description = "Multi-turn chat with structured agent instructions.",
    defaultSystemPrompt = "You are a helpful agent. Think step by step before replying.",
    newFeature = true,
  ),
  task(
    id = BuiltInTaskId.LLM_ASK_IMAGE,
    label = "Ask Image",
    category = Category.EXPERIMENTAL,
    iconKey = "image",
    shortDescription = "Ask questions about a picture",
    description = "Multimodal Q&A. Requires an image-capable model and platform support.",
    experimental = true,
  ),
  task(
    id = BuiltInTaskId.LLM_ASK_AUDIO,
    label = "Ask Audio",
    category = Category.EXPERIMENTAL,
    iconKey = "mic",
    shortDescription = "Ask questions about audio clips",
    description = "Audio Q&A. Requires an audio-capable model and platform support.",
    experimental = true,
  ),
  task(
    id = BuiltInTaskId.LLM_TINY_GARDEN,
    label = "Tiny Garden",
    category = Category.EXPERIMENTAL,
    iconKey = "emoji_nature",
    shortDescription = "Grow a tiny garden with prompts",
    description = "Playful demo task. Coming soon to multiplatform.",
    experimental = true,
  ),
)

/** Tasks grouped by category, preserving the order of [builtInTasks]. */
fun tasksByCategory(): Map<CategoryInfo, List<Task>> =
  builtInTasks().groupBy { it.category }

/** Returns the built-in [Task] for [id], or null if unknown. */
fun findBuiltInTask(id: String): Task? = builtInTasks().firstOrNull { it.id == id }

