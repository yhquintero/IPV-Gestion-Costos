# Matriz de aceptación (criterios documentados)

El prompt maestro §36 (30 criterios) **no se reproduce** aquí: el [README de docs](../README.md) lo deja para cuando el propietario entregue el texto. Esta tabla cruza **lo que el diseño ya exige** con evidencia en el repo.

Leyenda: **sí** = cubierto por prueba o control versionado · **parcial** · **⛔** no ejecutado / decisión pendiente.

| Id | Criterio (origen) | Evidencia | Estado |
|---|---|---|---|
| A1 | Invariantes I-01…I-20 | `InvariantsIT`, Flyway V2–V14 | sí (CI con JDK+Docker) |
| A2 | Dinero sin Double; HALF_UP por línea | `Money*Test`, `CommercePricingTest` | sí |
| A3 | Semillas de tasa no son constantes; prod rechaza `is_test` | `TestDataGuard`, JSON de semilla | sí |
| A4 | IDOR / aislamiento de organización | `ApiIT` IDOR; RLS | sí |
| A5 | Login uniforme, sin enumerar | `ApiIT` login 401 | sí |
| A6 | Bloqueo de cuenta + *throttle* IP/correo | `register_login_failure`; `LoginThrottle` | sí |
| A7 | MFA TOTP; sin preguntas de seguridad | `AuthService` / `Totp` | parcial (semilla TOTP sin cifrar en reposo) |
| A8 | Refresh rotatorio, reutilización revoca familia | `AuthService.refresh` | sí (ApiIT si cubre) |
| A9 | Auditoría encadenada `GET /audit/verify` | `AuditService` | sí |
| A10 | Ficha 7 estados; una VIGENTE; congelado al enviar | `ApiIT` / `CostSheetService` | sí |
| A11 | Control IPV ligado a versión | módulo ipvcontrol | sí |
| A12 | Sync E-1…E-5 | `SyncScenariosTest` | sí |
| A13 | Caos sync: lote/payload/epoch | `SyncChaosTest` | sí |
| A14 | Licencia: tabla 7.6, Fake, FROM_EXPIRY, I-18 | `LicenseEvaluator*` `FakeLicenseProviderTest` | sí |
| A15 | Keygen Cloud bloqueado | `KeygenCloudDisabledProvider` | sí (D-05) |
| A16 | Tasas: parser, caché, etiquetas, no scrap | `ElToqueParserTest` `RateLabels` | sí |
| A17 | API elTOQUE no se llama en CI | `ELTOQUE_PROVIDER_MODE=SEED` | sí (D-04) |
| A18 | CAD/MXN/ZELLE/CLA no inventados | parser | sí |
| A19 | CSP + cabeceras web | `middleware.ts` `next.config.ts` | sí (ZAP ⛔) |
| A20 | Cabeceras API `nosniff`/`DENY`/`no-store` | `ApiSecurityHeadersFilter` | sí |
| A21 | Sin secretos en APK/JS | `assert-no-secrets.sh`, ci-android | sí |
| A22 | gitleaks | `ci-security.yml` | sí (en GitHub) |
| A23 | SQLCipher + `allowBackup=false` | MASVS lista | sí (instrumentación ⛔ aquí) |
| A24 | Allowlist SSRF | `OutboundAllowlist` | sí |
| A25 | OpenAPI contrato | `ipv-gc.yaml` 0.8.0 | sí |
| A26 | Accesibilidad axe | `web/e2e` | ⛔ este sandbox; CI ubuntu |
| A27 | Carga k6 | `tools/k6` | artefacto; no corrido |
| A28 | ZAP baseline | `tools/zap` | no corrido |
| A29 | Pentest externo | — | ⛔ no contratado |
| A30 | Simulacro de restauración | Fase 10 | ⛔ |

Ningún hallazgo **crítico/alto** de una herramienta **ejecutada en este corte**. Lo no corrido no se marca en verde.
