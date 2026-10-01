# `core:domain`

Biblioteca JVM **sin framework** (ADR‑0002). Usada por el servidor y, en Fase 5, por Android (`includeBuild`).

- `Money` / `Rate` / `CostingArithmetic` / `RoundingPolicy` (ADR‑0007)
- Máquinas de estado de ficha y control IPV
- Vectores dorados en `src/main/resources/golden/`
- Evaluador de licencia (función pura, 7 estados + NO ACTIVADA / LÍMITE)
- `TestDataGuard` para rechazar muestras `is_test` en producción

Android lo incorpora con `includeBuild("../core")` (Fase 5). Este directorio tiene `settings.gradle.kts` propio para esa composición; el *build* raíz sigue incluyendo `:core:domain` como subproyecto.
