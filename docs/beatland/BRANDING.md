# Marca: cómo funciona el nombre "Beatland"

**Fuente única:** `beatland.properties` → `brand.name=Beatland`.

## Cambiar el nombre

1. Edita `brand.name` en `beatland.properties` (sin `< > & " \ $`).
2. `python3 scripts/beatland/apply_brand.py`
3. `python3 scripts/beatland/apply_brand.py --check` debe terminar en OK.
4. Pide al dueño que compile. El script detecta el nombre aplicado antes (lo lee de `Brand.kt`), así que sirve para renombrar de nuevo.

## Qué cubre

| Lugar | Mecanismo |
|---|---|
| Textos de todos los idiomas (`composeResources/values*/*.xml`, incluido `app_name`) | `apply_brand.py` reemplaza "SimpMusic" (y la marca anterior) |
| Constante Kotlin `Brand.NAME` | Generada por el script |
| Nombre del launcher Android (`androidApp/src/beatland*/res/values/app_name.xml`) | Generado por el script (`<nombre>` y `<nombre> Dev`) |
| `conveyor.conf` (`start-menu.group`, `StartupWMClass`) | Script |
| Escritorio: `packageName`, `.desktop`, `app.display-name`, nombre del `.exe` | Gradle lee `beatland.properties` (`beatlandBrandName`) |
| Literales en Kotlin (títulos, nombres de archivo compartido, carpeta de backups, factura de Analytics, etc.) | Código usa `Brand.NAME` |
| Nombre del canal de notificación de reproducción de Android (recurso de `core`) | Script: genera `androidApp/src/beatland/res/values/notification_channel.xml`, que lo sobrescribe (D-051) |

## Qué NO cambia (ver D-003, D-006, D-007, D-009)

Identificadores internos, URLs, "SimpMusic Lyrics", los 4 textos promocionales del autor y la etiqueta del canal de actualización. Tampoco las menciones al sitio `simpmusic.org` como servicio de upstream (convertir bibliotecas) ni los textos que viven en `core` (Discord Rich Presence, descripción de las playlists creadas en YouTube Music), que no se pueden tocar (D-051).
Los créditos a upstream y la licencia GPL-3.0 se mantienen (no se eliminan avisos de copyright).

## Código nuevo

Nunca escribas el nombre a mano. Si añades un texto visible en Kotlin, usa `Brand.NAME`.
Si upstream añade textos con "SimpMusic", `apply_brand.py` los convierte (strings) o `--check` los reporta (Kotlin). Ambos reconocen también `Simpmusic`, `SIMPMUSIC` y `Simp Music`; solo la forma toda en minúsculas (esquemas, dominios, carpetas) se deja, porque no es un nombre visible.

## Lo que el script no puede revisar

Imágenes y drawables con texto de marca (revisados con OCR en D-051: ninguno lo lleva; repetir si se añaden imágenes), metadatos de tiendas (`fastlane/`) y `README.md`. Ver `PENDING.md` → `feature/brand-audit`.
