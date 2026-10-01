# Fase 7 · Keygen, licencias y comercial

| | |
|---|---|
| **Estado** | En curso — puerto, Fake, evaluador por tabla, comercial, webhooks; Cloud ⛔ D-05 |
| **Fecha** | 2026‑10‑01 |
| **Gate** | Evaluador dirigido por tabla (7 estados + orden); conciliación FROM_EXPIRY; pruebas contra proveedor simulado. Keygen real/CE **no** hasta D-05 |

## Entregables

| Pieza | Ubicación |
|---|---|
| Puerto `LicenseProvider` + Fake + Cloud *disabled* | `core/domain/.../license/` |
| Evaluador **por tabla** `EVALUATION_TABLE` | `LicenseEvaluator.kt` + golden `license-states.json` |
| Renovación `FROM_EXPIRY` (D-12 intacto) | `RenewalMath.kt` |
| Precio CUP congelado HALF_UP | `CommercePricing.kt` |
| Políticas versionadas | `deploy/keygen/policies.yaml` · `PolicyCatalog` |
| Flyway V13 (renovaciones, asiento web, catálogo) | `server/db/migration/V13__license_renewal_commerce.sql` |
| API licencias / comercial / webhook | `server/app/.../licensing`, `commerce` · OpenAPI **0.7.0** |
| Pantallas panel y plataforma | `web/app/(panel)/app/licencias`, `plataforma/{precios,contratos,licencias}` |
| CI | `.github/workflows/ci-license.yml` |

## Decisiones que siguen ⛔

| Id | Efecto en este corte |
|---|---|
| **D-05** | `KEYGEN_MODE=CLOUD` arranca `KeygenCloudDisabledProvider` (503). No hay cliente HTTP a `api.keygen.sh`. |
| **D-12** | `renewalBasis = FROM_EXPIRY`. Una renovación tardía puede dejar la licencia vencida; se registra, no se cambia la base. |
| **D-02 / D-04 / D-06** | Sin cambio. |
| **I-18** | Se guarda `keygen_license_id` opaco. **Nunca** la clave de licencia. |
| **S-1** | ECDSA real del machine file pendiente. Fake firma HMAC `FAKE-NOT-A-SECRET`. |

## Cómo verificar

```bash
gradle :core:domain:test --tests cu.ipvgc.domain.license.*
```

Demo: `*@alpha.test` / `Seed-Passw0rd!`.
