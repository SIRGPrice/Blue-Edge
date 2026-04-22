package com.blueedge.assettool.io

import com.blueedge.assettool.model.AssetRegionsFile
import com.blueedge.assettool.model.Category
import com.blueedge.assettool.model.RegionEntry
import kotlinx.serialization.json.Json

data class ValidationIssue(val severity: Severity, val message: String, val entry: RegionEntry? = null) {
  enum class Severity { ERROR, WARNING }
  override fun toString(): String = "[${severity.name}] $message"
}

object AssetRegionsIo {

  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  private val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  fun load(): AssetRegionsFile {
    val f = RepoPaths.regionsJson
    if (!f.isFile) return AssetRegionsFile()
    return json.decodeFromString(AssetRegionsFile.serializer(), f.readText(Charsets.UTF_8)).sanitized()
  }

  fun save(file: AssetRegionsFile) {
    val sorted = file.sanitized().copy(regions = sortRegions(file.sanitized().regions))
    val out = RepoPaths.regionsJson
    out.parentFile.mkdirs()
    out.writeText(json.encodeToString(AssetRegionsFile.serializer(), sorted), Charsets.UTF_8)
  }

  private fun AssetRegionsFile.sanitized(): AssetRegionsFile = copy(
    regions = regions.map { region ->
      if (region.category == Category.TASK_SPRITE) region else region.copy(assetType = null)
    }
  )

  private fun sortRegions(regions: List<RegionEntry>): List<RegionEntry> =
    regions.sortedWith(
      compareBy(
        { it.category.ordinal },
        { it.assetPath },
        { it.row },
        { it.col },
        { it.id },
      )
    )

  /**
   * Runs a full validation pass and returns every issue found.
   *
   *  - Duplicate `(category, id)` pairs are errors.
   *  - Missing `id` / `label` are errors.
   *  - Negative col/row or span < 1 are errors.
   *  - `category = CHARACTER` without `characterId` or `characterSlot` is an error (the
   *    Kotlin generator groups by those fields).
   *  - Missing asset file on disk is an error.
   */
  fun validateAll(file: AssetRegionsFile): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    val seen = mutableSetOf<Pair<Category, String>>()
    for (r in file.sanitized().regions) {
      val key = r.category to r.id
      if (!seen.add(key)) {
        issues += ValidationIssue(ValidationIssue.Severity.ERROR,
          "Duplicate id '${r.id}' in category ${r.category.name.lowercase()}", r)
      }
      if (r.id.isBlank()) issues += ValidationIssue(ValidationIssue.Severity.ERROR,
        "Empty id in region on ${r.assetPath}", r)
      if (r.label.isBlank()) issues += ValidationIssue(ValidationIssue.Severity.ERROR,
        "Empty label for '${r.id}'", r)
      if (r.col < 0 || r.row < 0) issues += ValidationIssue(ValidationIssue.Severity.ERROR,
        "Negative col/row for '${r.id}' (${r.col},${r.row})", r)
      if (r.colSpan < 1 || r.rowSpan < 1) issues += ValidationIssue(ValidationIssue.Severity.ERROR,
        "Invalid span for '${r.id}' (${r.colSpan}×${r.rowSpan})", r)
      if (r.category == Category.CHARACTER) {
        if (r.characterId.isNullOrBlank()) issues += ValidationIssue(
          ValidationIssue.Severity.ERROR,
          "Character region '${r.id}' is missing characterId", r)
        if (r.characterSlot == null) issues += ValidationIssue(
          ValidationIssue.Severity.ERROR,
          "Character region '${r.id}' is missing characterSlot", r)
      }
      val asset = RepoPaths.resolveAsset(r.assetPath)
      if (!asset.isFile) issues += ValidationIssue(ValidationIssue.Severity.ERROR,
        "Asset not found on disk: ${r.assetPath}", r)
    }
    file.regions.filter { it.category != Category.TASK_SPRITE && it.assetType != null }.forEach { r ->
      issues += ValidationIssue(
        ValidationIssue.Severity.WARNING,
        "assetType en '${r.id}' se ignorará porque solo se admite en task_sprite",
        r,
      )
    }
    return issues
  }

  /** Backwards-compatible single-message validator (returns null on OK, or first ERROR). */
  fun validate(file: AssetRegionsFile): String? =
    validateAll(file).firstOrNull { it.severity == ValidationIssue.Severity.ERROR }?.message
}
