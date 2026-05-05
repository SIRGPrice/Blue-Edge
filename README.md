<div align="center">

# Blue Edge

**On-device generative AI playground for Android — with an iOS port in progress.**

[![License: Custom](https://img.shields.io/badge/license-Blue%20Edge%20Custom%201.0-2d6cdf.svg)](LICENSE.md)
[![Platform: Android](https://img.shields.io/badge/platform-Android%2012%2B-3DDC84.svg)](#requirements)
[![Platform: iOS](https://img.shields.io/badge/platform-iOS%2016%2B-000000.svg)](#requirements)
[![Kotlin](https://img.shields.io/badge/kotlin-multiplatform-7F52FF.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)

Blue Edge is a heavily modified fork of
[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery). It reshapes
the app into a local multimodal agent-chat framework and workflow automation
playground.

</div>

---

## ✨ What it does

- **100% on-device agent chat** with models downloaded from Hugging Face.
- **Multimodal prompts** with images, audio transcription, and live voice.
- **Mobile Actions** so the LLM can call local phone tools such as flashlight,
  contacts, calendar, maps, and messages.
- **StoCATstic Assistant**, a reactive animated assistant with notification,
  accessibility, SMS, and call capabilities.
- **Agent Chat + RAG** with persistent documents, chunking, local search, and
  retrieval-augmented generation.
- **Built-in benchmarks** for tokens per second, latency, and memory by
  accelerator (CPU / GPU / NPU / AICore).
- **iOS port in progress** through a native SwiftUI host app that shares the
  engine through Kotlin Multiplatform.

## 🚀 Get started on Android

Download the APK from the
[latest release](https://github.com/SIRGPrice/Blue-Edge/releases/latest), copy it
to your phone, and open it to install.

### Requirements

- **Android 12+**
- 6 GB of RAM available for the app.
- 6 GB of free storage.
- iOS 16+ for the experimental iOS host app.

## 🔐 Privacy

- **No telemetry**
- **100% local inference**
- **No internet access unless you explicitly download models**

## 💙 Sponsoring

If Blue Edge is useful to you, consider supporting ongoing development through
[GitHub Sponsors](https://github.com/sponsors/SIRGPrice). Sponsorship helps fund
testing devices, model experimentation, and maintenance.

## 📜 License and attributions

- Original Google AI Edge Gallery code: **Apache License 2.0**.
- Blue Edge-marked modifications: [Blue Edge Custom License 1.0](LICENSE.md).
- Project notices: [NOTICE](NOTICE).
- StoCATstic visual asset credits:
  [`app/src/main/assets/stocatstic/CREDITS.txt`](app/src/main/assets/stocatstic/CREDITS.txt).

Only files explicitly marked as Blue Edge-owned are governed by
[LICENSE.md](LICENSE.md). Files that retain upstream or third-party notices keep
their original terms.

---

<sub>Made by engineers 🩵</sub>

