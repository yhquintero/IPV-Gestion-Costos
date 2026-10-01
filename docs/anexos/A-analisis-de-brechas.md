# Anexo A · Análisis de brechas de los repositorios origen

> Complementa el [análisis arquitectónico](../01-analisis-arquitectonico.md#12-hallazgos-que-más-condicionan-el-diseño) · [Índice](../README.md)

**Fuentes**: `IPV_Fichas-Costos` @ `15faee5` y `inventario` @ `c42fc6f`, leídos el 2026‑10‑01. 🔎 = observado en código/documentación; **n/v** = *no verificado* (no se examinó o no hay evidencia). Las acciones son propuestas (🧭).

**Cómo leer**: *Objetivo* = lo que exige el producto unificado; *Brecha* = distancia entre el mejor de los dos repos y el objetivo.

## A.1 Plataforma y datos

| # | Capacidad | `IPV_Fichas-Costos` 🔎 | `inventario` 🔎 | Objetivo unificado | Brecha | Acción |
|---|---|---|---|---|---|---|
| 1 | Servidor | Python stdlib `http.server` | Python stdlib + `cryptography` | Monolito modular Kotlin/Spring Boot ([doc 17](../17-stack-recomendado.md)) | Reescritura | Portar reglas y pruebas como especificación |
| 2 | Base de datos | SQLite (+SQLCipher opcional) | SQLite; `app_state` = **una fila JSON** | PostgreSQL compartido | Total | Esquema nuevo con Flyway ([doc 3](../03-modelo-er.md)) |
| 3 | Identificadores | Enteros autoincrementales | Enteros (Room) + `remoteId` texto | UUID (v7) generados en cliente o servidor | Total | UUID en todas las tablas sincronizables |
| 4 | Multi‑organización / sucursal | Ninguno (decisión 2026‑09‑29) | Ninguno (almacenes ≠ sucursales) | Organización → Empresa → Sucursal + RLS | Total | Tenancy desde el primer día ([ADR‑0001](../adr/0001-reabrir-offline-y-multisucursal.md)) |
| 5 | Dinero | `Decimal` en TEXT, `ROUND_HALF_UP` | **`Double`** (Room) | `NUMERIC(19,4)` / `BigDecimal`; política de redondeo | Alta (inventario) | Convertir; pruebas de propiedades |
| 6 | Borrado | Papelera + **purga física** | Pantalla de papelera (`TrashScreen`) | Borrado lógico (`deleted_at`); purga solo por política de retención | Media | Soft delete + auditoría; purga restringida ([D‑17](../16-decisiones-pendientes.md#d-17)) |
| 7 | Integridad referencial | `ficha_items.material_id ON DELETE SET NULL` | Estado JSON sin FK | FK estrictas + instantáneas inmutables | Alta | Líneas referencian `ipv_value_id`; sin `SET NULL` |
| 8 | Precios históricos | Un `unit_price` sobrescribible por material | `priceHistory` (sección del estado) | Registros `ipv_values` con vigencia sin solape | Media | Exclusión `btree_gist` por rango de vigencia |

## A.2 Dominio

| # | Capacidad | `IPV_Fichas-Costos` 🔎 | `inventario` 🔎 | Objetivo | Brecha | Acción |
|---|---|---|---|---|---|---|
| 9 | Catálogo (categorías, unidades, proveedores) | `category` y `unit` como texto; `supplier` texto | `category` texto en producto | Catálogos normalizados y configurables | Media | Tablas `categories`, `units`, `suppliers` |
| 10 | Estados de Ficha | Borrador → Aprobada | n/a | BORRADOR → EN REVISIÓN → VALIDADA → APROBADA → VIGENTE → REEMPLAZADA/ANULADA | Alta | Máquina de estados en servidor ([doc 6](../06-flujo-ficha-ipv.md)) |
| 11 | Versionado de Ficha | `UNIQUE(product_id, version)`; editable solo en Borrador | n/a | Versiones inmutables con `parent_version_id` y hash de contenido | Media | Clonar‑para‑editar; congelar tras envío |
| 12 | Instantánea de tasa en Ficha | **No** | El cuadre guarda `cupUsd`/`mxnUsd` manuales por día | `rate_snapshot_id` inmutable por versión | Alta | Módulo de tasas ([doc 10](../10-flujo-eltoque-cache.md)) |
| 13 | Control IPV | `IPV-AAAA-NNNN`, `UNIQUE(ficha_id, periodo)`, Pendiente/Validado/Con diferencias, `snapshot_total` | n/a | Ligado a **versión** de ficha (no 1:1), numeración asignada por servidor | Media | `ipv_controls` + `ipv_control_lines` |
| 14 | Motor de reglas | Validaciones fijas en código | `validate_change` fijo | Reglas configurables, versionadas, con severidad | Total | Conjuntos de reglas JSON + intérprete determinista en `core:domain` |
| 15 | Aprobaciones | Botón "Aprobar" por rol | Cierre/reapertura de día con motivo | Flujo configurable, separación de funciones | Alta | `approval_policies` / `approval_requests` |
| 16 | Inventario | `stock`, `min_stock` en el material; pestaña de inventario | `stockActual` mutable + movimientos con `stockInicial/Final` | **Libro mayor de movimientos** inmutable; saldos derivados | Alta | `inventory_movements` + `stock_balances` |
| 17 | Cuadre, POS, finanzas | No | Sí (cuadre diario, venta rápida, finanzas) | Módulo posterior ([D‑15](../16-decisiones-pendientes.md#d-15)) | — | Diseñado ([doc 4](../04-modulos.md)), fuera del MVP |
| 18 | Almacenes | No | Sí (`docs/ALMACENES.md`) | Almacenes por sucursal | Media | `warehouses` |
| 19 | Reportes / exportación | n/v | CSV y PDF en cliente (`ExportManager`) | Reportes en servidor + exportación gobernada por `DATA_EXPORT` | Media | Módulo `reporting` |
| 20 | Dashboard | Tarjetas en la web | `HomeScreen` / analítica | KPIs por empresa/sucursal | Media | Vistas SQL + caché |
| 21 | Notificaciones | Correo SMTP (`email_notifications.py`) | Alertas locales (stock bajo) | Bandeja interna + correo; push ⛔ | Media | Outbox transaccional ([D‑20](../16-decisiones-pendientes.md#d-20)) |

## A.3 Identidad, seguridad y auditoría

| # | Capacidad | `IPV_Fichas-Costos` 🔎 | `inventario` 🔎 | Objetivo | Brecha | Acción |
|---|---|---|---|---|---|---|
| 22 | Hash de contraseñas | PBKDF2, 310 000 it. | PBKDF2, 210 000 it. | Argon2id (parámetros OWASP vigentes) | Media | Migrar al crear cuentas (sin datos legados) |
| 23 | MFA | TOTP + códigos de recuperación | TOTP + **pregunta de seguridad** | TOTP obligatorio a roles privilegiados + recuperación sin preguntas | Media | Eliminar preguntas de seguridad |
| 24 | Sesiones | JWT HS256 propio + tabla de sesiones | Cookie de sesión | JWT asimétrico corto + refresh rotatorio (Android) / BFF con cookie (web) | Media | [Doc 11](../11-estrategia-seguridad.md) |
| 25 | Autorización | Módulo × {ver, editar, costos} | Roles fijos × sección | RBAC + ABAC con alcance org/empresa/sucursal | Media | `role_assignments` con alcance |
| 26 | Auditoría | Cadena HMAC (`prev_hash`, `hash`); sin entidad/antes/después/motivo | `audit(ts,user,action,details,ip)` | Eventos completos + bloques firmados + anclaje externo | Alta | [Doc 12](../12-estrategia-auditoria.md) |
| 27 | Copias de seguridad | `sqlite_backup`, réplica y S3 (SigV4) | Respaldos Fernet manuales/automáticos | WAL + base backups cifrados fuera del servidor, con pruebas de restauración | Media | [Doc 17](../17-stack-recomendado.md) |
| 28 | Cifrado de BD | SQLCipher opcional | Fernet solo en backups | PostgreSQL con disco cifrado + cifrado por campo para secretos | Media | [Doc 11](../11-estrategia-seguridad.md) |

## A.4 Licenciamiento

| # | Capacidad | `IPV_Fichas-Costos` 🔎 | `inventario` 🔎 | Objetivo | Brecha | Acción |
|---|---|---|---|---|---|---|
| 29 | Esquema | ECDSA P‑256 propio (`IPV1.…`, planes 1S/1M/3M/6M/1A/2A, códigos de dispositivo) | HMAC‑SHA256 con secreto **en el servidor** | Keygen: políticas, máquinas, entitlements, archivos offline firmados | Total | Puerto `LicenseProvider` ([doc 7](../07-flujo-licencias-keygen.md)) |
| 30 | Aplicación en servidor | Bloqueo inicial hasta activar | Comprueba la licencia en cada `/api/*` (HTTP 402 `LICENSE_REQUIRED`), pero es **una licencia global por servidor** (`max_users`, `max_devices`), no por usuario | Licencia **por usuario** + entitlements validados en cada petición protegida | Media | Middleware con caché corta |
| 31 | Aplicación en Android | `License.kt` verifica ECDSA | **`valid=true` cacheado** | Verificación criptográfica del archivo de máquina + reloj fiable | Alta | Evaluador puro en `core:domain` |
| 32 | Prueba | n/v | Licencia de prueba automática | `IPV-TRIAL-7D` de pago (25 USD) | Media | Política Keygen dedicada |

## A.5 Clientes y sincronización

| # | Capacidad | `IPV_Fichas-Costos` 🔎 | `inventario` 🔎 | Objetivo | Brecha | Acción |
|---|---|---|---|---|---|---|
| 33 | Web | JS vanilla (~6 000 l. entre `app.js`, `enterprise.js`, `ux.js`, CSS) | JS vanilla (~4 200 l.) | Next.js + React + TypeScript, accesible (WCAG 2.2 AA) | Reescritura | [Doc 17](../17-stack-recomendado.md) |
| 34 | Android – arquitectura | Monolito de un archivo, sin dependencias | Compose + Hilt + Room + WorkManager | Multimódulo MVVM/UDF | Media | Reusar esqueleto de `inventario` |
| 35 | Android – seguridad | Keystore AES‑GCM, pinning TOFU, biometría | `EncryptedSharedPreferences` (**obsoleta**), biometría, CA de usuario | Keystore directo, SQLCipher, pines con respaldo/caducidad, sin CA de usuario en *release* | Media | [Doc 11](../11-estrategia-seguridad.md) |
| 36 | Offline | Ninguno (online‑only) | Caché + sync por **reemplazo del estado completo** | Outbox idempotente, cursor de cambios, conflictos por entidad | Total | [Doc 9](../09-flujo-android-offline-sync.md) |
| 37 | Detección de cambios | n/a | Observa Room; consulta `/api/state/version` cada 15 s | Sync por cursor y WorkManager; sin sondeo continuo | Media | Reducir consumo de datos |

## A.6 Calidad y operación

| # | Capacidad | `IPV_Fichas-Costos` 🔎 | `inventario` 🔎 | Objetivo | Brecha | Acción |
|---|---|---|---|---|---|---|
| 38 | Pruebas | 123 OK (3 omitidas) + Playwright | 14 JS OK + 1 clase Android | Pirámide completa + escenarios de sync ([doc 19](../19-roadmap.md)) | Media | Convertir en criterios de aceptación |
| 39 | CI | Ruff, Bandit, pip‑audit, ZAP, Docker, Android | n/v | Detekt, Lint, OWASP dep‑check/Trivy, gitleaks, SBOM, ZAP, E2E | Media | Mantener la disciplina, cambiar herramientas |
| 40 | Despliegue | Docker (solo lectura, `cap_drop ALL`), CA local | Caddy + Let's Encrypt + systemd | Compose + Caddy (TLS público) + secretos fuera del repo | Baja | Reutilizar endurecimiento de contenedores |
| 41 | Contrato de API | `docs/openapi.yaml` | n/v | OpenAPI 3.1 como fuente de verdad + clientes generados | Media | [Doc 17](../17-stack-recomendado.md) |
| 42 | i18n / accesibilidad | Solo español; sin pruebas de accesibilidad (según el estudio previo de IPV) | Solo español | ES (es‑CU) + WCAG 2.2 AA | Media | [D‑21](../16-decisiones-pendientes.md#d-21) |
