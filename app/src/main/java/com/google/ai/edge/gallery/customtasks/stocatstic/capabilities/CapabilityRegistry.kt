/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.capabilities

import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.CapabilityCategory
import javax.inject.Inject
import javax.inject.Singleton

/** Central extensible registry of capabilities. New nodes = new entries here. */
@Singleton
class CapabilityRegistry @Inject constructor(
  capabilities: Set<@JvmSuppressWildcards Capability>,
) {
  private val byId: Map<String, Capability> = capabilities.associateBy { it.id }

  fun get(id: String): Capability? = byId[id]
  fun all(): List<Capability> = byId.values.sortedWith(compareBy({ it.category.ordinal }, { it.label }))
  fun byCategory(): Map<CapabilityCategory, List<Capability>> =
    all().groupBy { it.category }
}

