# Pendiente y ramas planificadas (todas salen de `myapp`)

| Rama | Qué | Origen |
|---|---|---|
| `feature/brand-audit` | Auditoría de lo que el script no cubre: drawables/imágenes con texto de marca, `fastlane/`, `README.md`, logos, pantalla de créditos (conservando la atribución a upstream y la licencia GPL-3.0). | `BRANDING.md` |

## Notas abiertas

- **Sincronización 2026-11-09 (D-045):** `myapp` ya incluye el instalador de Windows (`17f0b318`); `dev` está al día con upstream (`fbdba0da`) y la rama `sync/dev-2026-11-09` trae esos 51 commits. **No está fusionada en `myapp`.** Falta: (1) que el dueño compile `beatlandDebug` en Android Studio y pase los errores, si los hay; (2) probar en Windows el instalador de la rama de sync; (3) probar en el teléfono las novedades y que nada nuestro se perdió (selector, collage, traducción, letra flotante); luego `--no-ff` a `myapp` y borrar la rama de sync.

- **Collage en Windows (D-044):** causa encontrada y reproducida (Coil 3.6.3 no parsea `file:/C:/…`); arreglo en `beatland-desktop-v8`. **No darlo por resuelto hasta que el dueño lo vea en pantalla.** Tras confirmarlo, valorar quitar el diagnóstico de D-043.
- **Escritorio:** los instaladores `beatland-desktop-v1` a `v3` se compilaron, instalaron y actualizaron en el sitio. Desde D-039 el código no se había vuelto a compilar: la v4 cayó en la suma de libmpv (D-040) y la v5 en la descarga de Gradle (D-042). **La v6 (`587c2d98`) compiló y generó `Beatland-2.3.1647-windows-x64.msi`** (su `libmpv-2.dll` es idéntica a la rebanada verificada y el `UpgradeCode` no cambió). Falta probarlo en Windows.

- `myapp` compila (confirmado por el dueño tras corregir AGP y `vivoAndroidApp`).
- Verificar en el teléfono, con el APK `beatland`, que aparecen "Floating lyrics" en Ajustes y el nombre Beatland en el launcher (ver `BUILD.md` para comprobar la variante instalada).
- Escritorio/`conveyor`: `vcs-url` ya apunta al fork; probar el empaquetado cuando convenga.
