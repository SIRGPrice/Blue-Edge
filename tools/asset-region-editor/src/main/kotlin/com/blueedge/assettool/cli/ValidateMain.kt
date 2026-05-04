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

