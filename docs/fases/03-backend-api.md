# Fase 3 · Backend / API

| | |
|---|---|
| **Estado** | En curso |
| **Fecha** | 2026‑10‑01 |
| **Gate** | Contrato OpenAPI; corte vertical verde; IDOR; `GET /audit/verify`; ASVS L2 parcial |

## Entregables

| Pieza | Ubicación |
|---|---|
| Contrato OpenAPI 3.1 | [`contracts/openapi/ipv-gc.yaml`](../../contracts/openapi/ipv-gc.yaml) |
| Identidad (Argon2id, JWT EdDSA, refresh rotatorio, MFA TOTP) | `server/app/.../identity`, `security` |
| Acceso RBAC | `access/AccessService` |
| Catálogo, valores IPV, fichas (7 estados), Control IPV, tasas manuales | controladores en `server/app` |
| Auditoría + bloques + `GET /audit/verify` | `audit/` |
| `Idempotency-Key`, `If-Match`/`ETag`, `problem+json` | `web/` |
| Notificaciones y reporte básico | `notifications/`, `reporting/` |
| Pruebas API + IDOR | `server/app/src/test/.../api/ApiIT.kt` |
| ASVS L2 (parcial) | [03-asvs-l2.md](03-asvs-l2.md) |

## Corte vertical cubierto

Registrar producto → Valor IPV vigente → borrador de ficha → reglas v1 → enviar (congela tasa + `content_hash`) → validar (cuatro ojos + hash) → aprobar → activar (una VIGENTE) → Control IPV numerado → auditoría verificable.

## 123 pruebas de IPV

Los repos origen **no se copian**. Los comportamientos documentados (redondeo por línea HALF_UP, congelado al salir de BORRADOR, una VIGENTE, anulación con motivo, control ligado a versión) se reproducen como **casos de aceptación** en `ApiIT` y en la suite I‑01…I‑20 de la Fase 2. No se inventa una lista de 123 nombres.

## Cómo verificar

```bash
gradle test
```
