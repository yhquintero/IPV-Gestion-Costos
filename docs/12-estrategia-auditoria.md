# 12. Estrategia de auditoría

> Entregable §39‑12 · [Índice](README.md) · Prioridad #2 (trazabilidad). Seguridad: [doc 11](11-estrategia-seguridad.md) · Modelo: [doc 3](03-modelo-er.md#336-transversales-auditoría-sincronización-y-notificaciones).

## 12.1 Objetivos

1. **Reconstruir** quién hizo qué, cuándo, desde dónde, sobre qué entidad, con qué valor anterior/nuevo y por qué.
2. **Detectar** cualquier alteración posterior de los registros (evidencia de manipulación).
3. **No interferir** con el rendimiento ni con el derecho a la protección de datos.

La auditoría **siempre está activa**; el derecho `ADVANCED_AUDIT` solo habilita la consulta/exportación avanzada, no el registro.

## 12.2 Qué se audita

Taxonomía `DOMINIO.ENTIDAD.ACCIÓN` (ejemplos):

| Dominio | Eventos | ¿Motivo obligatorio? |
|---|---|---|
| `AUTH` | `LOGIN.SUCCESS`, `LOGIN.FAILED`, `MFA.ENROLL`, `PASSWORD.CHANGE`, `SESSION.REVOKE`, `ACCOUNT.LOCKED` | No |
| `ACCESS` | `ROLE.ASSIGN`, `ROLE.REVOKE`, `PERMISSION.CHANGE` | **Sí** si el rol es privilegiado |
| `TENANCY` | `ORG/COMPANY/BRANCH.CREATE/UPDATE/DISABLE` | No |
| `CATALOG`, `IPV_VALUE` | `CREATE/UPDATE/DELETE_SOFT` | Sí al **borrar** |
| `COSTING` | `VERSION.CREATE/EDIT/SUBMIT/RETURN/VALIDATE/APPROVE/ACTIVATE/REPLACE/ANNUL` | **Sí** en devolver y anular |
| `RULES` | `RULESET.PUBLISH`, `OVERRIDE.GRANT` | **Sí** en excepciones |
| `IPV_CONTROL` | `CREATE/LINE.ADD/VALIDATE/CLOSE` | No |
| `INVENTORY` | `MOVEMENT.ADD`, `COUNT.ADD`, `ADJUSTMENT` | Sí en ajustes |
| `RATE` | `MANUAL.SET`, `ANOMALY.FLAGGED`, `PROVIDER.PAUSED` | **Sí** en tasa manual |
| `LICENSE` | `ISSUE`, `ACTIVATE`, `RENEW`, `SUSPEND`, `REINSTATE`, `REVOKE`, `DEVICE.DEACTIVATE` | **Sí** en suspender/revocar |
| `COMMERCE` | `CONTRACT.*`, `PAYMENT.*`, `PRICE.CHANGE` | Sí al anular o cambiar precio |
| `SYNC` | `MUTATION.APPLIED/CONFLICT/REJECTED`, `RESYNC` | No |
| `DATA` | `EXPORT`, `AUDIT.VERIFY` | **Sí** (propósito) en exportar costos |
| `ADMIN` | `BREAK_GLASS.ACCESS`, `BACKUP.RESTORE` | **Sí** |

Los **accesos denegados** también se registran (`result = DENIED`).

## 12.3 Esquema del evento

| Campo | Descripción |
|---|---|
| `id`, `seq` | UUID v7 y secuencia monótona por organización |
| `organization_id`, `company_id`, `branch_id` | Alcance |
| `occurred_at` | Hora **del servidor** (UTC) |
| `actor_type`, `actor_id`, `actor_roles` | `USER`, `SYSTEM`, `SERVICE`, `PROVIDER_WEBHOOK`, `JOB`; roles vigentes en ese instante |
| `action` | Taxonomía de §12.2 |
| `entity_type`, `entity_id` | Entidad afectada |
| `before`, `after`, `diff` | **Solo campos permitidos** por entidad (lista blanca); **nunca** contraseñas, tokens, claves ni semillas |
| `reason` | Texto del motivo (obligatorio según §12.2) |
| `result` | `SUCCESS`, `DENIED`, `FAILED` |
| `ip`, `user_agent`, `device_id`, `session_id` | Contexto; **IP según política de privacidad** |
| `request_id`, `mutation_id`, `source` | Trazabilidad con peticiones y sync; origen `WEB`, `ANDROID`, `API`, `JOB` |
| `client_time` | Hora declarada por el dispositivo (informativa) |
| `event_hash`, `block_id` | Integridad (§12.5) |

## 12.4 Escritura

- El evento se inserta **en la misma transacción** que el cambio de negocio: o ambos existen o ninguno.
- El rol de la aplicación solo puede **insertar** (sin `UPDATE`/`DELETE`/`TRUNCATE`); *triggers* lo refuerzan ([I‑14](03-modelo-er.md#34-invariantes-y-su-aplicación)).
- Los eventos de **trabajos y *webhooks*** usan el mismo camino con `actor_type` correspondiente.
- La tabla `status_history` (línea de tiempo de la UI) **no sustituye** la auditoría: es una vista de conveniencia.

## 12.5 Integridad: *hash* por evento y bloques firmados

Se evoluciona la **cadena HMAC** de IPV (que protege el orden pero no guarda entidad/antes/después/motivo) hacia un esquema verificable por terceros:

```text
event_hash   = SHA-256( JCS(evento sin event_hash ni block_id) )          # JCS = RFC 8785 (JSON canónico)

cada N eventos o T segundos, por organización (un solo "sellador" líder):
  merkle_root = raíz Merkle de los event_hash en orden de seq
  block_hash  = SHA-256( prev_block_hash ‖ merkle_root ‖ from_seq ‖ to_seq ‖ sealed_at )
  signature   = FIRMA( clave_de_auditoría[key_version], block_hash )       # Ed25519 o ECDSA P-256
```

- Insertar un evento **no requiere bloqueo global** (el sellado es asíncrono y de breve retraso).
- La **firma asimétrica** permite que un auditor verifique con la **clave pública** sin conocer secretos (con HMAC haría falta compartir la clave).
- `key_version` permite **rotar** la clave de firma sin invalidar el pasado.
- **Anclaje externo**: al menos una vez al día se exporta el último `block_hash` + firma a un almacenamiento fuera del servidor (y, opcional, se envía al administrador/auditor de la organización o a un servicio de sellado de tiempo ⛔ [D‑22](16-decisiones-pendientes.md#d-22)). Así, aunque alguien con acceso total reescriba la base, **no puede rehacer el pasado anclado**.

## 12.6 Verificación

- `GET /audit/verify?from=&to=` recalcula hashes, raíces Merkle, enlaces `prev_block_hash` y firmas, e informa **el primer punto de ruptura**.
- **Verificador sin conexión** (`tools/audit-verify`): necesita solo el paquete exportado y la clave pública.
- Trabajo programado diario + alerta inmediata ante ruptura (`AUDIT.INTEGRITY.FAILED`).
- Un fallo de verificación **congela** operaciones sensibles hasta revisión.

## 12.7 Acceso, privacidad y retención

- Consulta con filtros (actor, entidad, acción, rango); respeta el alcance del usuario; los costos dentro de `before/after` solo se muestran con `costs:view`.
- **IP "donde sea lícito"**: modo configurable (completa, truncada, *hash* con sal, desactivada) — ⛔ [D‑17](16-decisiones-pendientes.md#d-17).
- **Supresión de datos personales**: la auditoría guarda `actor_id` (seudónimo); el borrado/anonimización ocurre en `users`, sin romper la cadena.
- **Retención**: ⛔ [D‑17](16-decisiones-pendientes.md#d-17) (no se inventa un plazo legal). Técnicamente: particiones mensuales, archivo en frío firmado, purga solo por trabajo de retención auditado.
- Exportación con **paquete de evidencia** (eventos + bloques + firmas + clave pública).

## 12.8 Android y sincronización

- Las mutaciones de negocio hechas offline se auditan **cuando el servidor las aplica**, con `device_id`, `client_time`, hora del servidor y `mutation_id`; los rechazos y conflictos también (`SYNC.*`).
- Las señales **locales de seguridad** (reloj inconsistente, fallo de verificación de licencia, intentos de desbloqueo) se suben como `client_security_events` y se registran como *"reportado por el dispositivo, no verificable"*.

## 12.9 Rendimiento

Tabla de solo anexado particionada por mes; índices `(organization_id, occurred_at)`, `(entity_type, entity_id)`, `(actor_id, occurred_at)`; sellado en lotes; consultas pesadas contra réplica de lectura cuando exista.

## 12.10 Pruebas

| Prueba | Objetivo |
|---|---|
| Atomicidad | Si el cambio falla, no queda evento; si el evento falla, no se aplica el cambio |
| Inmutabilidad | `UPDATE/DELETE/TRUNCATE` rechazados para el rol de la app |
| Manipulación | Alterar un evento/bloque y comprobar que la verificación lo detecta |
| Rotación de clave | Verificación correcta a través de cambios de `key_version` |
| Privacidad | Ningún secreto en `before/after`; modos de IP |
| Rendimiento | Inserción sostenida sin bloqueo global |

## 12.11 Pendientes

[D‑17](16-decisiones-pendientes.md#d-17) (retención, IP, marco legal) · [D‑22](16-decisiones-pendientes.md#d-22) (anclaje, responsables) · algoritmo de firma final (Ed25519 vs ECDSA) en el spike S‑7.
