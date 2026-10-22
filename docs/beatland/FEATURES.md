# Funcionalidades propias de Beatland

## Ventana flotante de letras (solo Android, solo flavor `beatland`)

Una burbuja con la letra actual que queda sobre otras apps (WhatsApp, Instagram…); se arrastra y se expande al tocar.
Se activa en Ajustes → "Floating lyrics". Oculta en cualquier otro build/plataforma.

**Archivos propios (nuevos):**
- `composeApp/src/androidMain/.../floatinglyrics/` → `ComposeServiceLifecycle.kt`, `FloatingLyricsOverlayUi.kt`, `FloatingLyricsPermission.kt`, `FloatingLyricsService.kt`
- `composeApp/src/commonMain/.../expect/floatinglyrics/FloatingLyricsCapability.kt` (+ `.android.kt` en androidMain, `.jvm.kt` en jvmMain)
- `androidApp/src/beatland/AndroidManifest.xml` (permiso `SYSTEM_ALERT_WINDOW` y el servicio, añadidos por el merge de manifests)
- `androidApp/src/beatland/res/**` (ícono) y `androidApp/src/beatlandDebug/res/**`

**Ganchos en archivos de upstream (los únicos):**
- `SettingScreen.kt`: imports + bloque `item(key = "floating_lyrics")`, justo antes de `sponsor_block`.
- `androidApp/build.gradle.kts`: flavors (ver `DECISIONS.md` D-002, D-010, D-016).

**iOS:** el target iOS está comentado en `composeApp/build.gradle.kts` (upstream), por eso no hay `actual` en `iosMain`. Si upstream lo activa, añadir un `FloatingLyricsCapability.ios.kt` con todo en `false`/no-op (igual que el de jvmMain).

**Disponibilidad:** `isFloatingLyricsAvailable()` es `true` solo si el `applicationId` contiene `.beatland`.

**Diseño:** azul y azul marino (D-004); el tinte dinámico de la carátula puede ser de cualquier color.

### Dependencias de upstream (validar tras cada sync)

`LyricsView(...)`, `RichSyncLyricsLineItem`, `ParsedRichSyncLine`, `parseRichSyncWords`, `angledGradientBackground`, `rgbFactor`,
`rememberNowPlayingGlowTint`, `AppTheme`, `SimpIcons.{Album,Close,Delete,DragHandle,Fullscreen,FullscreenExit,Settings}`,
`SharedViewModel.{timeline,nowPlayingScreenData,controllerState,onUIEvent}`, `UIEvent.UpdateProgress`, `SettingGroup`, `SettingItem`.

## Selector de canciones para playlists (D-024)

Al crear una playlist (Biblioteca → crear) se abre una hoja a pantalla casi completa para llenarla; también se abre desde la fila
"Añadir canciones" que hay arriba de la lista en cualquier playlist local (vacía o no).
- Con el buscador vacío: chips **Recientes / Más escuchadas / Me gusta**. Al escribir: resultados de tu biblioteca + YouTube Music (con retardo de 400 ms).
- Selección múltiple **sin tope**; las canciones que ya están en la playlist salen marcadas y no se pueden volver a elegir.
- Botón "Añadir N canciones" (solo aparece con canciones seleccionadas); se añaden una a una con `addTrackToLocalPlaylist` (que inserta la canción en la base si viene de la búsqueda).
- **Vista previa (D-026):** cada fila tiene un botón ▶ en el extremo derecho para reproducir la canción antes de decidir; mientras suena pasa a ⏸. Reemplaza la cola actual, como al tocar una canción en cualquier lugar de la app.
- Construido solo con componentes de la app (D-025): `SongFullWidthItems` en modo selección, `Chip`, píldora de búsqueda y hoja como las de `ModalBottomSheet.kt`.
- Además, el tope de la selección múltiple normal pasa de 25 a 500 (D-023).

**Archivos propios (nuevos):** `composeApp/src/commonMain/.../beatland/picker/{PlaylistSongPickerViewModel,PlaylistSongPickerSheet,AddSongsRow}.kt`,
`composeResources/values/beatland_strings.xml` y `values-es/beatland_strings.xml`.

**Ganchos en archivos de upstream:** `ViewModelModule.kt` (1 línea de registro), `LibraryViewModel.kt` (`createPlaylistAndPickSongs`),
`LibraryScreen.kt` (abre el selector tras crear), `LocalPlaylistScreen.kt` (fila + selector), `SongSelectionState.kt` (constante).

**Dependencias de upstream (validar tras cada sync):** `SongRepository.{getAllSongs,getMostPlayedSongs,getLikedSongs}`, `SearchRepository.getSearchDataSong`,
`LocalPlaylistRepository.{addTrackToLocalPlaylist,getListTrackVideoId,getAllLocalPlaylists,insertLocalPlaylist}`, `SongsResult.toTrack`, `Track.toSongEntity`,
`SongFullWidthItems`, `Chip`, `rememberSurfaceDarkColors`, `LocalForceDarkText`, `typo()`, `SimpIcons.{Add,Close,Search}`.

## Collage automático de playlists locales (D-027)

Las playlists locales sin portada propia muestran un collage de hasta 4 carátulas de sus primeras canciones, que se actualiza solo cuando
esas canciones cambian. Si el usuario elige su propia portada, esa se respeta y el collage deja de generarse para esa playlist.
- No se aplica a playlists sincronizadas con YouTube ni a las que tengan otra miniatura (p. ej. importadas).
- Backfill: las playlists locales que ya existían sin portada reciben su collage en segundo plano al abrir la app.
- El collage aparece solo, unos 3-6 segundos después del último cambio, **sin salir de la pantalla**: la playlist abierta y la lista de la biblioteca se recargan con un evento (`PlaylistCollageEvents`).
- Coste: una consulta local por revisión (sin red ni imágenes si nada cambió). Va cada 3 s mientras hay cambios y pasa a cada 15 s tras ~1 minuto en calma; no se detiene mientras la app está abierta. El selector de canciones lo despierta al instante.
- Si se vacía una playlist, su collage anterior queda hasta que se le añadan canciones.
- Para volver al collage después de poner una portada propia habría que quitarla (no hay opción en la interfaz todavía).

**Archivos propios (nuevos):** `composeApp/.../beatland/collage/{PlaylistCollageIo.kt, PlaylistCollageManager.kt, PlaylistCollageEvents.kt}` (common), `PlaylistCollage.android.kt` (androidMain) y
`PlaylistCollage.jvm.kt` (jvmMain). **Gancho en upstream:** `App.kt` (1 línea: `PlaylistCollageEffect()`).
**Dependencias de upstream:** `LocalPlaylistRepository.{getAllLocalPlaylists,updateThumbnailLocalPlaylist}`, `SongRepository.getSongsByListVideoId`, `persistPickedImage`.

## Otros cambios respecto al original

- **Promos del autor y verificador de actualizaciones desactivados** mediante `ForkPolicy` (D-007, D-009).
- **Ícono Beatland** generado por script (D-020): `python3 scripts/beatland/generate_icon.py [--preview]` (requiere Pillow y numpy). Verifica además que las notas queden dentro de la zona segura del ícono adaptativo.
- Flavor Android `beatland` instalable junto al oficial (D-002) y sin bloqueo de "build no oficial" (D-010).
- Nombre y ajustes de escritorio con la marca parametrizada (`BRANDING.md`).
- Ajuste de configuration cache en tareas de mpv/Conveyor (D-012).
