/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Shared Kotlin Multiplatform module that hosts the cross-platform business
 * logic and Compose Multiplatform UI for BlueEdge (Android + iOS).
 *
 * Phase 1 only sets up the build, expect/actual platform abstractions and
 * the iOS framework target. Subsequent phases will progressively migrate
 * code from `:app` (Android-only) into `commonMain`.
 */
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  id("com.android.library")
}

// rootProject.name is "Blue Edge" (with a space), which would yield an
// invalid Kotlin package for the auto-generated Compose Resources class.
// Pin a clean package explicitly.
compose.resources {
  packageOfResClass = "com.blueedge.shared.resources"
  generateResClass = always
}

kotlin {
  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
    }
  }

  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64(),
  ).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "BlueEdgeShared"
      isStatic = true
      // Marketing name used by SwiftUI host.
      binaryOption("bundleId", "com.blueedge.shared")
    }
  }

  applyDefaultHierarchyTemplate()

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.materialIconsExtended)
      implementation(compose.ui)
      implementation(compose.components.resources)

      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.datetime)
      implementation(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.multiplatform.settings)
      implementation(libs.multiplatform.settings.coroutines)
      implementation(libs.ktor.core)
      implementation(libs.voyager.navigator)
      implementation(libs.voyager.transitions)
      implementation(libs.voyager.screenmodel)
      implementation(libs.voyager.koin)
    }

    androidMain.dependencies {
      implementation(libs.koin.android)
      implementation(libs.ktor.okhttp)
      implementation(libs.androidx.security.crypto)
      // Android-only Markdown rendering used by the shared `MarkdownText`
      // expect/actual. iOS uses a `Text` fallback for now.
      implementation(libs.commonmark)
      implementation(libs.richtext)
      // Bridge to existing Android-only LLM runtime (litertlm, MLKit GenAI,
      // TFLite, AICore). These remain in :app for now and are wired to the
      // shared `LlmEngine` interface via `actual` providers.
    }

    iosMain.dependencies {
      implementation(libs.ktor.darwin)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

android {
  namespace = "com.blueedge.shared"
  compileSdk = 35
  defaultConfig {
    minSdk = 31
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}
