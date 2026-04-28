# Plan de portado a iOS — Blue Edge

Estado: **Fase 1 completada** + **Fase 2 al 80 % (sin Mac)** + **Fase 3 iniciada**.

Estrategia elegida: **Kotlin Multiplatform + Compose Multiplatform** (Opción A).
Reutilización estimada de código: 70–85 %.

**Invariante crítica garantizada y verificada localmente:**
> `./gradlew :app:assembleDebug :shared:assembleDebug` → BUILD SUCCESSFUL
> en cualquier estado del desarrollo iOS. Lo refuerza el workflow
> `.github/workflows/ci.yml` (job `android` bloquea PRs; jobs `ios-*` son
> best-effort con `continue-on-error: true`).

---

## Lo entregado hasta ahora

### Fase 1 — Scaffolding KMP+CMP ✅
- Módulo `:shared` con targets `androidTarget`, `iosX64`, `iosArm64`,
  `iosSimulatorArm64`. Framework iOS estático: `BlueEdgeShared.framework`.
- Plugins KMP/CMP en `gradle/libs.versions.toml` y `build.gradle.kts` raíz.
- Compose Multiplatform 1.7.3 + Material 3 + Material Icons Extended.
- Dependencias multiplataforma maduras: Koin 4.0, multiplatform-settings 1.2,
  Ktor 2.3 (OkHttp/Darwin), kotlinx-coroutines, kotlinx-datetime,
  kotlinx-serialization.
- `compose.resources.packageOfResClass` fijado a `com.blueedge.shared.resources`
  para sortear el espacio en `rootProject.name = "Blue Edge"`.

### Fase 2 — Bridges nativos iOS (sin Mac) ✅
- **Swift bridges** (`iosApp/BlueEdge/Bridges/`):
  - `BlueEdgeLlmBridge.swift` — wrapper de **MediaPipe Tasks GenAI**
    (`LlmInference` + `LlmInference.Session`) con streaming de tokens.
  - `BlueEdgeAuthBridge.swift` — wrapper de **AppAuth-iOS** (HuggingFace OAuth).
  - `BlueEdgeDownloadBridge.swift` — `URLSession` con
    `URLSessionConfiguration.background(withIdentifier:)` para descargas
    que sobreviven a la suspensión.
  - `BridgeAdapters.swift` — adaptadores Swift que conforman los protocolos
    Kotlin (`BlueEdgeSharedLlmBridgeIos`, `…AuthBridgeIos`, `…DownloadBridgeIos`)
    exportados por el framework KMP.
- **Podfile** con `MediaPipeTasksGenAI`, `MediaPipeTasksGenAIC`,
  `TensorFlowLiteSwift`+`/CoreML`+`/Metal`, `AppAuth`.
- **Pipeline Kotlin/Native sin cocoapods plugin**: las dependencias nativas
  iOS las resuelve Xcode (no Gradle), de modo que la build Android no
  depende de tener CocoaPods instalado.
- `BlueEdgeRoot.start(bridges:)` recibe los bridges al lanzar la app y los
  expone a `actual` providers vía `IosBridgeRegistry`.
- Implementaciones reales (no stubs) en iOS para:
  `LlmEngine`, `OAuthClient`, `DownloadManager`, `SecureStorage` (Keychain),
  `ModelStorage` (Documents/models), `Platform`.

### Fase 3 — Migración progresiva a `commonMain` (iniciada) ✅
- Dominio multiplataforma `domain/Model.kt` (mirror serializable de
  `:app/.../data/Model.kt`, sin Context/File/Gson).
- `domain/ModelStorage.kt` con `actual` para Android (External Files Dir) e
  iOS (Documents/models).
- `chat/ChatViewModel.kt` — view-model multiplataforma con `StateFlow` que
  consume `LlmEngine.generate()` y emite tokens en streaming.
- `ui/chat/ChatScreen.kt` — pantalla Compose MP funcional con burbujas,
  composer, auto-scroll y caret de streaming.
- `BlueEdgeApp` ahora monta el chat real.

### CI ✅
- `.github/workflows/ci.yml`:
  - `android` — bloquea: `:app:assembleDebug` + `:shared:assembleDebug`
    en `ubuntu-latest`.
  - `ios-shared` y `ios-app` — best-effort en `macos-14`,
    `continue-on-error: true`.

---

## Lo que queda (requiere Mac o aún no migrado)

### Trabajo bloqueado por falta de Mac
1. **Generar `.xcodeproj`** con `xcodegen generate` (XcodeGen no está en
   Windows). El `iosApp/project.yml` ya está listo.
2. **`pod install`** para descargar MediaPipe/TFLite/AppAuth: requiere
   CocoaPods (Ruby), que solo soportamos en macOS.
3. **`xcodebuild`** para producir el `.app`/`.ipa`. CI ya está cableado.
4. Verificar nombres exportados de Kotlin/Native al header ObjC (p. ej.
   `BlueEdgeSharedLlmBridgeIos` vs `LlmBridgeIos`); puede requerir un ajuste
   menor en `BridgeAdapters.swift` que solo se ve al primer build en macOS.

### Trabajo Fase 3+ pendiente (todo en Windows)
| Origen `:app`                                     | Destino                       |
|---------------------------------------------------|-------------------------------|
| `data/Tasks.kt`, `Categories.kt`, `Config.kt`     | `shared/commonMain/domain/`   |
| `ui/theme/`                                       | `shared/commonMain/ui/theme/` |
| `ui/common/`                                      | `shared/commonMain/ui/common/`|
| `ui/llmchat/LlmChatViewModel`                     | ya cubierto por `ChatViewModel` |
| `ui/llmchat/LlmChatScreen`                        | extender `ChatScreen` (imágenes, prompts) |
| `ui/modelmanager/`                                | `shared/commonMain/ui/modelmanager/` |
| `ui/benchmark/`                                   | `shared/commonMain/ui/benchmark/`    |
| `ui/navigation/GalleryNavGraph`                   | reemplazar por **voyager** o **decompose** |
| `runtime/LlmModelHelper.kt` y siblings            | `shared/androidMain/runtime/` (real `actual` LlmEngine) |
| `worker/DownloadWorker.kt`                        | `shared/androidMain/download/` (real `actual` DownloadManager) |
| Hilt → Koin                                       | módulos en `commonMain/di/`   |
| DataStore Proto → multiplatform-settings + Proto  | `shared/commonMain/storage/`  |

---

## Decisiones de runtime de inferencia

| Android original                  | iOS sustituto                              | Madurez  |
|-----------------------------------|--------------------------------------------|----------|
| `litertlm` 0.10                   | **MediaPipe Tasks GenAI iOS** (oficial Google) | Estable |
| `play-services-tflite-*`          | **TensorFlowLiteSwift** + delegado **CoreML/Metal** | Estable |
| `mlkit-genai-prompt` beta         | MediaPipe LLM Inference (mismo runtime)    | Estable  |
| AICore / Gemini Nano              | **No disponible** → flag `supportsAICore=false` | n/a    |
| `net.openid:appauth`              | **AppAuth-iOS** (mismo proyecto OpenID)    | Estable  |
| `WorkManager`                     | `URLSession.background` + `BGTaskScheduler`| Estable  |
| `CameraX`                         | `AVCaptureSession`                         | Estable  |
| `androidx.security.crypto`        | **Keychain Services** (ya implementado)    | Estable  |
| `pdfbox-android`                  | `PDFKit`                                   | Estable  |
| `androidx.exifinterface`          | `ImageIO`                                  | Estable  |

