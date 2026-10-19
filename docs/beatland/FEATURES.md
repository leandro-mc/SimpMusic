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

**Disponibilidad:** `isFloatingLyricsAvailable()` es `true` solo si el `applicationId` contiene `.beatland`.

**Diseño:** azul y azul marino (D-004); el tinte dinámico de la carátula puede ser de cualquier color.

### Dependencias de upstream (validar tras cada sync)

`LyricsView(...)`, `RichSyncLyricsLineItem`, `ParsedRichSyncLine`, `parseRichSyncWords`, `angledGradientBackground`, `rgbFactor`,
`rememberNowPlayingGlowTint`, `AppTheme`, `SimpIcons.{Album,Close,Delete,DragHandle,Fullscreen,FullscreenExit,Settings}`,
`SharedViewModel.{timeline,nowPlayingScreenData,controllerState,onUIEvent}`, `UIEvent.UpdateProgress`, `SettingGroup`, `SettingItem`.

## Otros cambios respecto al original

- Flavor Android `beatland` instalable junto al oficial (D-002) y sin bloqueo de "build no oficial" (D-010).
- Nombre y ajustes de escritorio con la marca parametrizada (`BRANDING.md`).
- Ajuste de configuration cache en tareas de mpv/Conveyor (D-012).
