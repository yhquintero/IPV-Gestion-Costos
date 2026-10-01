# ASVS L2 (parcial) · Fase 3

No es una certificación. Mapa de controles implementados frente a OWASP ASVS 4.0 nivel 2, **solo lo que esta fase cubre**.

| ASVS | Control | Estado Fase 3 |
|---|---|---|
| V2.1.1 | Longitud de contraseña ≥ 12 recomendada (se acepta lo almacenado; política UI en Fase 4) | Parcial |
| V2.4.1 | Argon2id (m=19456, t=2, p=1) | Hecho |
| V2.2.1 | MFA TOTP opcional; obligatorio a roles privilegiados queda para política de alta de usuarios (Fase 3 enrollment pendiente de UI) | Parcial |
| V2.2.4 | Sin preguntas de seguridad | Hecho |
| V3.3 | Sesiones: access JWT corto EdDSA + refresh opaco rotatorio con *hash* | Hecho |
| V3.5 | Revocación de refresh (`/auth/logout`, reutilización revoca familia) | Hecho |
| V4.1 | Denegar por defecto; autorización por permiso en cada comando | Hecho |
| V4.2.1 | IDOR: consultas acotadas por `organization_id` + RLS; 404 | Hecho (prueba `ApiIT`) |
| V5.1 | Validación de entrada (Bean Validation + CHECKs de BD) | Parcial |
| V6.2 | Sin secretos en clientes; JWT asimétrico de desarrollo efímero, PEM obligatorio en `prod` | Hecho |
| V7.1 | `problem+json` sin trazas (`server.error.include-stacktrace=never`) | Hecho |
| V8.3.4 | Auditoría de login, transiciones de ficha, tasa manual | Hecho |
| V9.1 | TLS en el borde (Caddy, Fase 10); la API asume HTTP detrás del proxy | Diferido |
| V11.1.4 | `Idempotency-Key` en POST/PATCH | Hecho |
| V13.1 | OpenAPI como contrato | Hecho |
| V14.4.3 | Cabeceras de seguridad del panel (CSP) | Fase 4 |

Hallazgos residuales: enrollment MFA en API (severidad media), rate-limit de login (previsto, no cableado al borde), cifrado de campo TOTP (semilla en `bytea` sin envolver — **no usar en producción** hasta Fase 3+ endurecimiento).
