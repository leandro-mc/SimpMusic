# Reglas obligatorias de Beatland

Estas reglas se superponen a `CLAUDE.md`. Ver `DECISIONS.md` para el porqué de cada una.

## Flujo de trabajo

1. **Nunca** hagas commit en `dev` (es espejo del original).
2. Toda funcionalidad nueva va en `feature/<nombre>` creada desde `myapp`. No commitees directo en `myapp`
   salvo la documentación o la sincronización con `dev` (ver `SYNC.md`).
3. **No hay forma de compilar en el entorno del agente.** Cuando termines, di al dueño qué compilar
   (variante `beatlandDebug` o `beatlandRelease`) y espera los errores. No afirmes que compila.
4. Toda decisión nueva se registra en `DECISIONS.md`, y todo cambio respecto al original (archivo de upstream tocado o archivo propio nuevo) en `CHANGES.md`, en el mismo commit que lo introduce.
5. Minimiza los cambios en archivos del proyecto original: prefiere **archivos nuevos**, source sets de
   flavor (`androidApp/src/beatland/`) y ganchos pequeños. Cada línea cambiada en un archivo de upstream es
   un futuro conflicto de merge.
6. No toques el submódulo `core/` (apunta a `maxrave-dev/core`, que no es nuestro). Si algo requiere cambiarlo,
   busca un gancho en `composeApp`/`androidApp` o pregunta al dueño.

## UX y armonía con la app (prioridad máxima)

La experiencia de usuario es **muy importante**. Toda funcionalidad nueva debe sentirse como parte original de la app, en
**forma, UX, componentes, colores, tipografía, espaciados y comportamiento**. Una función que "funciona" pero se ve ajena, tiene
texto ilegible o colores chillones **no está terminada**. (Caso real: el primer selector de canciones usó colores genéricos de
Material y quedó celeste chillón e ilegible; hubo que rehacerlo con los componentes de la app. Ver D-025.)

Reglas:

1. **Reutiliza antes de crear.** Antes de dibujar algo, busca en `composeApp/src/commonMain/.../ui/component/` y en pantallas parecidas
   (`grep`) cómo lo resuelve la app y usa ese componente o patrón. Ejemplos ya existentes: `SongFullWidthItems` (filas de canción, con modo de
   selección), `Chip`, hojas con `ModalBottomSheet` + `Card` + `rememberSurfaceDarkColors()`, barra de búsqueda tipo píldora, `ActionButton`,
   `SettingGroup`/`SettingItem`, `EndOfPage`.
2. **Colores solo desde el tema**: `MaterialTheme.colorScheme.*`, `rememberSurfaceDarkColors()`, `seed`/`favoriteColor` de `ui/theme/Color.kt`.
   Nada de hex sueltos para decorar. No uses `primaryContainer`/`secondaryContainer` como adorno: en esta app el color semilla es un celeste
   claro y esos contenedores salen saturados y fuera de armonía.
3. **Legibilidad garantizada**: usa siempre pares de color del tema (`primary` con `onPrimary`, `surface` con `onSurface`). Si el fondo no es
   una superficie normal, pon el `color` del `Text` explícito (el `typo()` con `LocalForceDarkText` pinta blanco y desaparece sobre fondos claros).
   Evita botones deshabilitados con texto casi invisible: oculta la acción hasta que aplique o mantén contraste suficiente.
4. **Forma**: mismos radios, paddings (filas: `vertical = 6.dp, horizontal = 15.dp`), tamaños de miniatura (48 dp), estilos de texto
   (`typo().titleSmall`/`bodySmall`/`labelSmall`) y la misma estructura de hojas/diálogos que ya usa la app.
5. **Prueba en ambos temas**: claro y oscuro, y donde aplique con `LocalForceDarkText` (pantallas con degradado, como las playlists).
6. **Comportamiento esperado**: estados de carga, vacío y error visibles; teclado que no tapa la lista; áreas táctiles normales; sin acciones
   que parezcan no hacer nada.
7. **Si no hay componente equivalente**, créalo con los mismos tokens y deja anotado en `CHANGES.md` qué patrón imita.
8. El dueño valida el aspecto en su teléfono. En el aviso de entrega indica qué componentes de la app reutilizaste y qué revisar visualmente.

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
- La guarda `!BuildConfig.IS_PERSONAL_FORK` en `MainActivity.kt` (si se pierde, `beatlandRelease` queda bloqueado) y el campo `IS_PERSONAL_FORK` en `androidApp` **y** en `vivoAndroidApp` (D-018).
- `ForkPolicy.kt` y sus guardas: `HomeScreen.kt` (promos), `SharedViewModel.checkForUpdate()` y el bloque de actualizaciones de `SettingScreen.kt` (D-007, D-009).
- AGP `9.3.0` en `gradle/libs.versions.toml` (D-019).
- Desactivación de `lintVital*` en el `build.gradle.kts` raíz (D-022).
- `isDefault = true` del flavor `beatland` (D-021).
- `ignoredFlavors.set(setOf("foss", "beatland"))` de Sentry en `androidApp/build.gradle.kts`.
- `androidApp/src/beatland/**` y `androidApp/src/beatlandDebug/**`.
- Paquetes `beatland/picker/` y `beatland/collage/`, `PlaylistCollageEffect()` en `App.kt` y `beatland_strings.xml` (D-024, D-026, D-027); `MAX_SONG_SELECTION = 500` (D-023).
- Paquete `floatinglyrics/` (androidMain) y los `expect/actual` de `FloatingLyricsCapability`.
- El bloque `floating_lyrics` en `SettingScreen.kt`.
- `beatland.properties`, `scripts/beatland/`, `docs/beatland/`, `composeApp/.../brand/Brand.kt`.
- Íconos generados por `scripts/beatland/generate_icon.py` (D-020), que **sobrescribe** `drawable-v24/ic_launcher_*.xml`, los `mipmap-*` del flavor y `beatland_app_icon.*`; sus referencias en escritorio también. No editar esos archivos a mano.
- Los `notCompatibleWithConfigurationCache` de `mpvSetupAll` y `writeConveyorConfig`.

## Nunca traer / nunca restaurar

- `RetryingDataStore` (obsoleto, upstream lo arregló de otra forma). Ver `D-008`.
- Cualquier lista `OFFICIAL_PACKAGE_NAMES` modificada: no se edita.
