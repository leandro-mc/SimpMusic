# Bitácora de sincronizaciones y de la integración inicial

| Fecha | Qué | Notas |
|---|---|---|
| 2026-10-19 | Creación de `myapp` desde `dev` e integración inicial del zip "Sonara-delta" | Ver `DECISIONS.md` D-008, D-010, D-013 para lo que NO se portó o cambió respecto al zip. |
| 2026-11-09 | `feature/desktop-beatland-installer`: el collage se guardaba pero no se veía en Windows; el diagnóstico (D-043) mostró que Coil 3.6.3 no parsea `file:/C:/…`; se añade un *mapper* en el `ImageLoader` de escritorio (D-044) | D-043, D-044. |
| 2026-11-08 | `feature/desktop-beatland-installer`: el instalador de Windows fallaba por la suma de `linux-x64` (upstream re-publicó esa rebanada); suma alineada con upstream y descarga solo de `windows-x64`. **Sin sincronizar `dev`** (49 commits por detrás; aplazado, ver D-041). Además el CI cayó al descargar Gradle (no era nuestro código): pre-descarga con reintentos y errores como anotaciones | D-040, D-041, D-042. |
| 2026-10-26 | `feature/desktop-beatland-installer`: collage de Windows (despertar fiable, construcción inmediata, verificación) y barra del selector sin animación | D-039. |
| 2026-10-24 | `feature/desktop-beatland-installer`: marca/logo de escritorio, instalador MSI con versión creciente, workflow, guía de Windows, mitigación del arranque y collage en Windows | D-035 a D-038. |
| 2026-10-22 | `feature/floating-lyrics-controls`: letra flotante desde la canción en reproducción, miniplayer y velocidad; cambios de modo instantáneos; burbuja al borde y Reset fijo | D-032 a D-034. |
| 2026-10-21 | `feature/ai-translation-quality` (apilada sobre `feature/playlist-song-picker`): traducción con IA fiable, regenerar traducción y estilo de traducción | D-028 a D-030. |
| 2026-10-20 | `feature/playlist-song-picker`: selector de canciones, tope 500, rediseño UX, vista previa y collage automático | D-023 a D-027. |
| 2026-10-19 | `feature/beatland-cleanup`: promos y actualizaciones desactivados, ícono nuevo, flavor `beatland` por defecto | D-007, D-009, D-018 a D-021. |
