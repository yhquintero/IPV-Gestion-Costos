# G‑2 · Revisión de aislamiento, amenazas, conflictos y recuperación

Gate G‑2 del [ADR‑0001](../adr/0001-reabrir-offline-y-multisucursal.md). **Diseñado** en el paquete de análisis; **pendiente** de visto bueno del propietario y de un revisor técnico independiente ([D‑22](../16-decisiones-pendientes.md#d-22)).

| Criterio | Dónde está | Estado |
|---|---|---|
| (a) Aislamiento `organization_id` + RLS forzado + FK compuestas | [doc 3](../03-modelo-er.md) · Flyway V2–V12 | Diseñado e implementado en esquema |
| (b) Matriz de amenazas (A‑04 payload local, A‑15 fuzz sync) | [doc 11 §11.3](../11-estrategia-seguridad.md#113-modelo-de-amenazas) | Diseñado |
| (c) Política de conflicto por entidad | [doc 9 §9.4](../09-flujo-android-offline-sync.md#94-política-de-conflictos-por-entidad) · `SyncRules.policy` | Conteos/movimientos = anexado; borradores/líneas = `base_version` |
| (d) Recuperación / `RESYNC_REQUIRED` tras restauración | E‑5 · `sync_server_state.epoch` | Diseñado y cubierto en la suite JVM |

Esta página **no** sustituye la revisión humana. No se activa escritura offline en clientes reales con solo este documento.
