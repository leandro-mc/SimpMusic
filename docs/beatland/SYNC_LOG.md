# Bitácora de sincronizaciones y de la integración inicial

| Fecha | Qué | Notas |
|---|---|---|
| 2026-10-19 | Creación de `myapp` desde `dev` e integración inicial del zip "Sonara-delta" | Ver `DECISIONS.md` D-008, D-010, D-013 para lo que NO se portó o cambió respecto al zip. |
| 2026-11-12 | `feature/mejoras`: tarea 3 — cola de descargas de escritorio (máx. 2 a la vez, en segundo plano) y vigilante de bloqueos de la UI | D-048, D-049. |
| 2026-11-12 | `feature/mejoras`: tarea 2 — la pestaña resaltada de las barras sigue al destino real de la navegación | D-050. |
| 2026-11-12 | `feature/mejoras`: tarea 1 — ícono de la app original con el nombre Beatland (Android: el flavor deja de sobrescribirlo; escritorio: referencias de ícono de vuelta a las de upstream, incluido `mac { icons }` de D-045) | D-047 (reemplaza a D-020). |
| 2026-11-12 | `feature/mejoras`: auditoría de marca — factura de Analytics, coreano, canal de notificación de Android, logo de créditos al original; `apply_brand.py` reconoce cualquier capitalización | D-051. |
| 2026-11-10 | **`sync/dev-2026-11-09` fusionada en `myapp`** (`--no-ff`) tras confirmar el dueño que compila y funciona en Windows y en Android; rama de sync borrada. Queda fuera `b7dd4d66` de upstream (2026-11-10) | D-045, D-046. |
| 2026-11-09 | `sync/dev-2026-11-09`: la compilación de Android del dueño falló por clases duplicadas `kaml`/`kotaml` (el `core` nuevo añade `kotaml`); se excluye `kaml` y se añade un workflow que compila Android en CI | D-046. |
| 2026-11-09 | **Sincronización `sync/dev-2026-11-09`** (`dev` en `fbdba0da`, 51 commits, `core` → `164e89e0`; versión 2.5.0). 9 conflictos: 5 `strings.xml` (upstream + marca), `ModalBottomSheet.kt` (3 botones), `LibraryScreen.kt` (hoja compartida + selector), `desktopApp/build.gradle.kts` (ícono Beatland), `libs.versions.toml` (AGP 9.3.0, Kotlin 2.4.21). Guardas nuevas de `ForkPolicy` (launch banner, `LyricsPromoLine`) y selector también en el botón Crear del Home. Pendiente: compilar `beatlandDebug` y probar | D-045. |
| 2026-11-09 | `feature/desktop-beatland-installer`: el collage se guardaba pero no se veía en Windows; el diagnóstico (D-043) mostró que Coil 3.6.3 no parsea `file:/C:/…`; se añade un *mapper* en el `ImageLoader` de escritorio (D-044) | D-043, D-044. |
| 2026-11-08 | `feature/desktop-beatland-installer`: el instalador de Windows fallaba por la suma de `linux-x64` (upstream re-publicó esa rebanada); suma alineada con upstream y descarga solo de `windows-x64`. **Sin sincronizar `dev`** (49 commits por detrás; aplazado, ver D-041). Además el CI cayó al descargar Gradle (no era nuestro código): pre-descarga con reintentos y errores como anotaciones | D-040, D-041, D-042. |
| 2026-10-26 | `feature/desktop-beatland-installer`: collage de Windows (despertar fiable, construcción inmediata, verificación) y barra del selector sin animación | D-039. |
| 2026-10-24 | `feature/desktop-beatland-installer`: marca/logo de escritorio, instalador MSI con versión creciente, workflow, guía de Windows, mitigación del arranque y collage en Windows | D-035 a D-038. |
| 2026-10-22 | `feature/floating-lyrics-controls`: letra flotante desde la canción en reproducción, miniplayer y velocidad; cambios de modo instantáneos; burbuja al borde y Reset fijo | D-032 a D-034. |
| 2026-10-21 | `feature/ai-translation-quality` (apilada sobre `feature/playlist-song-picker`): traducción con IA fiable, regenerar traducción y estilo de traducción | D-028 a D-030. |
| 2026-10-20 | `feature/playlist-song-picker`: selector de canciones, tope 500, rediseño UX, vista previa y collage automático | D-023 a D-027. |
| 2026-10-19 | `feature/beatland-cleanup`: promos y actualizaciones desactivados, ícono nuevo, flavor `beatland` por defecto | D-007, D-009, D-018 a D-021. |
