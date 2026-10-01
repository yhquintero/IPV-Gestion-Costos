# ADR‑0004 · Política de tasa de cambio para documentos

| Campo | Valor |
|---|---|
| **Estado** | **Aceptado (provisional al iniciar Fase 2)** |
| **Fecha** | 2026‑10‑01 |
| **Decide** | Default de [D‑03](../16-decisiones-pendientes.md#d-03) |
| **Relacionados** | [Doc 10](../10-flujo-eltoque-cache.md) · [Doc 13](../13-estrategia-actualizacion-tasas.md) |

## Contexto

Qué tasa congela una ficha es [D‑03]. No hay token de elTOQUE ([D‑04]). Hay que modelar `rate_policies` y las instantáneas ya.

## Decisión

1. Política **por empresa** (`rate_policies`).
2. elTOQUE es **referencia etiquetada**, nunca "tasa oficial".
3. Se permite tasa **manual con motivo**.
4. Las fichas que salen de BORRADOR exigen `rate_snapshot_id`.
5. Las muestras de prueba (`SEED_TEST`, `is_test`) viven en JSON de semilla, **no** como constantes de código. Producción se niega a arrancar si las encuentra.

## Consecuencias

- El puerto de tasas y la tasa manual se usan en Fase 3; el proveedor real en Fase 8.
- CAD/MXN/ZELLE/CLA existen como instrumentos; si la API no los entrega, solo hay tasa manual.
