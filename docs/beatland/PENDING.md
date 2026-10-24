# Pendiente y ramas planificadas (todas salen de `myapp`)

| Rama | Qué | Origen |
|---|---|---|
| `feature/brand-audit` | Auditoría de lo que el script no cubre: drawables/imágenes con texto de marca, `fastlane/`, `README.md`, logos, pantalla de créditos (conservando la atribución a upstream y la licencia GPL-3.0). | `BRANDING.md` |

## Notas abiertas

- **Escritorio sin compilar:** la versión de Windows no se ha compilado nunca con los cambios de Beatland. Primer paso: generar el instalador (`DESKTOP.md`) y pasar el registro si falla.

- `myapp` compila (confirmado por el dueño tras corregir AGP y `vivoAndroidApp`).
- Verificar en el teléfono, con el APK `beatland`, que aparecen "Floating lyrics" en Ajustes y el nombre Beatland en el launcher (ver `BUILD.md` para comprobar la variante instalada).
- Escritorio/`conveyor`: `vcs-url` ya apunta al fork; probar el empaquetado cuando convenga.
