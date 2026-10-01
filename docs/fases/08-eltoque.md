# Fase 8 · elTOQUE (tasas de referencia)

| | |
|---|---|
| **Estado** | En curso — puerto, analizador, MOCK/SEED, planificador; **API live ⛔ D-04** |
| **Fecha** | 2026‑10‑01 |
| **Gate** | Contrato con fixtures; inyección de fallos; lista de términos. **D-04 no respondida** |

## Entregables

| Pieza | Ubicación |
|---|---|
| Puerto `ExchangeRateProvider`, parser, estados, etiquetas | `core/domain/.../rates/` |
| `CachedProvider`, limitador, *backoff*, anomalía (umbral nulo) | idem |
| Fixtures / servidor simulado | `tools/mock-eltoque/` · `SimulatedElToqueHttp` |
| Ingest + *lock* asesor + `GET /rates/history\|status` | `server/app/.../rates/` |
| Flyway V14 | `server/db/migration/V14__rate_provider_state.sql` |
| Android: última tasa conocida si falla la red | `RateRepository` |
| Pantalla plataforma | `web/app/(panel)/plataforma/tasas` |
| CI (nunca llama a la API real) | `.github/workflows/ci-rates.yml` |

## Cumplimiento de términos (13.12)

| Obligación | Estado |
|---|---|
| Una clave por aplicación, solo servidor | Sí (variable; no en Android/JS) |
| Citar a elTOQUE; «no oficial» | `RateLabels` |
| No modificar valores | Parser `BigDecimal` del JSON; sin márgenes |
| No raspar / no desafío Cloudflare | HTML → `INVALID_PAYLOAD` |
| No API pública de tasas | Endpoints autenticados |
| No «tasa oficial» ni patrocinio | `containsForbiddenOfficialClaim` |
| Historial / instantáneas / retransmisión a licenciados | ⛔ **D-04** consulta a `desarrollo@eltoque.com` |
| CAD/MXN/ZELLE/CLA vía API | No se inventan; solo MANUAL o SEED |
| Zona horaria del sello | `source_ts_utc = null`; vejez con `fetched_at` |

## Cómo verificar

```bash
gradle :core:domain:test --tests cu.ipvgc.domain.rates.*
```

Demo: `*@alpha.test` / `Seed-Passw0rd!`. `ELTOQUE_PROVIDER_MODE=SEED`.
