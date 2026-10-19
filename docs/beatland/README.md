# Beatland — documentación del fork

Beatland es una versión personalizada de [SimpMusic](https://github.com/maxrave-dev/SimpMusic) (GPL-3.0).
Esta carpeta **se superpone** a `CLAUDE.md` y a cualquier documento del proyecto original: si algo aquí
contradice al original, **gana esta carpeta**.

## Orden de lectura para cualquier agente de IA

1. `RULES.md` — reglas obligatorias y restricciones (léelo siempre).
2. `DECISIONS.md` — decisiones ya tomadas (IDs `D-0xx`). Consúltalo **antes** de resolver cualquier conflicto de merge.
3. `SYNC.md` — procedimiento para traer cambios de `dev` a `myapp`.
4. `BRANDING.md` — cómo funciona el nombre Beatland y el script `apply_brand.py`.
5. `FEATURES.md` — funcionalidades propias y los archivos que las componen.
6. `BUILD.md` — cómo compila el dueño del proyecto.
7. `PENDING.md` — trabajo pendiente y ramas planificadas.

## Modelo de ramas

| Rama | Rol | ¿Se puede editar? |
|---|---|---|
| `dev` | Espejo de `dev` del proyecto original. Solo se actualiza con "Sync fork". | **NO**, nunca commits propios |
| `myapp` | Rama de control: la versión Beatland. Aquí se resuelven los merges con `dev`. | Solo vía merge/PR desde ramas de trabajo |
| `feature/<nombre>` | Una funcionalidad nueva. Siempre sale de `myapp` y vuelve a `myapp`. | Sí |
| `sync/dev-AAAA-MM-DD` | Rama temporal para traer `dev` a `myapp` (ver `SYNC.md`). | Sí, es temporal |

## Reglas de comunicación

- Las conversaciones con el dueño son en **español**. Esta documentación está en **español**.
- El **código, los comentarios, los mensajes de commit y los nombres de rama** van en **inglés**.
- (Esto sustituye la regla "Always respond in English" de `CLAUDE.md`.)
