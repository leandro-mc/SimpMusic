# Pendiente y ramas planificadas (todas salen de `myapp`)

| Rama | Qué | Origen |
|---|---|---|
| `feature/brand-audit` | Auditoría de lo que el script no cubre: drawables/imágenes con texto de marca, `fastlane/`, `README.md`, logos, pantalla de créditos (conservando la atribución a upstream y la licencia GPL-3.0). | `BRANDING.md` |

## Notas abiertas

- **Sincronizar con upstream (aplazado, D-041):** `dev` está 51 commits por detrás a 2026-11-09 (2.3.0 → 2.5.0 más lo posterior al changelog; `core` con 27 commits nuevos; ícono y escritorio rediseñados). Hacerlo en `sync/dev-AAAA-MM-DD` desde `myapp` siguiendo `SYNC.md`, cuando el dueño confirme en Windows el instalador y los pendientes del collage y del botón del selector. Conflictos previstos en un ensayo: 5 `strings.xml`, `ModalBottomSheet.kt`, `LibraryScreen.kt`, `desktopApp/build.gradle.kts`, `libs.versions.toml`; los íconos de upstream **no** chocan con los nuestros (están en archivos propios del *flavor* `beatland`). A revisar a mano en el merge: (a) `LyricsPromoLine` / `PromoSelection` de upstream muestran avisos de su `remote-config.json` bajo la letra: hay que protegerlos con `ForkPolicy` (D-007); (b) la letra con palabras sincronizadas (LRCLIB) y "Add lyrics" tocan `LyricsView.kt` y `ModalBottomSheet.kt`, donde están nuestra traducción con IA y la letra flotante; (c) el botón Create de Home y la biblioteca nueva tocan `HomeScreen.kt` y `LibraryScreen.kt`, donde están el selector de canciones y el collage; (d) AGP 9.5.0 de upstream frente a nuestro 9.3.0 (D-019).

- **Collage en Windows (D-044):** causa encontrada y reproducida (Coil 3.6.3 no parsea `file:/C:/…`); arreglo en `beatland-desktop-v8`. **No darlo por resuelto hasta que el dueño lo vea en pantalla.** Tras confirmarlo, valorar quitar el diagnóstico de D-043.
- **Escritorio:** los instaladores `beatland-desktop-v1` a `v3` se compilaron, instalaron y actualizaron en el sitio. Desde D-039 el código no se había vuelto a compilar: la v4 cayó en la suma de libmpv (D-040) y la v5 en la descarga de Gradle (D-042). **La v6 (`587c2d98`) compiló y generó `Beatland-2.3.1647-windows-x64.msi`** (su `libmpv-2.dll` es idéntica a la rebanada verificada y el `UpgradeCode` no cambió). Falta probarlo en Windows.

- `myapp` compila (confirmado por el dueño tras corregir AGP y `vivoAndroidApp`).
- Verificar en el teléfono, con el APK `beatland`, que aparecen "Floating lyrics" en Ajustes y el nombre Beatland en el launcher (ver `BUILD.md` para comprobar la variante instalada).
- Escritorio/`conveyor`: `vcs-url` ya apunta al fork; probar el empaquetado cuando convenga.
