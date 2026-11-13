# Sincronizar `myapp` con `dev` (upstream)

Cada cierto tiempo se traen las novedades del proyecto original. **Nunca** se hace directamente en `myapp`.

## Pasos

1. **Dueño:** en GitHub, "Sync fork" sobre la rama `dev` (debe ser fast-forward; `dev` nunca tiene commits propios).
   Si `dev` se desvió, avisar: algo se commiteó donde no debía.
2. Crear la rama de integración desde `myapp`:
   `git checkout myapp && git checkout -b sync/dev-AAAA-MM-DD`
3. Traer `dev`: `git merge dev` (merge, no rebase: conserva el historial y facilita revertir).
4. Resolver conflictos con esta tabla (y con `DECISIONS.md`):

| Archivo en conflicto | Qué hacer |
|---|---|
| `composeResources/values*/*.xml` | Tomar la versión de upstream y volver a correr `apply_brand.py` (paso 6). |
| `conveyor.conf`, `desktopApp/build.gradle.kts` | Tomar upstream y reaplicar los puntos de `RULES.md` → "Mantener siempre". Los íconos (`iconFile`, `icons`, también el de `mac {}`) son los de upstream (`circle_app_icon` y, en macOS, `rounded_app_icon`): desde D-047 no hay nada que reaplicar aquí. |
| `androidApp/build.gradle.kts` | Conservar los bloques `productFlavors`, `IS_PERSONAL_FORK` y `ignoredFlavors … "beatland"`. |
| `MainActivity.kt` | Conservar `!BuildConfig.IS_PERSONAL_FORK` en `checkOfficialBuild`. |
| `SettingScreen.kt` | Conservar el bloque `floating_lyrics` y el `if (ForkPolicy.UPSTREAM_UPDATE_CHECK_ENABLED)` que envuelve los ajustes de actualización; si upstream cambió el patrón de `SettingGroup`/`SettingItem`, adaptar. |
| `HomeScreen.kt`, `SharedViewModel.kt` | Conservar las guardas de `ForkPolicy` (promos, *launch banner* y `checkForUpdate`) y `createLocalPlaylistAndPickSongs`. Si upstream añade un diálogo o banner promocional nuevo, protegerlo igual. |
| `LyricsPromoLine.kt` | Conservar el `return` inicial con `ForkPolicy.UPSTREAM_PROMOS_ENABLED` (D-045). |
| `CreateSheet.kt` | Conservar el estado y la hoja del selector (`PlaylistSongPickerSheet`) y el `onCreate` que usa `createLocalPlaylistAndPickSongs` (D-045). |
| `build.gradle.kts` (raíz) | Conservar el bloque `lintVital` dentro de `subprojects` (D-022) y el `exclude(group = "com.charleskorn.kaml")` de `configurations.all` (D-046; revisar si upstream ya resolvió el choque `kaml`/`kotaml` de otra forma). |
| `ViewModelModule.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt`, `LocalPlaylistScreen.kt`, `SongSelectionState.kt` | Conservar los ganchos del selector de playlists (marcados `BEATLAND (D-023/D-024)`); ver `FEATURES.md`. |
| `App.kt` | Conservar `PlaylistCollageEffect()` (D-027). |
| `ModalBottomSheet.kt`, `SettingScreen.kt` (letra flotante) | Conservar la acción "Letra flotante" del menú de la canción en reproducción y el interruptor basado en `floatingLyricsWindowShowing()` (D-032). |
| `SharedViewModel.kt` (traducción), `ModalBottomSheet.kt`, `LyricsView.kt`, `AppleMusicLyricsLines.kt`, `NowPlayingContentSpotify.kt`, `NowPlayingContentM3Expressive.kt`, `AppleMusicShared.kt` | Conservar `RobustLyricsTranslator` en `translateWithAi`, `TranslationQueue` (`translationQueue`, `runTranslationJob`), `regenerateAiTranslation`, la acción del menú, los estilos de traducción (`AppleMusicTranslatedColor` ahora vale `BeatlandTranslationColor`, `beatlandTranslationStyle`) y el filtro de traducciones en blanco (D-028 a D-030). |
| `DesktopApp.kt` | Conservar el `ToastHost` diferido (`toastHostReady`) (D-036); el resto de cambios de marca ya se reaplican con `apply_brand.py`. |
| `desktopApp/build.gradle.kts` (bloque `windows {}`, `description`/`vendor`) | Conservar los metadatos del MSI, **el `upgradeUuid` fijo** (D-035) y `beatlandWindowsVersion` / `msiPackageVersion` (D-037). |
| `composeApp/src/jvmMain/kotlin/com/maxrave/simpmusic/DesktopApp.kt` | Tomar upstream y reaplicar el import y `add(DriveLetterFileUriMapper())` dentro de `.components { }` del `ImageLoader` (D-044). |
| `composeApp/src/jvmMain/kotlin/com/maxrave/simpmusic/DesktopApp.kt` (además) | Reaplicar el import y la llamada `installBeatlandDesktopServices()` justo después del bloque `startKoin { }` y antes de leer el idioma: debe ejecutarse **después** de Koin y **antes** de que nada pida el `DownloadHandler` (D-048, D-049). |
| `AppBottomNavigationBar.kt`, `DesktopSidebar.kt`, `LiquidGlassAppBottomNavigationBar.android.kt` | Reaplicar el import y la llamada `SyncSelectedTab(navController, <pestañas visibles>, selectedIndex) { selectedIndex = it }` justo antes de `val selectTab` (común y barra lateral) o de `var isExpanded` (Android) (D-050). Si upstream pasa a derivar la pestaña seleccionada del `NavController`, quitar el ayudante. |
| `ListeningReceipt.kt` (factura de Analytics) | Conservar `Brand.NAME.uppercase()` en el título y `Brand.NAME.lowercase()` en el pie (D-051); si upstream los reescribe con literales, `apply_brand.py --check` lo detecta. |
| Todos los archivos que llaman a `navigateOnce(...)` o `returnToTab(...)` (`grep -rn "navigateOnce\|returnToTab" composeApp/src`) | Reaplicar el cambio línea a línea tras un merge: son llamadas `navController.navigate(<Destino sin argumentos>)` convertidas en `navController.navigateOnce(...)` (más el import), y la rama «pestaña seleccionada con subpantalla encima» de `selectTab` en las cuatro barras (D-052). Si upstream añade un botón nuevo a una pantalla global, usar lo mismo. |
| `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/AdapterItems.kt` | Tomar upstream y reaplicar, en `HomeItemContentPlaylist`, el import de `PlaylistCoverDiagnostics` y los *callbacks* `onSuccess`/`onError` del `AsyncImage` (D-043). |
| `composeApp/build.gradle.kts` | Tomar upstream, **incluidas las sumas de `mpvNativesChecksums`** (upstream es la fuente de confianza: nunca pegar el "actual" de un error; D-040), y reaplicar `notCompatibleWithConfigurationCache` de `mpvSetupAll` (D-012) y el filtro `mpvSlicesToSetup` / `-PmpvSlices` (D-040). |
| `gradle/libs.versions.toml` | Conservar AGP `9.3.0` (D-019). |
| `vivoAndroidApp/build.gradle.kts` | Conservar `IS_PERSONAL_FORK` (D-018). |
| `CLAUDE.md` | Tomar upstream y volver a poner la línea de cabecera que apunta a `docs/beatland/README.md`. |
| Cualquier otro | Preferir upstream y reaplicar solo el cambio listado en `RULES.md`/`FEATURES.md`. |

5. Si upstream **re-introduce algo que decidimos no traer** (ver `RULES.md` → "Nunca traer"), descartarlo y anotarlo.
6. Reaplicar la marca: `python3 scripts/beatland/apply_brand.py` y luego `python3 scripts/beatland/apply_brand.py --check`.
   Corregir los literales Kotlin que reporte con `Brand.NAME`.
7. Validar estáticamente que la ventana flotante sigue compilando contra upstream: revisar que existan y no hayan cambiado de firma
   los símbolos listados en `FEATURES.md` → "Dependencias de upstream".
8. Registrar en `DECISIONS.md` cualquier decisión nueva, actualizar `CHANGES.md` si cambió algún archivo propio o tocado, y anotar en `SYNC_LOG.md` la fecha y los conflictos resueltos.
9. **Pedir al dueño que compile** `beatlandDebug`. Si hay errores, él los pasa y se corrigen en la misma rama.
10. Cuando compile, merge de `sync/dev-AAAA-MM-DD` a `myapp` (PR o `--no-ff`). Después borrar la rama de sync.
11. Las ramas `feature/*` abiertas se actualizan con `git merge myapp`.

## Si upstream toca el submódulo `core`

El puntero `core` se actualiza solo con el merge. Tras el merge: `git submodule update --init`. Nunca commitear dentro de `core/`.

- Si cambia `DownloadHandler` (`domain/…/mediaservice/handler/DownloadHandler.kt`) o el `DownloadUtils` de escritorio (`media-jvm/…/download/DownloadUtils.kt`), revisar `beatland/download/` (D-048): el decorador sustituye `downloadTrack`, `removeDownload`, `removeAllDownloads` y `downloadTask` y delega el resto; la cola asume que `downloadTrack` de escritorio suspende hasta terminar y que deja `SongEntity.downloadState` en DOWNLOADED o NOT_DOWNLOADED.
- Si el `DownloadHandler` pasa a registrarse de otra forma en Koin (otro tipo, `qualifier`, o `allowOverride(false)`), `installThrottledDownloads()` deja de sustituirlo (lo avisa en el log: «The queue was NOT installed»).
