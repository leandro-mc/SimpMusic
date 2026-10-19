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
| Literales en Kotlin (títulos, nombres de archivo compartido, carpeta de backups, etc.) | Código usa `Brand.NAME` |

## Qué NO cambia (ver D-003, D-006, D-007, D-009)

Identificadores internos, URLs, "SimpMusic Lyrics", los 4 textos promocionales del autor y la etiqueta del canal de actualización.
Los créditos a upstream y la licencia GPL-3.0 se mantienen (no se eliminan avisos de copyright).

## Código nuevo

Nunca escribas el nombre a mano. Si añades un texto visible en Kotlin, usa `Brand.NAME`.
Si upstream añade textos con "SimpMusic", `apply_brand.py` los convierte (strings) o `--check` los reporta (Kotlin).

## Lo que el script no puede revisar

Imágenes y drawables con texto de marca, metadatos de tiendas (`fastlane/`), `README.md` y logos. Ver `PENDING.md` → `feature/brand-audit`.
