# Pendiente y ramas planificadas (todas salen de `myapp`)

| Rama | Qué | Origen |
|---|---|---|
| `feature/disable-author-promos` | Desactivar los 4 textos promocionales del autor (blog, footguns, "star en GitHub", reseña) y sus diálogos. | D-007 |
| `feature/disable-update-checker` | Desactivar el verificador de actualizaciones que apunta a releases de upstream. | D-009 |
| `feature/brand-icon` | Rediseñar el ícono (hoy una "S" de Sonara) para Beatland: launcher Android y escritorio (`beatland_app_icon.*`). | D-017 |
| `feature/brand-audit` | Auditoría de lo que el script no cubre: drawables/imágenes con texto de marca, `fastlane/`, `README.md`, logos, pantalla de créditos (conservando la atribución a upstream y la licencia GPL-3.0). | `BRANDING.md` |

## Notas abiertas

- Primera compilación de `myapp` aún sin confirmar: el dueño debe compilar `beatlandDebug` y reportar errores.
- Escritorio/`conveyor`: `vcs-url` ya apunta al fork; probar el empaquetado cuando convenga.
