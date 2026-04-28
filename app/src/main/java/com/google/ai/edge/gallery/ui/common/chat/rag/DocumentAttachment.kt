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
