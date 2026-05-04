# Construir y distribuir la app iOS desde Windows

No se puede compilar Swift/Xcode en Windows: Apple **exige macOS**. Hay que
delegar la build a un Mac, pero **no necesitas tener uno físico**. Estas son
las rutas viables, ordenadas de la más cómoda a la menos.

---

## Vía 1 — GitHub Actions (gratis, ya cableada) ⭐

El workflow `.github/workflows/ci.yml` ya tiene tres jobs iOS que se ejecutan
en runners `macos-14` de GitHub. Tú no tocas nada de macOS.

### 1.1 — Subir el repo a GitHub
Desde PowerShell en la raíz del proyecto:

```powershell
cd <ruta-local-al-repo>
git init
git add .
git commit -m "BlueEdge iOS port scaffolding"
# crea el repo en https://github.com/new (privado o público)
git remote add origin https://github.com/<TU-USER>/BlueEdge.git
git branch -M main
git push -u origin main
```

### 1.2 — Descargar el `.app` para el Simulador iOS
Cada `git push` ejecuta el job `ios-app`. Cuando termina:

1. Ve a `github.com/<TU-USER>/BlueEdge/actions`.
2. Abre el último run.
3. Sección **Artifacts** → `BlueEdge-iOS-Simulator-app.zip`.
4. Descárgalo en Windows.

Para **probarlo en el Simulador iOS** necesitas un Simulador, que sólo corre
en Mac. Si no tienes Mac, salta directamente a 1.3 (TestFlight) para iPhone
real.

### 1.3 — Generar `.ipa` firmada y subirla a TestFlight
TestFlight es la forma estándar de instalar tu propia app en un iPhone real
sin necesidad de Mac local. Requisitos previos:

- **Cuenta Apple Developer Program** ($99/año) — única cosa que cuesta dinero.
  Se contrata 100 % desde Windows en https://developer.apple.com/programs/.

Después, una sola vez, prepara los secretos en GitHub:

| Secret de GitHub                       | Cómo obtenerlo                                     |
|----------------------------------------|----------------------------------------------------|
| `APPLE_TEAM_ID`                        | https://developer.apple.com/account → Membership   |
| `IOS_DIST_CERT_P12_BASE64`             | Generar Distribution Cert en App Store Connect, exportar como `.p12`, codificar en base64 (`certutil -encode cert.p12 cert.b64` en Windows) |
| `IOS_DIST_CERT_PASSWORD`               | La contraseña del .p12                             |
| `IOS_PROVISIONING_PROFILE_BASE64`      | App Store Connect → Provisioning Profile → descargar y base64-encode |
| `IOS_KEYCHAIN_PASSWORD`                | Inventa una contraseña fuerte                      |
| `APPSTORE_API_KEY_ID`                  | App Store Connect → Users & Access → Keys          |
| `APPSTORE_API_ISSUER_ID`               | Misma página, “Issuer ID”                          |
| `APPSTORE_API_KEY_BASE64`              | Descarga el `AuthKey_*.p8` y base64-encode         |

Cuando los tengas, en GitHub:

1. **Settings → Secrets and variables → Actions → New repository secret**
   y pega cada uno.
2. Ve a **Actions → BlueEdge CI → Run workflow** (botón “Run workflow”).
   Esto dispara el job `ios-archive`.
3. Cuando termina, descarga `BlueEdge-iOS-ipa.zip` y/o (si configuraste la
   API key) la app aparecerá automáticamente en **TestFlight** dentro de
   App Store Connect.
4. Invítate a ti mismo como tester en App Store Connect → TestFlight →
   Internal Testing.
5. Instala la app **TestFlight** en tu iPhone con tu Apple ID y verás
   BlueEdge listo para instalar.

> **Truco para los certificados sin Mac**: si no quieres lidiar con
> CertificateSigningRequest manualmente, usa **fastlane match** en el
> workflow (https://docs.fastlane.tools/actions/match/) o deja que
> **Codemagic** (Vía 2) gestione todo automáticamente.

---

## Vía 2 — Codemagic (gratis 500 min/mes, lo más fácil)

https://codemagic.io – conectas el repo de GitHub, eliges "iOS" y le das tu
Apple ID. Codemagic genera certificados/profiles automáticamente y publica
en TestFlight con un clic.

Buena alternativa a la Vía 1.3 si los secretos manuales se te atragantan.

---

## Vía 3 — Xcode Cloud

Incluido con la cuenta Apple Developer. Necesitas conectarte una vez desde
Xcode (en macOS) para configurarlo. Si tienes acceso puntual a un Mac
prestado durante 30 minutos basta para dejarlo armado para siempre.

---

## Vía 4 — Mac en la nube por horas (~$1–2/h)

Cuando quieras debuggear interactivamente o tocar Xcode tú mismo:

| Servicio          | Precio aprox.      | Notas                                |
|-------------------|--------------------|--------------------------------------|
| **MacInCloud**    | ~$1/h pago por uso | Acceso vía VNC/RDP desde Windows     |
| **MacStadium**    | desde $59/mes      | Mac mini M2 dedicado                 |
| **AWS EC2 Mac**   | $1.08/h, mín 24 h  | macOS bare-metal                     |
| **Scaleway Mac mini M1** | ~€0.11/h    | Buena relación calidad/precio en EU  |

Te conectas con Microsoft Remote Desktop o RealVNC desde Windows, abres Xcode,
ejecutas:

```bash
brew install xcodegen cocoapods
git clone https://github.com/<TU-USER>/BlueEdge.git
cd BlueEdge/iosApp
xcodegen generate
pod install
open BlueEdge.xcworkspace
```

Y compilas / corres en el Simulador o lanzas a un dispositivo.

---

## Vía 5 — Mac VM en Windows ❌

VMware/VirtualBox + macOS funciona técnicamente, **pero viola la EULA de
Apple** salvo en hardware Apple. No cubierta aquí.

---

## Resumen recomendado

- **Validación rápida** en cada commit → Vía 1.1 + 1.2 (gratis).
- **Probar en tu iPhone** sin gastar en Mac → Vía 1.3 (Apple Dev $99/año
  + TestFlight).
- **Debug interactivo** ocasional → Vía 4 (MacInCloud por horas).

La parte Android sigue funcionando 100 % en Windows con
`./gradlew :app:assembleDebug`, completamente independiente del estado
iOS — la pipeline `android` del CI lo refuerza en cada PR.

