<!--
Copyright 2026 SIRGPrice

This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge

Licensed under the Blue Edge Custom License 1.0.
You may not use this file except in compliance with that license.
GitHub may host, cache, display, and facilitate collaboration on this file
as required by the GitHub Terms of Service.
See the repository root: BLUE_EDGE_CUSTOM_LICENSE.md
-->
# Asset Region Editor

Herramienta **oficial de escritorio** (Kotlin/JVM + Swing con FlatLaf Dark) para definir las
regiones (celdas) dentro de los spritesheets de `app/src/main/assets/stocatstic/` y
asociarles un `id` + `label` + categoría. El resultado se guarda en

- `tools/asset-regions/asset_regions.json` — **fuente de verdad** (commit en el repo)

y se transpila a

- `app/src/main/java/com/google/ai/edge/gallery/customtasks/stocatstic/ui/assets/AssetCatalogs.generated.kt`

que es lo que consume la app Android en runtime.

> Este módulo **no forma parte del APK**. Vive en `:tools:asset-region-editor` y sólo se
> compila/ejecuta en la máquina del desarrollador. La app móvil ya **no** incluye un
> mini-editor de celdas — toda asignación de coordenadas se hace aquí y se consume como
> preset en la galería del juego.

## Requisitos

- JDK 17+ (el toolchain Gradle lo descarga si no está disponible).
- Gradle wrapper incluido en el repo.

## Ejecutar

Desde la raíz del repo:

```powershell
./gradlew :tools:asset-region-editor:run
```

La ventana se abre con el JSON actual ya cargado. La UI tiene cuatro zonas claramente
separadas:

```
┌─────────────────────── Top Bar (acciones + Cell + Zoom) ──────────────────────┐
│ Sidebar │                         Canvas                         │ Inspector  │
│  📁      │  • fondo checker (ve píxeles transparentes)             │  📝 Región │
│ ASSETS  │  • grid con líneas cada 4 celdas resaltadas            │  📊 Tabla   │
│ + buscar │  • celdas asignadas coloreadas por categoría           │            │
│         │  • hover / selección en dorado                         │            │
├─────────┴────────── Status bar (toast + hover + totales) ─────────────────────┤
└───────────────────────────────────────────────────────────────────────────────┘
```

## Atajos de teclado y ratón

| Gesto                              | Acción                                                   |
|------------------------------------|----------------------------------------------------------|
| Click izquierdo                    | Alterna una celda en la selección actual (amarilla)      |
| Middle-drag / Right-drag           | Pan del canvas (pan dentro del scroll)                   |
| Ctrl + rueda                       | Zoom in/out (1×–16×, entero)                             |
| Rueda                              | Scroll vertical                                          |
| Escribir en la barra de búsqueda   | Filtra la lista de assets por nombre                     |

## Flujo de trabajo oficial

1. **Ejecutar**: `./gradlew :tools:asset-region-editor:run`.
2. Escribe en la **barra de búsqueda** (sidebar) para filtrar hasta el asset que quieres
   editar — la lista muestra el número de regiones ya definidas por asset.
3. Ajusta **Cell** (px, 16 por defecto; 32/48 para algunos `Object Animation/`). El valor se
   recuerda por asset en `cellSizeOverrides`.
4. Haz **zoom** con Ctrl+rueda y **click izquierdo** en las celdas que quieras asignar.
5. Rellena **id**, **label**, **category** en el inspector y pulsa **➕ Añadir / actualizar**.
   Para personajes, además **characterId** + **char. slot**.
6. El **tipo** (`plant/solid/normal`) ya no se edita en el formulario principal: se cambia
   exclusivamente en la tarjeta **TIPO DE TASK SPRITES SELECCIONADOS**, permitiendo edición
   masiva de una o varias filas `task_sprite` a la vez.
7. Puedes crear directamente una región en el JSON con **✨ Nueva región JSON**:
   abre un diálogo para definir `category`, `assetPath`, `id`, `label`, `col`, `row`,
   `colSpan`, `rowSpan` y, según aplique, `characterId` + `characterSlot` o `assetType`.
   El mismo diálogo permite **previsualizar** el rectángulo en el canvas antes de confirmar.
   El `assetPath` debe apuntar a un PNG ya existente en `app/src/main/assets/stocatstic/`.
8. Las regiones ya guardadas aparecen translúcidas en el canvas y en la **tabla inferior**;
   seleccionarlas en la tabla las carga en el formulario para editarlas.
9. **✔ Validar** (o `./gradlew :tools:asset-region-editor:validateRegions` en CI) comprueba
   IDs únicos, campos requeridos y que los ficheros referenciados existan en disco.
10. **💾 Guardar JSON** → escribe `tools/asset-regions/asset_regions.json` (ordenado,
   determinista).
11. **⚙ Generar catálogos Kotlin** → escribe `AssetCatalogs.generated.kt`.
12. Compila la app: `./gradlew :app:assembleDebug`.

## Validación headless (CI)

```powershell
./gradlew :tools:asset-region-editor:validateRegions
```

Devuelve **exit code 1** y escribe cada error a `stderr` si:
- Hay `(category, id)` duplicados.
- Algún `id` / `label` está vacío.
- `col`/`row` son negativos o `colSpan`/`rowSpan` < 1.
- Una región con `category = character` no lleva `characterId` o `characterSlot`.
- El `assetPath` referenciado no existe en disco.

Warnings actualmente no existen; sólo errores bloqueantes.

## Esquema JSON

Ver `tools/asset-region-editor/src/main/kotlin/com/blueedge/assettool/model/Model.kt`:

```jsonc
{
  "version": 1,
  "cellSizeOverrides": { "stocatstic/Object Animation/Fountain.png": 32 },
  "regions": [
    {
      "assetPath": "stocatstic/Tilesets/Exterior_Tileset.png",
      "category": "root",
      "id": "mailbox",
      "label": "Buzón",
      "col": 12, "row": 4,
      "colSpan": 1, "rowSpan": 1
    }
  ]
}
```

Categorías válidas: `character`, `root`, `path`, `task_sprite`, `enemy`, `animal`, `ui`.

Para `character` usa los campos extra `characterId` (agrupador: mismo valor para las 7 tiras
de un personaje) y `characterSlot` (`idle|run|hoe|scythe|water|sword|hurt`). El generador
agrupa las 7 tiras en un único `CharacterEntry`.

## Integración con la app

- `AssetCatalogs.generated.kt` expone:
  - `object PathCatalogGen { val ENTRIES: List<GalleryEntry> }`
  - `object RootCatalogGen { val ENTRIES: List<GalleryEntry> }`
  - `object TaskSpriteCatalogGen { val ENTRIES: List<GalleryEntry> }`
  - `object CharacterCatalogGen { val ALL: List<CharacterEntry> }`
- En la app, la **galería** (`AssetGallerySheet`) expone estos catálogos al usuario para que
  elija qué sprite usar por personaje / inicio / camino / tarea. **No hay selección manual
  de tile en la app** — todo se hace aquí.

## Paleta y diseño visual

Basado en FlatLaf Dark + tokens propios (`ui/theme/EditorTheme.kt`):

| Rol              | Color             | Uso                                          |
|------------------|-------------------|----------------------------------------------|
| `background`     | `#0F1115`         | Fondo del canvas y layout base                |
| `surface`        | `#161A21`         | Cards, sidebar, tabla                        |
| `surfaceElevated`| `#1E232C`         | Checker alterno, hover sutil                 |
| `border`         | `#2A313D`         | Separadores, outlines                        |
| `accent`         | `#4C9AFF`         | Botón primario, focus, selección de tabla    |
| `highlight`      | `#FFCE52`         | Selección viva en el canvas                  |
| `success`        | `#64D589`         | Toasts informativos                          |
| `warning`        | `#FFA53C`         | Toasts de aviso                              |

Cada categoría tiene su color dedicado (`catCharacter`, `catRoot`, ...) que se pinta tanto
en el overlay del canvas como en la columna de la tabla.

## Limitaciones conocidas

- Selección multi-celda funciona como *bounding box* continua (min/max de las celdas
  clicadas). No hay selección libre de píxeles (`bbox`) por UI; el schema ya lo soporta para
  añadirlo más adelante.
- La herramienta ya no crea ni edita PNGs: solo referencia assets existentes y escribe regiones
  en el JSON.
- Categorías `animal`, `enemy`, `ui` están en el modelo pero no hay generador Kotlin
  todavía — se añadirán cuando el runtime los consuma.
- Pan sólo con middle/right-drag; no hay "hold space + drag" al estilo Photoshop (TODO).
