# ADR‑0005 · Modelo de despliegue e aislamiento

| Campo | Valor |
|---|---|
| **Estado** | **Aceptado (provisional al iniciar Fase 2)** |
| **Fecha** | 2026‑10‑01 |
| **Decide** | Defaults de [D‑06](../16-decisiones-pendientes.md#d-06) y [D‑07](../16-decisiones-pendientes.md#d-07) |
| **Relacionados** | [Doc 2](../02-diagrama-arquitectura.md) · ADR‑0001 |

## Decisión

1. Topología inicial **A: SaaS central** multi‑organización, con la **misma base desplegable por cliente**.
2. Aislamiento: `organization_id NOT NULL` + **RLS forzado** + FK compuestas (I‑01). El rol `app_rw` no tiene `BYPASSRLS`.
3. Hosting y jurisdicción siguen ⛔ pendientes de dictamen; el esquema no asume un proveedor concreto.

## Verificación

Suite de fuga entre organizaciones en Testcontainers (I‑01).
