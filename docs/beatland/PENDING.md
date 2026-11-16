# Pendiente y ramas planificadas (todas salen de `myapp`)

| Rama | Qué | Origen |
|---|---|---|
| `feature/brand-audit` | Auditoría de lo que el script no cubre. Hecho en D-051: imágenes con texto de marca (OCR), literales Kotlin en cualquier capitalización, canal de notificación, pantalla de créditos. Pendiente: `fastlane/` y `README.md` (metadatos de tienda y documentación del repo; no se ven dentro de la app), conservando la atribución a upstream y la licencia GPL-3.0. | `BRANDING.md` |

## Notas abiertas

- **Sincronización 2026-11-09 (D-045, D-046): hecha y fusionada en `myapp`.** `dev` y `myapp` trajeron los 51 commits de upstream (versión 2.5.0, `core` 164e89e0). El dueño probó en Windows (instaladores `beatland-desktop-v9`/`v10`) y en Android (compila y funciona, también el reconocimiento de canciones). **Próxima sincronización:** upstream ya tiene `b7dd4d66` (2026-11-10, controles del reproductor SoundCloud + puntero de `core`), no incluido.
- **Collage en Windows (D-044):** el dueño confirmó el 2026-11-10 que se ve bien. El diagnóstico de D-043 (`PlaylistCoverDiagnostics` y los dos *callbacks* de `AdapterItems.kt`) ya no hace falta; es solo log y puede quitarse cuando convenga (quitarlo también reduce un archivo de upstream tocado).
- **CI deshabilitado por GitHub (2026-11-14, D-054):** los workflows del fork se apagaron por exceso de ejecuciones. Ahora CI solo con autorización explícita del dueño y como máximo una vez por semana; las pruebas se compilan en el PC del dueño (`BUILD.md`). **Sin verificar por CI ni compilar todavía:** `dfa72c68` (D-052, navegación) y `7993d740` (D-053, playlists), más la rama completa `feature/improvements`; el dueño los compilará en su PC. El último commit que compiló en CI es `5eaa6dba`.
- Escritorio/`conveyor`: `vcs-url` ya apunta al fork; probar el empaquetado de macOS/Linux solo si algún día se necesita (el CI solo construye el MSI de Windows, y solo con autorización, D-054).
