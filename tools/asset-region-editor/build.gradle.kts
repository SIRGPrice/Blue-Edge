/*
 * Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge
 *
 * Licensed under the Blue Edge Custom License 1.0.
 * You may not use this file except in compliance with that license.
 * GitHub may host, cache, display, and facilitate collaboration on this file
 * as required by the GitHub Terms of Service.
 * See the repository root: LICENSE.md
 */
plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  application
}


// Use the JVM that runs Gradle (JDK 17+ assumed, same as the Android app build).
// Avoids a separate toolchain download.

dependencies {
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.flatlaf)
  implementation(libs.flatlaf.extras)
}

application {
  mainClass.set("com.blueedge.assettool.MainKt")
  applicationName = "BlueEdgeAssetRegionEditor"
}

tasks.named<JavaExec>("run") {
  // The tool resolves repo-relative paths from the working directory; make sure it's the repo root.
  workingDir = rootProject.projectDir
  standardInput = System.`in`
  jvmArgs = listOf("-Dapple.awt.application.name=Blue Edge Asset Region Editor")
}

/**
 * Headless validation of the JSON catalogue. Intended to be wired to CI:
 *    ./gradlew :tools:asset-region-editor:validateRegions
 * Fails the build if duplicate ids or missing asset files are detected.
 */
tasks.register<JavaExec>("validateRegions") {
  group = "verification"
  description = "Validate tools/asset-regions/asset_regions.json (ids, required fields, assets on disk)."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.blueedge.assettool.cli.ValidateMainKt")
  workingDir = rootProject.projectDir
}
