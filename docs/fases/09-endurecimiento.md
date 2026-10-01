# Fase 9 · Pruebas y endurecimiento

| | |
|---|---|
| **Estado** | En curso — harness de aceptación, límites, caos de sync, gitleaks. **Pentest, ZAP, k6 y simulacro de restauración no ejecutados** |
| **Fecha** | 2026‑10‑01 |
| **Gate** | Matriz de criterios **documentados** con evidencia; 0 críticos/altos de herramientas **corridas**. El texto íntegro del §36 del prompt **no está en el repo** ([docs/README](../README.md)) |

## Entregables

| Pieza | Ubicación |
|---|---|
| Matriz de aceptación (diseño, no §36 copiado) | [09-matriz-aceptacion.md](09-matriz-aceptacion.md) |
| `LoginThrottle` + cabeceras API | `core/domain/.../security`, `AuthController`, `ApiSecurityHeadersFilter` |
| Caos de sync (lote, payload, idempotencia, epoch) | `SyncChaosTest` |
| Allowlist SSRF elTOQUE/Keygen | `OutboundAllowlist` |
| gitleaks CI | `.github/workflows/ci-security.yml` · `.gitleaks.toml` |
| k6 (artefacto, no corrido) | `tools/k6/` |
| ZAP (instrucciones, no corrido) | `tools/zap/` |
| ASVS L2 / MASVS L1 | actualizados; residuales abiertos |

## Qué **no** se afirma

- Prueba de penetración externa: ⛔ presupuesto / no contratada.
- ZAP baseline contra staging: no hay staging.
- k6: script listo, no hay *target*.
- Simulacro de restauración (RPO/RTO): Fase 10.
- Playwright/axe en este sandbox: Chromium CDN bloqueado (CI ubuntu sí).
- D-02, D-04, D-05, D-06 siguen abiertas.

## Cómo verificar

```bash
gradle :core:domain:test --tests cu.ipvgc.domain.security.* --tests cu.ipvgc.domain.sync.*
```
