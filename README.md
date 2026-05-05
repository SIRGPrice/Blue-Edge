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

- **Chat agéntico 100 % on-device** con modelos descargados desde
  Hugging Face.
- **Multimodal**: prompts con imagen, audio (ASR) y voz en directo.
- **Mobile Actions**: el LLM puede invocar acciones del teléfono (linterna,
  contactos, calendario, mapa, mensajes…) mediante *tool calling* local.
- **StoCATstic Assistant**: asistente reactivo con escena animada, capacidades
  de notificación, accesibilidad, SMS y llamadas.
- **Agent Chat & RAG**: documentos permanentes, chunking, búsqueda y
  *retrieval augmented generation* totalmente local.
- **Benchmarks integrados** de tokens/seg, latencia y memoria por acelerador
  (CPU / GPU / NPU / AICore).
- **iOS port (en curso)**: app SwiftUI nativa que comparte motor con Android
  vía un módulo Kotlin Multiplatform.

## 🚀 Empezar (Android)

-Descarga el archivo apk en el móvil y ábrelo.

### Requisitos

- **Android 12+**
- 6 GB de RAM disponibles dedicados solo para la app.
- 6 GB de espacio libre en el almacenamiento.

## 🔐 Privacidad

- **Cero telemetría**
- **Inferencia 100% local**
- **No accede a internet**

## 📜 Licencia y atribuciones

- Código original de Google AI Edge Gallery: **Apache License 2.0**
  (©Google LLC). Ver [`LICENSE`](LICENSE) y [`NOTICE`](NOTICE).
- Modificaciones de Blue Edge: **Blue Edge Custom License 1.0**.
  Ver [`BLUE_EDGE_CUSTOM_LICENSE.md`](BLUE_EDGE_CUSTOM_LICENSE.md).
- Assets visuales del módulo *StoCATstic*: ver
  [`app/src/main/assets/stocatstic/CREDITS.txt`](app/src/main/assets/stocatstic/CREDITS.txt).

---

<sub>Hecho por Ingenieros 🩵</sub>

