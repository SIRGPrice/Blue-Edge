/*
 * Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge
 *
 * Licensed under the Blue Edge Custom License 1.0.
 * You may not use this file except in compliance with that license.
 * GitHub may host, cache, display, and facilitate collaboration on this file
 * as required by the GitHub Terms of Service.
 * See the repository root: BLUE_EDGE_CUSTOM_LICENSE.md
 */
package com.google.ai.edge.gallery.ui.common.chat.rag
import android.net.Uri
enum class AttachmentScope {
  PERSISTENT,
  TEMPORARY,
}
data class DocumentAttachment(
  val uri: Uri,
  val displayName: String,
  val sizeBytes: Long,
  val mimeType: String?,
  val scope: AttachmentScope,
)
data class PermanentDocumentRef(
  val id: String,
  val displayName: String,
  val textLength: Int,
  val addedAtMs: Long,
)
data class PendingRagStaging(
  val documents: List<DocumentAttachment>,
)
