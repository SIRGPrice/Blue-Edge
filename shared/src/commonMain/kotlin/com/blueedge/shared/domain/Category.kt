/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform mirror of `:app/.../data/Categories.kt`.
 *
 * Drops `@StringRes` (Android-only). Use `labelKey` to look up a localized
 * string per platform; `label` (when non-null) wins over `labelKey`.
 */
package com.blueedge.shared.domain

import kotlinx.serialization.Serializable

@Serializable
data class CategoryInfo(
  val id: String,
  /** Resource key (e.g. "category_llm") resolved per-platform. */
  val labelKey: String? = null,
  /** Literal label. Takes precedence over [labelKey] when non-null. */
  val label: String? = null,
)

object Category {
  val LLM = CategoryInfo(id = "llm", labelKey = "category_llm")
  val CLASSICAL_ML = CategoryInfo(id = "classical_ml", labelKey = "category_llm")
  val EXPERIMENTAL = CategoryInfo(id = "experimental", labelKey = "category_experimental")
}

