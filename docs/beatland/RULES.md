# Reglas obligatorias de Beatland

Estas reglas se superponen a `CLAUDE.md`. Ver `DECISIONS.md` para el porqué de cada una.

## Flujo de trabajo

1. **Nunca** hagas commit en `dev` (es espejo del original).
2. Toda funcionalidad nueva va en `feature/<nombre>` creada desde `myapp`. No commitees directo en `myapp`
   salvo la documentación o la sincronización con `dev` (ver `SYNC.md`).
3. **No hay forma de compilar en el entorno del agente.** Cuando termines, di al dueño qué compilar
   (variante `beatlandDebug` o `beatlandRelease`) y espera los errores. No afirmes que compila.
4. Toda decisión nueva se registra en `DECISIONS.md` en el mismo commit que la introduce.
5. Minimiza los cambios en archivos del proyecto original: prefiere **archivos nuevos**, source sets de
   flavor (`androidApp/src/beatland/`) y ganchos pequeños. Cada línea cambiada en un archivo de upstream es
   un futuro conflicto de merge.
6. No toques el submódulo `core/` (apunta a `maxrave-dev/core`, que no es nuestro). Si algo requiere cambiarlo,
   busca un gancho en `composeApp`/`androidApp` o pregunta al dueño.

## Diseño

- **Prohibido el color morado y derivados** (violeta, índigo, magenta, lila, púrpura) en todo lo nuevo o
  personalizado. Los colores dinámicos derivados de la carátula del álbum son aceptables (no son una elección
  de diseño). Lo que ya venga así del original se deja.
- **Prohibido usar íconos que hagan referencia a IA** (destellos/sparkles, `auto_awesome`, robots, cerebros,
  varitas mágicas, etc.) en lo personalizado. Lo del original se deja.
- Los textos de las funciones personalizadas pueden estar en inglés literal si evita tocar `strings.xml`.

## Marca

- El nombre visible de la app es **Beatland** y sale de `beatland.properties`. **No escribas el nombre a
  mano** en código nuevo: usa `Brand.NAME` (Kotlin), `@string/app_name` (Android) o `beatlandBrandName` (Gradle).
- No renombres identificadores internos (paquete `com.maxrave.simpmusic`, `applicationId`, claves de recursos,
  esquemas de URL, carpeta `.simpmusic`). Ver `D-003`.
- Después de cada merge, ejecuta `python3 scripts/beatland/apply_brand.py` y luego `--check`.

## Mantener siempre (cambios respecto al original que no se pueden perder en un merge)

- Flavors `official`/`beatland` y los `buildConfigField IS_PERSONAL_FORK` en `androidApp/build.gradle.kts`.
- La guarda `!BuildConfig.IS_PERSONAL_FORK` en `MainActivity.kt` (si se pierde, `beatlandRelease` queda bloqueado).
- `ignoredFlavors.set(setOf("foss", "beatland"))` de Sentry en `androidApp/build.gradle.kts`.
- `androidApp/src/beatland/**` y `androidApp/src/beatlandDebug/**`.
- Paquete `floatinglyrics/` (androidMain) y los `expect/actual` de `FloatingLyricsCapability`.
- El bloque `floating_lyrics` en `SettingScreen.kt`.
- `beatland.properties`, `scripts/beatland/`, `docs/beatland/`, `composeApp/.../brand/Brand.kt`.
- Íconos `beatland_app_icon.*` y sus referencias en escritorio.
- Los `notCompatibleWithConfigurationCache` de `mpvSetupAll` y `writeConveyorConfig`.

## Nunca traer / nunca restaurar

- `RetryingDataStore` (obsoleto, upstream lo arregló de otra forma). Ver `D-008`.
- Cualquier lista `OFFICIAL_PACKAGE_NAMES` modificada: no se edita.
