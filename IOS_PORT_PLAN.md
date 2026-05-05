# Plan de portado a iOS — Blue Edge

Estado: **Fase 1 completada** + **Fase 2 al 80 % (sin Mac)** + **Fase 3 en curso (~94 %)**.

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

### Fase 3 — Migración progresiva a `commonMain` (en curso) ✅
- Dominio multiplataforma `domain/Model.kt` (mirror serializable de
  `:app/.../data/Model.kt`, sin Context/File/Gson).
- Dominio adicional migrado a `shared/commonMain/domain/`:
  `Category.kt`, `Task.kt` + `BuiltInTaskId`, `Config.kt` (todos los
  `Config*` + `convertValueToTargetType` + `createLlmChatConfigs*` +
  `getConfigValueString`), `Consts.kt`. Drops `@StringRes`/`ImageVector`
  /`MutableState`; usa `iconKey`, `labelKey` y `kotlinx-serialization`.
- `domain/ModelStorage.kt` con `actual` para Android (External Files Dir) e
  iOS (Documents/models).
- `chat/ChatViewModel.kt` — view-model multiplataforma con `StateFlow` que
  consume `LlmEngine.generate()` y emite tokens en streaming.
- `ui/chat/ChatScreen.kt` — pantalla Compose MP funcional con burbujas,
  composer, auto-scroll y caret de streaming.
- **Theme multiplataforma** en `shared/commonMain/ui/theme/`
  (`Color.kt`, `Theme.kt` con `BlueEdgeTheme`, `CustomColors`,
  `LocalCustomColors`, `ThemeMode`, `ThemeSettings`). `StatusBarColorController`
  como `expect`/`actual` (Android: `WindowCompat`; iOS: no-op gestionado por SwiftUI).
- **AndroidBridgeRegistry** (`shared/androidMain/.../android/bridges/`) — patrón
  paralelo a `IosBridgeRegistry` para que `:app` inyecte en arranque las
  implementaciones reales de `LlmEngine` y `DownloadManager` (litertlm,
  WorkManager) sin que `:shared` dependa de ellas. Los `actual` de Android
  consultan el registry y caen al stub si nadie ha registrado bridges.
- **`SettingsRepository`** (`shared/commonMain/storage/`) sobre
  `multiplatform-settings`: `actual` Android (SharedPreferences) e iOS
  (NSUserDefaults). Wrapper para primitivos + serialización JSON de objetos
  + accesor tipado `themeMode`.
- **Módulo Koin compartido** (`shared/commonMain/di/SharedModule.kt`)
  exponiendo `LlmEngine`, `DownloadManager`, `OAuthClient`, `ModelStorage`,
  `SecureStorage`, `SettingsRepository`, `ChatViewModel`. `:app` y el host
  iOS llaman `startKoin { modules(sharedModules()) }` en arranque.
- **`:app` consume `:shared`** (`implementation(project(":shared"))`).
  `GalleryApplication.onCreate()` instala `SharedAndroidContext`, registra
  `AppBridges` en `AndroidBridgeRegistry` y arranca Koin con los módulos
  compartidos junto a Hilt — los dos coexisten durante la migración.
  Verificado: `:app:assembleDebug` sigue verde.
- **DownloadManager real (Android)**: `WorkManagerDownloadManager` en
  `:app/.../bridges/` envuelve un `SimpleDownloadWorker` (CoroutineWorker
  genérico con progreso por tiempo) y publica `Flow<DownloadStatus>`
  observando `WorkInfo`. `AppBridges` lo expone vía
  `downloadManagerFactory`; el `actual` Android consulta el registry
  primero y cae al stub si no se ha registrado nada.
- **LlmEngine real (Android)**: `AndroidSharedLlmEngine` en
  `:app/.../bridges/` implementa `com.blueedge.shared.runtime.LlmEngine` y
  adapta `LlmModelDescriptor` → `:app` `Model` sintético (`RuntimeType.LITERT_LM`,
  `localModelFilePathOverride`, configs de tokens/acelerador). `load()` llama
  a `model.runtimeHelper.initialize(...)`; `generate()` convierte callbacks de
  `runInference(...)` en `Flow<LlmEvent>` y `close()` limpia el helper legacy.
  `AppBridges.llmEngineFactory` ya lo registra en `AndroidBridgeRegistry`.
- **Settings importer** one-shot: en `GalleryApplication` se hidrata
  `SettingsRepository.themeMode` desde el proto-DataStore al primer
  arranque (con `KEY_THEME_IMPORTED` como guarda). Segundo importer
  (`KEY_USERPREFS_IMPORTED`) hidrata `tosAccepted`, `gemmaTermsAccepted`,
  `hasRunTinyGarden`, `hasSeenBenchmarkComparisonHelp` y `textInputHistory`.
- **ChatScreen extendido**: botón Stop durante streaming, botón Clear
  conversación, banner de error con Dismiss, panel de quick-prompts cuando
  la conversación está vacía y composer deshabilitado mientras genera.
  `ChatUiState.isGenerating` + `ChatViewModel.clearMessages()`/`dismissError()`.
- **Composables stateless** migrados a `shared/commonMain/ui/common/`:
  `ColorUtils.kt` (con `Task` compartido), `ClickableLink.kt`, `EmptyState.kt`
  (sin `@StringRes` — usa `String`), `FloatingBanner.kt`, `ErrorDialog.kt`,
  `Accordions.kt`, `Constants.kt` (`SMALL_BUTTON_CONTENT_PADDING`).
- **Markdown multiplataforma**: `MarkdownText` como `expect`/`actual`.
  Android usa `compose-richtext` (deps movidas a `shared/androidMain`),
  iOS un fallback `Text` con stripper de Markdown.
- **WebView multiplataforma**: `PlatformWebView(content, modifier, onUrlOpen)`
  con `WebContent.Html` y `WebContent.Url`. Android: `AndroidView` +
  `android.webkit.WebView`. iOS: `UIKitView` + `WKWebView` con
  `WKNavigationDelegate.decidePolicy` para interceptar enlaces.
- **AudioRecorder multiplataforma**: `shared/commonMain/audio/AudioRecorder.kt`
  con `AudioRecordingConfig`, `AudioRecorderState`, `AudioRecorder` y
  `provideAudioRecorder()`. Android usa `MediaRecorder` a `.m4a` temporal
  (AAC/MPEG_4) y devuelve bytes; iOS mantiene stub seguro hasta conectar
  el bridge Swift/AVAudioRecorder. Expuesto por `sharedCoreModule`.
- **AudioPlayer multiplataforma**: `shared/commonMain/audio/AudioPlayer.kt`
  con `AudioPlaybackConfig`, `AudioPlayerState`, `AudioPlayer` y
  `provideAudioPlayer()`. Android usa `AudioTrack` para PCM16 mono y emite
  progreso por `StateFlow`; iOS mantiene stub seguro hasta conectar
  AVAudioEngine/AVAudioPlayerNode. Expuesto por `sharedCoreModule`.
- **CameraPreviewSurface**: `shared/commonMain/ui/camera/CameraPreviewSurface.kt`
  como `expect`/`actual` estable para UI compartida. Android/iOS arrancan y
  paran `CameraController` y renderizan placeholder seguro; el interior se
  puede sustituir por CameraX `PreviewView` / iOS `AVCaptureVideoPreviewLayer`
  sin cambiar callers.
- **ModelManager compartido base**: `ModelStorage` ahora lista archivos de
  primer nivel (`ModelFile`, `listModelFiles()`) con actual Android/iOS.
  `SharedModelManagerScreen` muestra el directorio de modelos y los archivos
  locales; `RootNavigator` lo usa en la ruta Models.
- **Benchmark compartido base**: `SharedBenchmarkScreen` + `BenchmarkSummary`
  en `shared/commonMain/ui/benchmark/`; `RootNavigator` lo usa en la ruta
  Benchmark.
- **Benchmark runner real**: `BenchmarkRunner` (commonMain) consume el
  `LlmEngine` compartido y mide prefill (time-to-first-token), decode y
  output tokens; `BenchmarkViewModel` con `BenchmarkUiState` orquesta
  selección de modelo, prompt y errores. La pantalla lista los modelos
  locales (`.task`/`.tflite`/`.bin`/`.litertlm`) vía `ModelStorage.listModelFiles()`,
  permite seleccionar/refrescar y persiste el último path en
  `SettingsRepository.lastLoadedModelPath` (también escrito por
  `ChatViewModel.loadModel`). Inyectado vía Koin
  (`BenchmarkRunner` single, `BenchmarkViewModel` factory).
- **Selección de modelo end-to-end**: `ModelManagerViewModel` con botón
  "Use" por archivo carga el modelo en `LlmEngine` y persiste el path.
  `ChatViewModel` hidrata el último modelo en `init` automáticamente para
  que el chat arranque listo. Highlight visual del modelo activo +
  Refresh manual.
- **Consent gate**: `ConsentScreen` en `commonMain/ui/consent/` se muestra
  como gate del `RootNavigator` mientras no estén aceptados TOS y Gemma
  Terms (`SettingsRepository.tosAccepted` + `gemmaTermsAccepted`).
- **Import de modelos en iOS**: `ModelImporter` (expect/actual) +
  `BlueEdgeModelImportBridge.swift` con `UIDocumentPickerViewController`
  que copia archivos a `Documents/models` y devuelve los paths.
  `ModelManagerViewModel.importModel()` y botón "Import" en la pantalla
  cuando el platform lo soporta. Android queda como no-op (los usuarios
  pueden depositar archivos directamente en el directorio externo).
- **Wire de `LlmGenerationConfig`** extremo-a-extremo en iOS:
  `LlmBridgeIos.generate()` recibe `temperature/topK/topP/randomSeed` y
  `BlueEdgeLlmBridge.swift` recrea la `LlmInference.Session` por
  generación con esas opciones. `awaitClose { bridge.close() }` libera la
  sesión MediaPipe cuando la coroutine se cancela (botón Stop del chat).
- **Bridges Swift Audio reales**: `BlueEdgeAudioRecorderBridge.swift`
  (`AVAudioRecorder` → m4a/AAC) y `BlueEdgeAudioPlayerBridge.swift`
  (`AVAudioEngine` + `AVAudioPCMBuffer` para PCM16 mono).
  `AudioRecorder.ios.kt` y `AudioPlayer.ios.kt` ahora delegan al bridge a
  través de `IosBridgeRegistry`. Aggregate `BlueEdgeIosBridges` ampliado
  con `audioRecorder`/`audioPlayer` opcionales.
- **Settings screen compartida** (`commonMain/ui/settings/SettingsScreen.kt`)
  con cambio de tema (`AUTO/LIGHT/DARK`), inspección/limpieza del último
  modelo cargado y reset de consent. Ruta `SettingsRoute` añadida al
  `RootNavigator`.
- **HF OAuth config** (`commonMain/auth/HuggingFaceOAuth.kt`) con
  `clientId/authEndpoint/tokenEndpoint/redirectUri/scopes` y
  `defaultConfig()` para inyectar al `OAuthClient` desde la futura UI de
  descarga.
- **Navegación Voyager**: `shared/commonMain/ui/navigation/RootNavigator.kt`
  con `HomeScreen`, `ChatRoute`, `ModelManagerScreen`, `BenchmarkScreen` y
  `BackScaffold`/`PlaceholderBody`. Reemplaza `GalleryNavGraph.kt`.
  Deps añadidas a `libs.versions.toml`: `voyager-navigator/transitions/screenmodel/koin` 1.1.0-beta03.
- **HomeScreen real con catálogo de Tasks**: `BuiltInTasks.kt` (LLM_CHAT,
  LLM_PROMPT_LAB, LLM_AGENT_CHAT + experimentales LLM_ASK_IMAGE/AUDIO/
  TINY_GARDEN), `IconRegistry.iconFor()` mapeando `iconKey` →
  Material Icons Extended, `HomeViewModel` (filtro por categoría + búsqueda
  free-text) y `SharedHomeScreen` (TopAppBar con Models/Benchmark/Settings,
  search field, FilterChips de categoría, grid adaptive de TaskTiles con
  badges New/Experimental). `ChatRoute` ahora es `data class ChatRoute(taskId)`
  que llama a `ChatViewModel.setTaskById()` para aplicar el `defaultSystemPrompt`
  de la tarea elegida. Tasks experimentales muestran snackbar "coming soon".
  Tests `BuiltInTasksTest` + `HomeViewModelTest` verdes.
- `BlueEdgeApp` ahora monta `BlueEdgeTheme { Surface { RootNavigator() } }`.

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
| Origen `:app`                                     | Destino                       | Estado |
|---------------------------------------------------|-------------------------------|--------|
| `data/Tasks.kt`, `Categories.kt`, `Config.kt`, `Consts.kt` | `shared/commonMain/domain/` | ✅ |
| `ui/theme/`                                       | `shared/commonMain/ui/theme/` | ✅ |
| `ui/common/` (stateless: ColorUtils, ClickableLink, EmptyState, FloatingBanner, ErrorDialog, Accordions, MarkdownText, PlatformWebView) | `shared/commonMain/ui/common/` | ✅ |
| `ui/common/` (Audio recorder/playback)           | `AudioRecorder` + `AudioPlayer` common; Android real | ✅ parcial (iOS bridges pendientes) |
| `ui/common/` (Camera preview surface)            | `CameraPreviewSurface` expect/actual placeholder | ✅ parcial (preview native pendiente) |
| `ui/llmchat/LlmChatViewModel`                     | ya cubierto por `ChatViewModel` | ✅ parcial |
| `ui/llmchat/LlmChatScreen`                        | extender `ChatScreen` (Stop/Clear/quick-prompts ✅; imágenes pendiente) | ✅ parcial |
| `ui/modelmanager/`                                | `SharedModelManagerScreen` + `ModelManagerViewModel` (Use button, active highlight, Import via `ModelImporter` expect/actual + Swift `UIDocumentPickerViewController`) | ✅ funcional |
| `ui/benchmark/`                                   | `SharedBenchmarkScreen` + `BenchmarkRunner` + `BenchmarkViewModel` (LlmEngine real, model picker, prompt, persist last model) | ✅ funcional Android (iOS al primer build con MediaPipe) |
| `ui/navigation/GalleryNavGraph`                   | `RootNavigator.kt` (Voyager) | ✅ esqueleto |
| `runtime/LlmModelHelper.kt` y siblings            | `AndroidSharedLlmEngine` registrado en `AndroidBridgeRegistry` | ✅ adaptador básico real |
| `worker/DownloadWorker.kt`                        | `WorkManagerDownloadManager` + `SimpleDownloadWorker` registrado en bridge | ✅ |
| Hilt → Koin                                       | Koin arranca junto a Hilt en `:app` | coexistencia ✅; sustitución gradual pendiente |
| DataStore Proto → multiplatform-settings + Proto  | `SettingsRepository` ✅; importer one-shot tema ✅ + userprefs ✅ (tos, gemmaTos, hasRunTinyGarden, hasSeenBenchHelp, textInputHistory) + `lastLoadedModelPath` ✅ | resto de claves pendiente |

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
