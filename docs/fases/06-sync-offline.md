# Fase 6 · Sincronización y offline (ADR‑0001)

| | |
|---|---|
| **Estado** | En curso — protocolo, suite E‑1…E‑5, API, outbox Android (etapa **6a**) |
| **Fecha** | 2026‑10‑01 |
| **Gate** | E‑1…E‑5 verdes en CI; G‑2 documentado (pendiente de revisión del propietario); 2 semanas de uso interno 6a **no ejecutadas aquí** |

## Entregables

| Pieza | Ubicación |
|---|---|
| Motor de sync (función pura + almacén en memoria) | `core/domain/.../sync/` |
| Suite G‑3 E‑1…E‑5 | `core/domain/src/test/.../sync/SyncScenariosTest.kt` |
| `POST /sync/push` · `GET /sync/changes` · `GET /sync/bootstrap` | `server/app/.../sync/` · OpenAPI 0.6.0 |
| Flyway V12 (epoch, sombra, payload) | `server/db/migration/V12__sync_epoch_shadow.sql` |
| Room v2: outbox, conflictos, conteos | `android/core/data` |
| `SyncWorker` (push antes que pull, Wi‑Fi/red) | `android/core/data/.../sync/SyncWorker.kt` |
| UI conteos 6a + centro de conflictos | `feature/inventory`, `feature/sync` |
| Revisión G‑2 | [06-g2-revision.md](06-g2-revision.md) |
| CI | `.github/workflows/ci-sync.yml` |

## Etapas

| Etapa | Offline editable | En este corte |
|---|---|---|
| **6a** | Conteos de inventario (solo anexado) | **Sí** (`SyncStage.A_COUNTS` por defecto) |
| **6b** | Líneas de Control IPV y movimientos | Tipos en el motor; no habilitados en prod (`ipvgc.sync.stage`) |
| **6c** | Borradores de ficha | Igual; transiciones de estado siguen **ONLINE_ONLY** |

Ningún cliente real escribe offline hasta G‑2 y G‑3 en verde (ADR‑0001).

## Cómo verificar

```bash
gradle :core:domain:test --tests cu.ipvgc.domain.sync.SyncScenariosTest
```

`RESYNC_REQUIRED` conserva la outbox PENDING. `mutation_id` es idempotente (I‑15).
