# G-4 · Criterios de salida del piloto (plantilla)

⛔ [D-22](../16-decisiones-pendientes.md#d-22) / [D-30](../16-decisiones-pendientes.md#d-30). **No firmado.** Completar **antes** de abrir el piloto a clientes reales.

| Campo | Valor acordado | Firma |
|---|---|---|
| Presupuesto mensual infra + soporte | | |
| RPO (máx. pérdida de datos) | | |
| RTO (máx. tiempo de recuperación) | | |
| Umbral conflictos / semana (6a) | | |
| Umbral rechazos sync / semana | | |
| % usuarios que trabajan offline (G-0) | | |
| 2 semanas 6a sin pérdida de datos | no ejecutado | |
| Simulacro de restauración (fecha, RTO medido) | no ejecutado | |
| Alertas 401 elTOQUE / 401 login spray / audit verify | no probadas en staging | |
| Revisor técnico independiente (G-2) | pendiente | |
| Dictámenes D-04, D-05, D-06 | ⛔ | |

Hasta que esta hoja esté firmada, `ipvgc.sync.stage` permanece en `A_COUNTS` y no hay clientes reales en offline editable (ADR-0001).
