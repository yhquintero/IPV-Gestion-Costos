# ADR‑0006 · Orden y vocabulario IPV ↔ Ficha de Costo

| Campo | Valor |
|---|---|
| **Estado** | **Aceptado (provisional al iniciar Fase 2)** |
| **Fecha** | 2026‑10‑01 |
| **Decide** | Default de [D‑01](../16-decisiones-pendientes.md#d-01) |
| **Relacionados** | [Doc 6](../06-flujo-ficha-ipv.md) |

## Decisión

1. **Valor IPV** (`ipv_values`) y **Control IPV** (`ipv_controls`) son entidades distintas.
2. El valor precede a la ficha; el control se liga a *versiones* de ficha (no 1:1).
3. Modo de control por defecto: `CONSISTENCIA`.
4. Políticas por empresa en `companies.settings` (`lines_require_ipv_value`, `control_requires_active_sheet`, `ipv_control_mode`).
5. La norma cubana de referencia sigue ⛔ [D‑02]; no se inventan fórmulas legales.

## Verificación

I‑09 (línea de control vs versión) e I‑10 (vigencia de valores IPV) en la suite de BD.
