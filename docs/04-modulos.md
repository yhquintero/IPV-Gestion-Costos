# 4. Módulos

> Entregable §39‑4 · [Índice](README.md) · Propuesta 🧭: **monolito modular** (un despliegue, fronteras estrictas).

## 4.1 Por qué un monolito modular

| Criterio | Monolito modular | Microservicios |
|---|---|---|
| Integridad transaccional (ficha + estado + auditoría + sync en **una** transacción) | Natural | Sagas, consistencia eventual |
| Operación en un servidor modesto, equipo pequeño | Sí | Costo operativo alto |
| Latencia/ancho de banda en Cuba | Un solo punto de entrada | Más saltos internos |
| Evolución futura | Los módulos pueden extraerse si hace falta | — |

**Reglas de frontera** (verificadas con pruebas de arquitectura: Spring Modulith o ArchUnit):

1. Cada módulo expone un paquete `api` (interfaces y DTO) y oculta su `internal`.
2. Un módulo **solo lee/escribe sus propias tablas**. Para datos ajenos usa la API del dueño o eventos.
3. Notificación "hacia arriba" por **eventos** con *outbox* transaccional (publicación fiable).
4. Excepción documentada: `reporting` lee **vistas publicadas** por otros módulos (solo lectura).
5. Inversión de dependencias para las licencias: `access` define el puerto `EntitlementChecker`; `licensing` lo implementa.

```text
 Capa 3 · Plataforma   platform-admin · commerce · licensing · rates
 Capa 2 · Negocio      catalog · ipv-values · inventory · costing · rules · workflow
                       ipv-control · reporting · notifications · (cuadre, posterior)
 Capa 1 · Núcleo       identity · access · tenancy · audit · sync
 Capa 0 · Compartido   core:domain (Kotlin/JVM)  ·  kernel técnico (outbox, idempotencia, RLS, errores)

 Una capa depende solo de capas inferiores (o de puertos que ella define). Hacia arriba: eventos.
```

## 4.2 Catálogo de módulos

**Derecho de uso** = entitlement de Keygen que habilita el módulo (asignación provisional ⛔ [D‑09](16-decisiones-pendientes.md#d-09)). **Fase** = fase del [roadmap](19-roadmap.md) en que se construye.

| Id | Módulo | Responsabilidades | Datos propios | Depende de | Eventos que publica | Derecho de uso | Fase |
|---|---|---|---|---|---|---|---|
| M‑01 | `identity` | Alta/login, contraseñas (Argon2id), MFA, recuperación, sesiones y *refresh tokens*, bloqueo por intentos. | `users`, `mfa_factors`, `sessions` | access, audit | `UserLockedOut`, `SessionRevoked` | — | 3 |
| M‑02 | `access` | Roles, permisos, asignaciones con alcance; **decisión de autorización**; capacidades para la UI; límites por licencia vía puerto. | `roles`, `permissions`, `role_assignments` | tenancy | `RoleAssigned` | — | 3 |
| M‑03 | `tenancy` | Organizaciones, empresas, sucursales, ajustes por empresa; **tope de empresas/sucursales**. | `organizations`, `companies`, `branches` | — | `CompanyCreated` | `MULTI_COMPANY`, `MULTI_BRANCH` | 3 |
| M‑04 | `catalog` | Categorías, unidades, proveedores, insumos y productos/servicios. | `categories`, `units`, `suppliers`, `raw_materials`, `products` | tenancy | `CatalogChanged` | `IPV_BASIC` | 3 |
| M‑05 | `ipv-values` | Registro de **Valores IPV** con vigencia sin solape e historial. | `ipv_values` | catalog | `IpvValueChanged` | `IPV_BASIC` | 3 |
| M‑06 | `inventory` | Almacenes, libro mayor de movimientos, saldos, conteos. | `warehouses`, `inventory_movements`, `stock_balances`, `inventory_counts` | catalog | `StockBelowMinimum` | `IPV_BASIC` | 3‑6 |
| M‑07 | `costing` | Fichas, versiones, líneas, **cálculo**, instantáneas, ciclo de vida. | `cost_sheets`, `cost_sheet_versions`, `cost_sheet_lines` | catalog, ipv-values, rules, workflow, rates | `CostSheetSubmitted/Validated/Approved/Activated/Replaced/Annulled` | `COST_SHEETS` | 3 |
| M‑08 | `rules` | Conjuntos de reglas versionados, evaluación determinista, excepciones justificadas. | `rule_sets`, `rule_set_versions`, `rule_evaluations`, `rule_overrides` | catalog | `RuleSetPublished` | `IPV_BASIC` (plantillas) · `IPV_ADVANCED` (reglas propias) | 3 |
| M‑09 | `workflow` | Infraestructura de estados y **aprobaciones** configurables; historial de estados. | `approval_policies`, `approval_requests`, `approval_steps`, `status_history` | access | `ApprovalRequested`, `ApprovalDecided` | `IPV_BASIC` | 3 |
| M‑10 | `ipv-control` | Controles IPV, líneas, varianzas, numeración. | `ipv_controls`, `ipv_control_lines`, `document_sequences` | costing, catalog, workflow | `IpvControlValidated`, `IpvControlWithDifferences` | `IPV_BASIC` | 3 |
| M‑11 | `rates` | Proveedores de tasas, planificador, muestras, estado, instantáneas, tasa manual, política por empresa. | `rate_*`, `exchange_rate_*` | audit | `RateUpdated`, `RateBecameStale` | — (siempre) | 8 (puerto y tasa manual en 3) |
| M‑12 | `licensing` | Puerto `LicenseProvider`, adaptador Keygen, licencias por usuario, dispositivos, derechos, archivos offline, *webhooks*. | `licenses`, `license_*`, `keygen_webhook_events` | identity, access, audit | `LicenseIssued/Renewed/Expiring/Suspended/Revoked`, `DeviceActivated` | — | 7 |
| M‑13 | `commerce` | Catálogo de precios, contratos, pagos, recibos; CUP derivado de la tasa. | `price_catalog_items`, `contracts`, `contract_items`, `payments`, `receipts` | rates, licensing | `PaymentConfirmed` | — | 7 |
| M‑14 | `audit` | Eventos de auditoría, sellado por bloques, verificación, consulta. | `audit_events`, `audit_blocks` | — | `AuditBlockSealed` | `ADVANCED_AUDIT` (consulta/exportación avanzada; **el registro siempre está activo**) | 3 |
| M‑15 | `sync` | Registro de cambios, mutaciones idempotentes, conflictos, *bootstrap*, cursores. | `sync_change_log`, `sync_mutations`, `sync_conflicts` | access, todos los módulos con entidades sincronizables (vía `SyncContributor`) | `SyncConflictCreated` | `ANDROID_ACCESS` | 6 |
| M‑16 | `reporting` | Reportes, tablero, exportaciones (PDF/XLSX/CSV). | vistas/caché | vistas de otros módulos | — | `REPORTS` · exportar: `DATA_EXPORT` | 3‑4 |
| M‑17 | `notifications` | Bandeja interna, correo, preferencias, recordatorios (vencimiento de licencia, revisiones pendientes). | `notifications`, `outbox_events` | (consume eventos) | — | — | 3‑7 |
| M‑18 | `platform-admin` | Trastienda del personal de plataforma: organizaciones, precios, contratos, monitor de tasas, operaciones de licencia, acceso *break‑glass* auditado. | (usa tablas de otros por sus API) | commerce, licensing, rates, tenancy, audit | — | — | 7 |
| M‑19 | `cuadre` *(posterior)* | Cuadre diario de caja, venta rápida, finanzas y cierre/reapertura de período con motivo (de `inventario`). | por definir | inventory, rates, audit | `DayClosed`, `DayReopened` | por definir | ⛔ [D‑15](16-decisiones-pendientes.md#d-15) |

## 4.3 Módulo compartido `core:domain` (Kotlin/JVM)

Biblioteca **sin dependencias de framework** usada por el servidor **y** por Android:

| Contenido | Para qué |
|---|---|
| `Money`, `Rate`, `Rounding` | Dinero decimal exacto y política de redondeo única. |
| Motor de cálculo de fichas | Mismos totales en servidor, Android y vista previa web. |
| Intérprete de reglas (DSL cerrado) | Validación determinista; mismo resultado offline y online. |
| Máquinas de estado (ficha, control) | Transiciones válidas en un solo lugar. |
| Evaluador de estado de licencia | Función pura `(archivo, última validación, reloj fiable) → estado` ([doc 7](07-flujo-licencias-keygen.md)). |
| Vectores dorados (JSON) | Casos de prueba compartidos por JVM, Android y web. |

> La web **no** reimplementa el cálculo: pide una vista previa al servidor (necesita conexión de todos modos).

## 4.4 Módulos de la app Android (Gradle)

```text
:app                      composición, navegación, Hilt
:core:domain  (JVM)       ← el mismo módulo del servidor
:core:common              utilidades, resultados, fechas
:core:network             Retrofit/OkHttp, pines, interceptores (gzip, idempotencia)
:core:data                Room + SQLCipher, repositorios, outbox, SyncWorker
:core:security            Keystore, biometría, archivo de máquina, reloj fiable
:core:designsystem        Material 3, componentes accesibles
:feature:auth  :feature:home  :feature:catalog  :feature:ipv  :feature:costing
:feature:control  :feature:inventory  :feature:rates  :feature:license
:feature:sync  :feature:settings                      (+ :feature:cuadre más adelante)
```

## 4.5 Secciones del sitio Web (Next.js)

| Zona | Rutas | Contenido |
|---|---|---|
| **Pública** (SSG, rápida con poco ancho de banda) | `/`, `/precios`, `/prueba`, `/contacto`, `/terminos`, `/privacidad` | Presentación; precios en USD con **equivalente en CUP calculado** y etiqueta de fuente ([doc 10](10-flujo-eltoque-cache.md)). |
| **Panel** | `/app/inicio`, `/app/catalogo`, `/app/valores-ipv`, `/app/fichas`, `/app/controles`, `/app/inventario`, `/app/tasas`, `/app/reportes`, `/app/auditoria`, `/app/usuarios`, `/app/licencias`, `/app/configuracion` | Operación diaria por rol y alcance. |
| **Plataforma** (solo personal autorizado, MFA obligatorio) | `/plataforma/organizaciones`, `/plataforma/precios`, `/plataforma/contratos`, `/plataforma/tasas`, `/plataforma/licencias` | Administración comercial y operativa. |

## 4.6 Eventos de dominio (catálogo mínimo)

| Evento | Publica | Consumen |
|---|---|---|
| `CostSheetSubmitted/Validated/Approved` | costing | notifications (a revisores/aprobadores), audit, reporting |
| `CostSheetActivated` / `Replaced` | costing | ipv-control (avisar controles abiertos), notifications, reporting |
| `CostSheetAnnulled` | costing | ipv-control (bloquear nuevas líneas, marcar abiertos), notifications |
| `IpvValueChanged` | ipv-values | costing (señalar fichas desactualizadas), notifications |
| `RateUpdated` / `RateBecameStale` | rates | reporting, notifications (alerta a administradores) |
| `LicenseExpiring` / `Suspended` / `Revoked` | licensing | notifications, sync (forzar refresco de archivo), audit |
| `PaymentConfirmed` | commerce | licensing (emitir/renovar), notifications |
| `SyncConflictCreated` | sync | notifications |
| `AuditBlockSealed` | audit | operaciones (anclaje externo) |
