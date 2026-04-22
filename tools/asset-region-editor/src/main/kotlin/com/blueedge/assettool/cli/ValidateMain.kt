/*
 * Headless validator for tools/asset-regions/asset_regions.json.
 *
 * Exit codes:
 *   0 → no errors (warnings printed to stdout, if any).
 *   1 → at least one error. Each issue printed to stderr.
 *
 * Wired to the Gradle task:  ./gradlew :tools:asset-region-editor:validateRegions
 */
package com.blueedge.assettool.cli

import com.blueedge.assettool.io.AssetRegionsIo
import com.blueedge.assettool.io.RepoPaths
import com.blueedge.assettool.io.ValidationIssue
import kotlin.system.exitProcess

fun main() {
  val file = AssetRegionsIo.load()
  val issues = AssetRegionsIo.validateAll(file)
  val errors = issues.count { it.severity == ValidationIssue.Severity.ERROR }
  val warnings = issues.count { it.severity == ValidationIssue.Severity.WARNING }

  println("Validating ${RepoPaths.regionsJson.absolutePath}")
  println("Regions: ${file.regions.size}  •  Errors: $errors  •  Warnings: $warnings")

  for (issue in issues) {
    val out = if (issue.severity == ValidationIssue.Severity.ERROR) System.err else System.out
    out.println(issue.toString())
  }
  if (errors > 0) exitProcess(1)
  println("OK")
}

