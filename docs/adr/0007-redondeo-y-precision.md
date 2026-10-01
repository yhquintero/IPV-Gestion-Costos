# ADR‑0007 · Redondeo y precisión monetaria

| Campo | Valor |
|---|---|
| **Estado** | **Aceptado (provisional al iniciar Fase 2)** |
| **Fecha** | 2026‑10‑01 |
| **Decide** | Default de [D‑25](../16-decisiones-pendientes.md#d-25) |
| **Relacionados** | [Doc 6.10](../06-flujo-ficha-ipv.md) · `core:domain` |

## Decisión

1. **Nunca** `float`/`double` para dinero. `NUMERIC(19,4)` en BD; `BigDecimal` en dominio.
2. Redondeo **por línea** a centavos, `RoundingMode.HALF_UP`.
3. El total es la **suma de líneas ya redondeadas**.
4. Costo por unidad de rendimiento: `HALF_UP` a centavos de `total / yield`.
5. Tasas `NUMERIC(18,6)` estrictamente positivas (I‑11).
6. Moneda base `CUP`; conjunto permitido por empresa (I‑20 / D‑27).

## Verificación

Vectores dorados `golden/costing-arithmetic.json` y pruebas de propiedades en `core:domain`.
