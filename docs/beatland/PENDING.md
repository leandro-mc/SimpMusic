# Pendiente y ramas planificadas (todas salen de `myapp`)

| Rama | Qué | Origen |
|---|---|---|
| `feature/brand-audit` | Auditoría de lo que el script no cubre. Hecho en D-051: imágenes con texto de marca (OCR), literales Kotlin en cualquier capitalización, canal de notificación, pantalla de créditos. Pendiente: `fastlane/` y `README.md` (metadatos de tienda y documentación del repo; no se ven dentro de la app), conservando la atribución a upstream y la licencia GPL-3.0. | `BRANDING.md` |

## Notas abiertas

- **Sincronización 2026-11-09 (D-045, D-046): hecha y fusionada en `myapp`.** `dev` y `myapp` trajeron los 51 commits de upstream (versión 2.5.0, `core` 164e89e0). El dueño probó en Windows (instaladores `beatland-desktop-v9`/`v10`) y en Android (compila y funciona, también el reconocimiento de canciones). **Próxima sincronización:** upstream ya tiene `b7dd4d66` (2026-11-10, controles del reproductor SoundCloud + puntero de `core`), no incluido.
- **Collage en Windows (D-044):** el dueño confirmó el 2026-11-10 que se ve bien. El diagnóstico de D-043 (`PlaylistCoverDiagnostics` y los dos *callbacks* de `AdapterItems.kt`) ya no hace falta; es solo log y puede quitarse cuando convenga (quitarlo también reduce un archivo de upstream tocado).
- **Android:** `.github/workflows/beatland-android-check.yml` compila `beatlandDebug` en CI con una etiqueta `beatland-android-check-<n>` (D-046); usarlo antes de pedir al dueño que compile.
- Escritorio/`conveyor`: `vcs-url` ya apunta al fork; probar el empaquetado de macOS/Linux solo si algún día se necesita (el CI solo construye el MSI de Windows).
