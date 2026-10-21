# Beatland vs. SimpMusic original — lista de cambios

Resumen conciso de **todo lo que Beatland modifica o agrega** respecto a `dev` (el espejo del original).
Para el porqué de cada cosa → `DECISIONS.md`. Para el detalle de las funciones → `FEATURES.md`.

> **Regla:** toda modificación a un archivo de upstream o todo archivo propio nuevo se anota aquí **en el mismo commit**.

Comprobar que este documento coincide con la realidad (debe listar lo mismo):

```
git diff --name-status dev...myapp -- . ':!docs'     # archivos que difieren del original
git diff --stat dev...myapp -- . ':!docs' | tail -1  # resumen
```

## 1. Qué es distinto (por tema)

| Tema | Qué cambia | Decisión |
|---|---|---|
| **Nombre** | La app se llama **Beatland** en todos los textos visibles (31 idiomas), en el launcher, en el escritorio y en nombres de archivos compartidos. Se cambia desde `beatland.properties` + `apply_brand.py`. | D-005, D-014 |
| **Ícono** | Ícono nuevo "Beamed" (notas con travesaño-onda), generado por script. Vectores adaptativos, monocromo, PNG, ICO, ICNS. | D-020 |
| **Instalación en paralelo** | Flavor Android `beatland` (`applicationId` con sufijo `.beatland`) junto a `official`; `beatland` es el predeterminado. | D-002, D-021 |
| **Letra flotante** | Ventana flotante con la letra encima de otras apps (solo Android, solo flavor `beatland`), con su ajuste en Configuración. Paleta azul, sin morado. | D-004 |
| **Selector de canciones para playlists** | Al crear una playlist (y desde "Añadir canciones" en cualquier playlist local) se abre un selector con Recientes / Más escuchadas / Me gusta y búsqueda en biblioteca + YouTube Music, con selección sin tope. Hecho con los componentes de la app (hoja, `Chip`, `SongFullWidthItems`, píldora de búsqueda). | D-024, D-025 |
| **Vista previa en el selector** | Botón ▶/⏸ en cada fila para escuchar la canción antes de añadirla (reemplaza la cola actual). | D-026 |
| **Miniatura de playlists locales** | Collage automático de hasta 4 carátulas para playlists locales sin portada propia; se actualiza solo y respeta la portada del usuario. | D-027 |
| **Tope de selección múltiple** | 25 → 500 canciones. | D-023 |
| **Promos del autor original** | Diálogos de reseña/"star en GitHub", blog y footguns **desactivados** (`ForkPolicy`). | D-007 |
| **Actualizaciones** | Verificador de actualizaciones **desactivado** y sus 3 ajustes ocultos (apuntaban a releases de upstream). | D-009 |
| **Chequeo de "build no oficial"** | Se omite en el flavor `beatland` (`BuildConfig.IS_PERSONAL_FORK`); upstream bloquearía el `applicationId` propio. | D-010, D-018 |
| **Build** | Sentry ignorado en `beatland`; `lintVital*` desactivado en todos los módulos; AGP fijado en 9.3.0; `mpvSetupAll` y `writeConveyorConfig` incompatibles con configuration cache (marcado). | D-016, D-022, D-019, D-012 |
| **Escritorio** | Nombre/íconos/título de ventana/protocolo de Windows/Conveyor con la marca; `vcs-url` apunta al fork. | D-005, D-011 |
| **Carpeta de backups automáticos** | `Download/Beatland` en vez de `Download/SimpMusic`. | D-005 |
| **Documentación para agentes** | `AGENTS.md`, `docs/beatland/`, y una línea al inicio de `CLAUDE.md`. | D-001, D-015 |

## 2. Archivos de upstream que modificamos (puntos de conflicto en cada merge)

| Archivo | Cambio (resumido) |
|---|---|
| `androidApp/build.gradle.kts` | Flavors `official`/`beatland` (`isDefault`), `IS_PERSONAL_FORK`, Sentry `ignoredFlavors` + `"beatland"` |
| `androidApp/.../MainActivity.kt` | `checkOfficialBuild` solo si `!IS_PERSONAL_FORK` |
| `androidApp/.../backup/AutoBackupWorker.kt` | Carpeta `Download/${Brand.NAME}` |
| `vivoAndroidApp/build.gradle.kts` | Campo `IS_PERSONAL_FORK = false` (comparte `MainActivity`) |
| `build.gradle.kts` (raíz) | Desactiva `lintVital*` en todos los módulos |
| `gradle/libs.versions.toml` | AGP `9.3.0` |
| `composeApp/build.gradle.kts` | `mpvSetupAll` marcado `notCompatibleWithConfigurationCache` |
| `composeApp/.../di/ViewModelModule.kt` | Registro de `PlaylistSongPickerViewModel` (1 línea) |
| `composeApp/.../viewModel/LibraryViewModel.kt` | Nueva función `createPlaylistAndPickSongs` |
| `composeApp/.../viewModel/SharedViewModel.kt` | `checkForUpdate()` sale de inmediato (`ForkPolicy`) |
| `composeApp/.../ui/screen/home/SettingScreen.kt` | Ajuste "Floating lyrics"; oculta los 3 ajustes de actualización |
| `composeApp/.../ui/screen/home/HomeScreen.kt` | Guardas de `ForkPolicy` en los diálogos promocionales |
| `composeApp/.../App.kt` | Inicia `PlaylistCollageEffect()` (1 línea) |
| `composeApp/.../ui/screen/library/LibraryScreen.kt` | Abre el selector tras crear una playlist |
| `composeApp/.../ui/screen/library/LocalPlaylistScreen.kt` | Fila "Añadir canciones" + selector |
| `composeApp/.../ui/component/selection/SongSelectionState.kt` | `MAX_SONG_SELECTION = 500` |
| `composeApp/.../values*/strings.xml` (31 idiomas) | "SimpMusic" → Beatland (lo hace `apply_brand.py`; se conservan "SimpMusic Lyrics" y 4 textos promo) |
| Literales de marca con `Brand.NAME` | `SimpMusicChart.kt`, `ShareLyricsCard.kt`, `ShareLyricsSheet.kt`, `ShareTasteSheet.kt`, `ListeningReceipt.kt`, `WrappedScreen.kt`, `WrappedShareCard.kt`, `ListenTogetherScreen.kt`, `AppLogFiles.kt`, `ShareImage.android.kt` |
| Escritorio | `DesktopApp.kt`, `CrashDialog.kt`, `WindowsProtocolRegistrar.kt`, `MiniPlayerWindow.kt`, `desktopApp/build.gradle.kts`, `desktopApp/.../Main.kt`, `conveyor.conf` |
| `CLAUDE.md`, `.gitignore` | Línea de cabecera que apunta a `docs/beatland/`; ignora `__pycache__` |

## 3. Archivos propios nuevos (no existen en upstream)

| Ruta | Para qué |
|---|---|
| `beatland.properties` | Nombre de la marca (fuente única) |
| `scripts/beatland/apply_brand.py` | Aplica la marca a strings, `Brand.kt`, `app_name.xml`, `conveyor.conf`; `--check` busca "SimpMusic" visibles |
| `scripts/beatland/generate_icon.py` | Genera todo el ícono (vectores + PNG/ICO/ICNS) |
| `composeApp/.../brand/Brand.kt` | `Brand.NAME` (**generado** por `apply_brand.py`) |
| `composeApp/.../brand/ForkPolicy.kt` | Interruptores: promos y verificador de actualizaciones |
| `composeApp/.../floatinglyrics/` (androidMain, 4 archivos) | Servicio, interfaz, permiso y ciclo de vida de la letra flotante |
| `composeApp/.../expect/floatinglyrics/FloatingLyricsCapability*.kt` | `expect` (common) y `actual` (android, jvm) |
| `composeApp/.../beatland/picker/` (3 archivos) | Selector de canciones: ViewModel, hoja de interfaz y fila de entrada |
| `composeApp/.../beatland/collage/` + `androidMain`/`jvmMain` (4 archivos) | Collage automático de miniaturas: `expect`, observador y `actual` por plataforma |
| `composeResources/values/beatland_strings.xml`, `values-es/…` | Textos del selector (claves `bl_`) |
| `androidApp/src/beatland/**`, `androidApp/src/beatlandDebug/**` | Manifest del flavor (permiso y servicio de la letra flotante), nombre e íconos |
| `composeApp/icon/beatland_app_icon.*`, `composeResources/drawable/beatland_app_icon.png` | Íconos de escritorio y de la app (generados) |
| `AGENTS.md`, `docs/beatland/*` | Documentación para agentes y para ti |

## 4. Cosas del zip de Sonara que NO integramos

- `RetryingDataStore` / cambio a `DataStoreManagerImpl.jvm.kt`: obsoleto (upstream lo arregló) y vive en el submódulo `core`. → D-008
- Bucle tolerante a fallos de `mpvSetupAll` y su checksum linux-x64: upstream ya tiene uno más nuevo. → D-013
- Ícono "S" de Sonara: reemplazado por el actual. → D-020

## 5. Cosas del original que mantenemos a propósito

- Identificadores internos (paquete `com.maxrave.simpmusic`, claves de recursos, esquemas de URL, carpeta `.simpmusic`). → D-003
- "SimpMusic Lyrics" (servicio de terceros) y los 4 textos promocionales del autor (inertes). → D-006, D-007
- El submódulo `core/` sin tocar.
