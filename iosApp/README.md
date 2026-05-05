<!--
Copyright 2026 SIRGPrice

This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge

Licensed under the Blue Edge Custom License 1.0.
You may not use this file except in compliance with that license.
GitHub may host, cache, display, and facilitate collaboration on this file
as required by the GitHub Terms of Service.
See the repository root: LICENSE.md
-->
# BlueEdge iOS host app
#
# This directory holds the SwiftUI shell that embeds the shared Kotlin
# Multiplatform / Compose Multiplatform UI produced by the `:shared` Gradle
# module.
#
# ## Project generation (macOS only)
#
# The `.xcodeproj` is **not** committed; generate it with XcodeGen so the
# repository stays Windows/Linux-friendly:
#
# ```bash
# brew install xcodegen cocoapods
# cd iosApp
# xcodegen generate
# pod install                 # once the Podfile is added in Phase 2
# open BlueEdge.xcworkspace
# ```
#
# ## Phase 2 work items (LLM runtime, see `shared/.../runtime/LlmEngine.ios.kt`)
#
# 1. Add `Podfile` with:
#    ```
#    pod 'MediaPipeTasksGenAI'        # LLM Inference, replaces litertlm
#    pod 'MediaPipeTasksGenAIC'
#    pod 'TensorFlowLiteSwift'        # benchmarks / generic TFLite
#    pod 'TensorFlowLiteSwift/CoreML'
#    pod 'AppAuth'                    # HuggingFace OAuth
#    ```
# 2. Drop a Swift bridge (`BlueEdgeLlmBridge.swift`) that wraps
#    `LlmInference` from MediaPipe and exposes a plain `@objc` class.
# 3. Add a cinterop `def` file under `shared/src/iosMain/cinterop/` so the
#    Kotlin `IosMediaPipeLlmEngine` can call the Swift bridge.
#
# ## Running
#
# Build & run the `BlueEdge` scheme on an iPhone Simulator (iOS 16+) or a
# physical device. The `preBuildScripts` block in `project.yml` invokes
# `./gradlew :shared:embedAndSignAppleFrameworkForXcode` to produce the
# `BlueEdgeShared.framework` consumed by the Swift target.

