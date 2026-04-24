/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

import android.content.Context
import android.util.Log
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

private const val TAG = "AGDocumentTextExtractor"

/**
 * Turns any supported text-bearing document into plain text + optional metadata.
 *
 * Design goals:
 * - 100% on-device: no network, no cloud.
 * - No payload size limits: reads streams, normalises, and lets the RAG layer chunk.
 * - Text-only pipeline: images/audio are handled elsewhere.
 */
object DocumentTextExtractor {

  /** Supported extensions + their MIME hints. Matches both document picker and manual detection. */
  val SUPPORTED_EXTENSIONS: Set<String> = setOf(
    // Plain text
    "txt", "md", "markdown", "log", "csv", "tsv", "rst", "adoc", "asciidoc",
    // Structured data
    "json", "jsonl", "ndjson", "yaml", "yml", "toml", "ini", "properties", "cfg", "conf", "env",
    // Markup
    "html", "htm", "xml", "xhtml", "svg",
    // Rich text
    "rtf",
    // Source code / scripts
    "kt", "kts", "java", "py", "js", "mjs", "ts", "tsx", "jsx",
    "rb", "rs", "go", "c", "h", "cpp", "hpp", "cc", "cxx",
    "cs", "swift", "sql", "sh", "bash", "zsh", "fish",
    "bat", "cmd", "ps1", "php", "dart", "lua", "r", "scala", "vb", "gradle",
    // Office XML
    "docx", "pptx", "xlsx",
    // OpenDocument
    "odt", "ods", "odp",
    // PDF
    "pdf",
  )

  /**
   * MIME types to hand to the document picker. Uses wildcard groups where possible so that
   * Android picker exposes all matching files.
   */
  val PICKER_MIME_TYPES: Array<String> = arrayOf(
    "text/*",
    "application/json",
    "application/xml",
    "application/rtf",
    "application/pdf",
    "application/x-ndjson",
    "application/x-yaml",
    "application/x-toml",
    "application/x-sh",
    "application/x-kotlin",
    "application/javascript",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.oasis.opendocument.spreadsheet",
    "application/vnd.oasis.opendocument.presentation",
  )

  /**
   * Reads the document and returns the fully extracted plain text.
   *
   * @throws UnsupportedDocumentException if the file's format cannot be transformed into text.
   */
  fun extract(context: Context, document: DocumentAttachment): String {
    val ext = document.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    ensurePdfBoxInitialised(context)

    val resolver = context.contentResolver
    return resolver.openInputStream(document.uri).use { input ->
      requireNotNull(input) { "Cannot open ${document.displayName}" }
      when (ext) {
        "pdf" -> extractPdf(input)
        "docx" -> extractDocx(input)
        "pptx" -> extractPptx(input)
        "xlsx" -> extractXlsx(input)
        "odt", "ods", "odp" -> extractOdf(input)
        "html", "htm", "xhtml" -> stripHtml(readAllText(input))
        "xml", "svg" -> stripXml(readAllText(input))
        "rtf" -> stripRtf(readAllText(input))
        "" -> {
          // Fall back to MIME detection when the file has no extension.
          extractByMime(document.mimeType, input)
        }
        else -> {
          if (ext in SUPPORTED_EXTENSIONS) readAllText(input)
          else extractByMime(document.mimeType, input)
        }
      }
    }.trim()
  }

  /**
   * Compute a stable id for a document based on its display name + size + a sha256 prefix of
   * the stream. Used to deduplicate when the user re-attaches the same doc.
   */
  fun stableId(context: Context, document: DocumentAttachment): String {
    val digest = MessageDigest.getInstance("SHA-256")
    try {
      context.contentResolver.openInputStream(document.uri)?.use { input ->
        val buffer = ByteArray(8 * 1024)
        while (true) {
          val read = input.read(buffer)
          if (read <= 0) break
          digest.update(buffer, 0, read)
        }
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to hash ${document.displayName}; falling back to name+size", t)
    }
    val hashHex =
      digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    val safeName = document.displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    return "${safeName}_${document.sizeBytes}_${hashHex}"
  }

  // -----------------------------------------------------------------------------------------
  // Format-specific extractors
  // -----------------------------------------------------------------------------------------

  private fun extractByMime(mime: String?, input: InputStream): String {
    val m = mime?.lowercase(Locale.ROOT).orEmpty()
    return when {
      m.startsWith("text/") -> readAllText(input)
      m == "application/json" || m == "application/x-ndjson" -> readAllText(input)
      m == "application/xml" -> stripXml(readAllText(input))
      m == "application/rtf" -> stripRtf(readAllText(input))
      else -> throw UnsupportedDocumentException(
        "Unsupported document type. Extractor cannot convert '$mime' to text."
      )
    }
  }

  private fun extractPdf(input: InputStream): String {
    val buffer = StringBuilder()
    PDDocument.load(input).use { doc ->
      val stripper = PDFTextStripper().apply { sortByPosition = true }
      buffer.append(stripper.getText(doc))
    }
    return buffer.toString()
  }

  private fun extractDocx(input: InputStream): String =
    readOoxmlTextNodes(input) { entryName ->
      entryName == "word/document.xml" || entryName.startsWith("word/header") || entryName.startsWith("word/footer")
    }

  private fun extractPptx(input: InputStream): String =
    readOoxmlTextNodes(input) { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }

  private fun extractXlsx(input: InputStream): String {
    // Walk the shared strings table + each worksheet. We keep the output row-major, one cell
    // per line separated by tabs so the LLM can still reason about the structure.
    val shared = mutableListOf<String>()
    val sheets = mutableListOf<Pair<String, ByteArray>>()

    ZipInputStream(input).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        val name = entry.name
        if (!entry.isDirectory) {
          val bytes = zip.readBytes()
          if (name == "xl/sharedStrings.xml") {
            shared.addAll(readSharedStrings(bytes))
          } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
            sheets.add(name to bytes)
          }
        }
        entry = zip.nextEntry
      }
    }

    val out = StringBuilder()
    for ((name, bytes) in sheets.sortedBy { it.first }) {
      out.append("# ").append(name.substringAfterLast('/')).append("\n")
      out.append(readWorksheet(bytes, shared))
      out.append("\n")
    }
    return out.toString()
  }

  private fun extractOdf(input: InputStream): String {
    // All ODF packages keep their textual body in content.xml.
    val out = StringBuilder()
    ZipInputStream(input).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        if (!entry.isDirectory && entry.name == "content.xml") {
          val text = readOdfContent(zip.readBytes())
          out.append(text)
          return@use
        }
        entry = zip.nextEntry
      }
    }
    return out.toString()
  }

  // -----------------------------------------------------------------------------------------
  // Low-level helpers
  // -----------------------------------------------------------------------------------------

  private fun readAllText(input: InputStream): String {
    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
      val sb = StringBuilder()
      val buffer = CharArray(8 * 1024)
      while (true) {
        val read = reader.read(buffer)
        if (read <= 0) break
        sb.append(buffer, 0, read)
      }
      return sb.toString()
    }
  }

  private fun stripHtml(html: String): String {
    val withoutScriptsAndStyles = html
      .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
      .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
      .replace(Regex("(?is)<!--.*?-->"), " ")
    val withoutTags = withoutScriptsAndStyles.replace(Regex("(?is)<[^>]+>"), " ")
    val decoded = decodeHtmlEntities(withoutTags)
    return collapseWhitespace(decoded)
  }

  private fun stripXml(xml: String): String {
    val withoutTags = xml.replace(Regex("(?is)<[^>]+>"), " ")
    return collapseWhitespace(decodeHtmlEntities(withoutTags))
  }

  private fun stripRtf(rtf: String): String {
    // Remove RTF control words (e.g. \par, \b), group delimiters, and hex escapes.
    var s = rtf
    s = s.replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), " ")
    s = s.replace(Regex("\\\\'[0-9a-fA-F]{2}"), " ")
    s = s.replace(Regex("[{}]"), " ")
    return collapseWhitespace(s)
  }

  private fun decodeHtmlEntities(s: String): String = s
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("&#(\\d+);")) { match -> match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }

  private fun collapseWhitespace(s: String): String {
    return s
      .replace(Regex("\\r\\n?"), "\n")
      .replace(Regex("[\\t\\x0B\\f]+"), " ")
      .replace(Regex(" +"), " ")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
  }

  private fun readOoxmlTextNodes(
    input: InputStream,
    acceptEntry: (String) -> Boolean,
  ): String {
    val collected = mutableListOf<String>()
    ZipInputStream(input).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        if (!entry.isDirectory && acceptEntry(entry.name)) {
          val bytes = zip.readBytes()
          collected.add(readAllWTextNodes(bytes))
        }
        entry = zip.nextEntry
      }
    }
    return collected.joinToString("\n\n")
  }

  private fun readAllWTextNodes(bytes: ByteArray): String {
    // Works for docx (w:t, w:p) and pptx (a:t, a:p).
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(bytes.inputStream(), Charsets.UTF_8.name())

    val out = StringBuilder()
    var eventType = parser.eventType
    var inTextNode = false
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          val name = parser.name
          inTextNode = name != null && (name.endsWith(":t") || name == "t")
          // Paragraph / line break markers -> newline.
          if (name != null && (name.endsWith(":p") || name == "p" || name.endsWith(":br") || name == "br")) {
            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
          }
        }
        XmlPullParser.END_TAG -> {
          val name = parser.name
          if (name != null && (name.endsWith(":t") || name == "t")) inTextNode = false
          if (name != null && (name.endsWith(":p") || name == "p")) {
            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
          }
        }
        XmlPullParser.TEXT -> if (inTextNode) out.append(parser.text)
      }
      eventType = parser.next()
    }
    return collapseWhitespace(out.toString())
  }

  private fun readSharedStrings(bytes: ByteArray): List<String> {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(bytes.inputStream(), Charsets.UTF_8.name())

    val strings = mutableListOf<String>()
    val current = StringBuilder()
    var inSi = false
    var inT = false
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "si" -> { inSi = true; current.setLength(0) }
            "t" -> inT = true
          }
        }
        XmlPullParser.TEXT -> if (inSi && inT) current.append(parser.text)
        XmlPullParser.END_TAG -> {
          when (parser.name) {
            "t" -> inT = false
            "si" -> {
              if (inSi) strings.add(current.toString())
              inSi = false
            }
          }
        }
      }
      event = parser.next()
    }
    return strings
  }

  private fun readWorksheet(bytes: ByteArray, shared: List<String>): String {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(bytes.inputStream(), Charsets.UTF_8.name())

    val sb = StringBuilder()
    var cellType: String? = null
    var inValue = false
    val cellValue = StringBuilder()
    var event = parser.eventType
    var firstCellInRow = true
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "row" -> firstCellInRow = true
            "c" -> { cellType = parser.getAttributeValue(null, "t"); cellValue.setLength(0) }
            "v", "t" -> inValue = true
          }
        }
        XmlPullParser.TEXT -> if (inValue) cellValue.append(parser.text)
        XmlPullParser.END_TAG -> {
          when (parser.name) {
            "v", "t" -> inValue = false
            "c" -> {
              val raw = cellValue.toString()
              val resolved = if (cellType == "s") {
                raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
              } else raw
              if (!firstCellInRow) sb.append('\t')
              sb.append(resolved)
              firstCellInRow = false
            }
            "row" -> sb.append('\n')
          }
        }
      }
      event = parser.next()
    }
    return sb.toString()
  }

  private fun readOdfContent(bytes: ByteArray): String {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(bytes.inputStream(), Charsets.UTF_8.name())

    val out = StringBuilder()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          val name = parser.name
          if (name != null && (name == "text:p" || name == "text:h" || name.endsWith(":p") || name.endsWith(":h") || name == "table:table-row")) {
            if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
          }
        }
        XmlPullParser.TEXT -> out.append(parser.text)
      }
      event = parser.next()
    }
    return collapseWhitespace(out.toString())
  }

  // -----------------------------------------------------------------------------------------
  // PdfBox resource loader (must run once per process)
  // -----------------------------------------------------------------------------------------

  @Volatile private var pdfBoxInitialised = false
  private val pdfBoxLock = Any()

  private fun ensurePdfBoxInitialised(context: Context) {
    if (pdfBoxInitialised) return
    synchronized(pdfBoxLock) {
      if (pdfBoxInitialised) return
      try {
        PDFBoxResourceLoader.init(context.applicationContext)
      } catch (t: Throwable) {
        Log.w(TAG, "PdfBox resource loader init failed", t)
      }
      pdfBoxInitialised = true
    }
  }
}

/** Thrown when the extractor cannot produce text for a given document. */
class UnsupportedDocumentException(message: String) : RuntimeException(message)

