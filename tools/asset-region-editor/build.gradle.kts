/*
 * Blue Edge — Asset Region Editor (desktop dev tool)
 *
 * Official authoring tool for the `tools/asset-regions/asset_regions.json` catalogue that
 * feeds `AssetCatalogs.generated.kt` in the Android app. Kotlin + Swing with the FlatLaf dark
 * look-and-feel for a modern, professional appearance.
 *
 * Run with:  ./gradlew :tools:asset-region-editor:run
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
