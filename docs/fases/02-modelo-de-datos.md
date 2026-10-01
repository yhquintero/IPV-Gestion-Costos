# Fase 2 · Modelo de datos

| | |
|---|---|
| **Estado** | En curso — esqueleto, migraciones, dominio y pruebas escritos |
| **Fecha** | 2026‑10‑01 |
| **Gate** | Invariantes I‑01…I‑20 en verde (Testcontainers); propiedades de dinero; migraciones reproducibles desde cero |

## Entregables

| Pieza | Ubicación |
|---|---|
| `core:domain` (`Money`, tasas, redondeo, ciclo de ficha, vectores dorados) | `core/domain/` |
| Migraciones Flyway V1…V10 | `server/db/migration/` |
| Esqueleto Spring Boot | `server/app/` |
| Semilla 100 % sintética | `tools/seed/` |
| Suite I‑01…I‑20 + reproducibilidad | `server/app/src/test/` |
| CI base | `.github/workflows/ci-core-server.yml` |
| ADR‑0002…0007 | `docs/adr/` |

## Invariantes ↔ prueba

| Id | Prueba |
|---|---|
| I‑01 | `InvariantsIT.I-01 app_rw cannot see or write another organization` |
| I‑02 | `I-02 at most one VIGENTE version per cost sheet` |
| I‑03 | `I-03 version_no unique per sheet` |
| I‑04 | `I-04 vigente ranges do not overlap` |
| I‑05 | `I-05 content is frozen after BORRADOR` |
| I‑06 | `I-06 only legal status transitions` |
| I‑07 | `I-07 four eyes rejects same actor` |
| I‑08 | `I-08 ipv value cannot be physically deleted while referenced` |
| I‑09 | `I-09 control line cannot point at a draft or out-of-period version` |
| I‑10 | `I-10 ipv values do not overlap by subject branch and currency` |
| I‑11 | `I-11 amounts are non-negative and rates positive` + `MoneyTest` / `RateTest` |
| I‑12 | `I-12 rate samples are immutable` |
| I‑13 | `I-13 snapshots are immutable and unique by hash` |
| I‑14 | `I-14 audit is append-only` |
| I‑15 | `I-15 mutations are applied once` |
| I‑16 | `I-16 physical delete is forbidden for app_rw` |
| I‑17 | `I-17 inventory movements are append-only` |
| I‑18 | `I-18 unique keygen id and one active license per user` |
| I‑19 | `I-19 document numbers are unique and monotonic` |
| I‑20 | `I-20 line currency must belong to the company` |

## Defaults adoptados (provisionales)

Documentados en ADR‑0002…0007. El propietario puede reemplazarlos con un ADR posterior. Lo que sigue ⛔ (norma cubana, token elTOQUE, dictamen Keygen/hosting) **no se inventó**.

## Cómo verificar

```bash
# JDK 21 + Docker (Testcontainers)
gradle test
```

Las tasas de prueba se cargan desde `tools/seed/src/main/resources/seed/exchange-rates-test.json`, nunca como constantes de código. El perfil `prod` se niega a arrancar si `is_test = true`.
