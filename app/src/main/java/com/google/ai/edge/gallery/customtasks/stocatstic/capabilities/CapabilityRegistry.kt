/*
 * Copyright 2026 SIRGPrice and Blue Edge contributors
 * Part of Blue Edge, a heavily modified app fork based on Google AI Edge Gallery.
 * Upstream project originally published by Google LLC:
 * https://github.com/google-ai-edge/gallery
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

