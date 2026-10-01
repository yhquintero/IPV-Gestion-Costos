# ADR‑0003 · Proveedor de licencias (puerto, no Cloud sin dictamen)

| Campo | Valor |
|---|---|
| **Estado** | **Aceptado (provisional al iniciar Fase 2)** |
| **Fecha** | 2026‑10‑01 |
| **Decide** | Default de [D‑05](../16-decisiones-pendientes.md#d-05) |
| **Relacionados** | [Doc 7](../07-flujo-licencias-keygen.md) · [R‑01](../14-riesgos.md) |

## Contexto

Keygen Cloud se rige por ley de Texas y Cuba está bajo embargo de EE. UU. No hay dictamen legal. El esquema de datos de licencias no puede esperar a ese dictamen.

## Decisión

1. El producto habla con un puerto `LicenseProvider`; Cloud, CE o un proveedor propio son adaptadores.
2. **No se contrata Keygen Cloud** hasta dictamen escrito.
3. El modelo de datos (`licenses`, `license_devices`, `license_entitlements`, `keygen_webhook_events`) se crea ya, sin secretos ni claves de licencia persistidas.

## Consecuencias

- Fase 7 implementa el adaptador **Fake** y deja Cloud/CE como `KeygenCloudDisabledProvider` hasta dictamen.
- Spike S‑1 (CE local) sigue pendiente del propietario.
