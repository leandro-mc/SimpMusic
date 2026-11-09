# Pendiente y ramas planificadas (todas salen de `myapp`)

| Rama | Qué | Origen |
|---|---|---|
| `feature/brand-audit` | Auditoría de lo que el script no cubre: drawables/imágenes con texto de marca, `fastlane/`, `README.md`, logos, pantalla de créditos (conservando la atribución a upstream y la licencia GPL-3.0). | `BRANDING.md` |

## Notas abiertas

- **Sincronizar con upstream (aplazado, D-041):** `dev` está 49 commits por detrás (2.3.0 → 2.5.0, `core` nuevo, ícono y escritorio rediseñados). Hacerlo en `sync/dev-AAAA-MM-DD` desde `myapp` siguiendo `SYNC.md`, cuando el dueño confirme en Windows el instalador y los pendientes del collage y del botón del selector. Conflictos previstos en un ensayo: 5 `strings.xml`, `ModalBottomSheet.kt`, `LibraryScreen.kt`, `desktopApp/build.gradle.kts`, `libs.versions.toml`; los íconos de `composeApp/icon/` de upstream chocan con los generados (D-020).

- **Escritorio:** los instaladores `beatland-desktop-v1` a `v3` se compilaron, instalaron y actualizaron en el sitio. Desde D-039 el código no se había vuelto a compilar (la v4 cayó en la suma de libmpv, D-040); la v5 es la primera comprobación.

- `myapp` compila (confirmado por el dueño tras corregir AGP y `vivoAndroidApp`).
- Verificar en el teléfono, con el APK `beatland`, que aparecen "Floating lyrics" en Ajustes y el nombre Beatland en el launcher (ver `BUILD.md` para comprobar la variante instalada).
- Escritorio/`conveyor`: `vcs-url` ya apunta al fork; probar el empaquetado cuando convenga.
