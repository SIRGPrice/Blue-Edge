<div align="center">

# Blue Edge

**On-device generative AI playground for Android — and now iOS.**

[![License: Custom](https://img.shields.io/badge/license-Blue%20Edge%20Custom%201.0-2d6cdf.svg)](BLUE_EDGE_CUSTOM_LICENSE.md)
[![Platform: Android](https://img.shields.io/badge/platform-Android%2012%2B-3DDC84.svg)](#requisitos)
[![Platform: iOS](https://img.shields.io/badge/platform-iOS%2016%2B-000000.svg)](#requisitos)
[![Kotlin](https://img.shields.io/badge/kotlin-multiplatform-7F52FF.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)

Blue Edge es un fork profundamente modificado de
[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) centrado en
ejecutar **modelos de lenguaje y multimodales en el dispositivo**, sin enviar
datos a servidores remotos.

</div>

---

## ✨ Qué hace

- **Chat LLM 100 % on-device** con modelos importados o descargados desde
  Hugging Face / AICore.
- **Multimodal**: prompts con imagen, audio (ASR) y voz en directo.
- **Mobile Actions**: el LLM puede invocar acciones del teléfono (linterna,
  contactos, calendario, mapa, mensajes…) mediante *tool calling* local.
- **StoCATstic Assistant**: asistente reactivo con escena animada, capacidades
  de notificación, accesibilidad, SMS y llamadas.
- **Agent Chat & RAG**: documentos permanentes, chunking, búsqueda BM25 y
  *retrieval augmented generation* totalmente local.
- **Benchmarks integrados** de tokens/seg, latencia y memoria por acelerador
  (CPU / GPU / NPU / AICore).
- **iOS port (en curso)**: app SwiftUI nativa que comparte motor con Android
  vía un módulo Kotlin Multiplatform.

## 🧱 Arquitectura

```
BlueEdge/
├── app/                       # App Android (Jetpack Compose, Hilt, Koin, Compose Nav)
│   └── src/main/java/com/google/ai/edge/gallery/...
├── shared/                    # Kotlin Multiplatform: dominio, runtime, OAuth, RAG
│   └── src/{commonMain,androidMain,iosMain}/kotlin/com/blueedge/shared/...
├── iosApp/                    # Host SwiftUI + bridges a la lib KMP
│   ├── BlueEdge/
│   └── project.yml            # XcodeGen spec (no se versiona el .xcodeproj)
├── tools/asset-region-editor/ # Herramienta JVM/Swing para etiquetar tilesets
└── .github/workflows/         # CI Android + iOS Simulator + iOS firmado
```

| Capa            | Stack |
|-----------------|------|
| UI Android      | Jetpack Compose 1.x · Material 3 · Compose Navigation |
| UI iOS          | SwiftUI · UIKit (bridges) · AppAuth-iOS |
| Lógica común    | Kotlin Multiplatform (Android + iosArm64 + iosSimulatorArm64) |
| LLM runtime     | LiteRT-LM · TFLite (CPU/GPU) · ML Kit GenAI · Google AICore |
| RAG             | BM25 · PDFBox · text extractors propios |
| Persistencia    | DataStore (Proto) · WorkManager para descargas |
| OAuth           | net.openid.appauth (Android) · AppAuth-iOS (iOS) |
| Build           | Gradle 8.10 (KTS) · AGP · KSP · Hilt |

## 🚀 Empezar (Android)

### Requisitos

- **Android Studio Ladybug** o más reciente.
- **JDK 21** (incluido en Android Studio como JBR).
- **Android SDK 35** (compileSdk) y un dispositivo o emulador con **Android 12+**
  (minSdk 31).
- 4 GB de RAM disponibles para Gradle (`org.gradle.jvmargs=-Xmx2048m`).

### Build local

```powershell
git clone https://github.com/SIRGPrice/Blue-Edge.git
cd Blue-Edge

# 1. Crea local.properties con la ruta a tu Android SDK.
"sdk.dir=$env:LOCALAPPDATA\Android\Sdk" | Out-File -Encoding ascii local.properties

# 2. Compila el APK debug.
./gradlew :app:assembleDebug

# 3. El APK queda en:
#    app/build/outputs/apk/debug/app-debug.apk
```

> En macOS/Linux: `echo "sdk.dir=$ANDROID_HOME" > local.properties && ./gradlew :app:assembleDebug`.

### Instalar en un dispositivo

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

O abre el proyecto con Android Studio y pulsa **Run ▶**.

### Configurar OAuth de Hugging Face (opcional)

Para descargar modelos privados o con licencia (Gemma, Llama…) hace falta
sustituir el placeholder en [`app/build.gradle.kts`](app/build.gradle.kts):

```kotlin
manifestPlaceholders["appAuthRedirectScheme"] = "tu-redirect-scheme"
```

…y registrar ese mismo *scheme* en tu app de Hugging Face.

## 🍎 iOS

El target iOS no requiere Mac para empezar: el workflow
[`.github/workflows/ci.yml`](.github/workflows/ci.yml) construye en runners
`macos-15`. Detalles en [`BUILD_IOS_FROM_WINDOWS.md`](BUILD_IOS_FROM_WINDOWS.md)
y [`IOS_PORT_PLAN.md`](IOS_PORT_PLAN.md).

Para construir localmente en macOS:

```bash
brew install xcodegen
cd iosApp && xcodegen generate
open BlueEdge.xcodeproj
```

## 🧪 Tests

```powershell
./gradlew :shared:allTests          # Tests KMP (commonTest)
./gradlew :app:testDebugUnitTest    # Tests Android unit
```

## 🛠️ Herramientas

- [`tools/asset-region-editor/`](tools/asset-region-editor/) — editor Swing
  para mapear regiones de tilesets/sprites a categorías; genera
  [`tools/asset-regions/asset_regions.json`](tools/asset-regions/asset_regions.json)
  consumido por el módulo *StoCATstic*.

```powershell
./gradlew :tools:asset-region-editor:run
```

## 🔐 Privacidad

- **Cero telemetría.** Toda la inferencia es local salvo descargas explícitas
  de modelos.
- Los tokens OAuth se almacenan cifrados con
  [`androidx.security.crypto`](https://developer.android.com/jetpack/androidx/releases/security)
  (Android Keystore).
- Logs y `tombstone.txt` están explícitamente listados en
  [`.gitignore`](.gitignore) y nunca deben subirse.

## 🤝 Contribuir

Issues y PRs bienvenidos. Antes de enviar cambios:

1. Ejecuta `./gradlew :app:assembleDebug :shared:allTests`.
2. Aplica los headers de licencia con
   `pwsh ./scripts/update_attribution_headers.ps1`.
3. Revisa el [CI](.github/workflows/ci.yml) en verde.

## 📜 Licencia y atribuciones

- Código original de Google AI Edge Gallery: **Apache License 2.0**
  (©Google LLC). Ver [`LICENSE`](LICENSE) y [`NOTICE`](NOTICE).
- Modificaciones de Blue Edge: **Blue Edge Custom License 1.0**.
  Ver [`BLUE_EDGE_CUSTOM_LICENSE.md`](BLUE_EDGE_CUSTOM_LICENSE.md).
- Assets visuales del módulo *StoCATstic*: ver
  [`app/src/main/assets/stocatstic/CREDITS.txt`](app/src/main/assets/stocatstic/CREDITS.txt).

---

<sub>Hecho con ☕ y CUDA-libre. Si Blue Edge te resulta útil, deja una ⭐ en
GitHub.</sub>

