# 3. Modelo entidad‑relación

> Entregable §39‑3 · [Índice](README.md) · Modelo **lógico** para PostgreSQL (🧭 propuesta). Las migraciones reales (Flyway) se escriben en la Fase 2, tras validar este diseño.

## 3.1 Convenciones

**Columnas estándar** de toda tabla de negocio sincronizable (exigidas por el prompt: UUID, `createdAt`, `updatedAt`, `version`, `deletedAt`, `lastSyncedAt`, borrado lógico):

| Columna | Tipo | Regla |
|---|---|---|
| `id` | `uuid` PK | UUID v7; lo genera el cliente (offline) o el servidor. |
| `organization_id` | `uuid NOT NULL` | **Clave de tenencia**; RLS la usa en cada consulta. |
| `created_at` / `updated_at` | `timestamptz` | Hora **del servidor** al persistir. |
| `origin_created_at` | `timestamptz NULL` | Hora que *declara* el dispositivo (informativa, no confiable). |
| `version` | `bigint NOT NULL DEFAULT 1` | La incrementa el servidor en cada cambio; base de la concurrencia optimista (`ETag`/`If-Match`) y del sync. |
| `deleted_at` | `timestamptz NULL` | **Borrado lógico**. Los únicos parciales incluyen `WHERE deleted_at IS NULL`. |
| `created_by` / `updated_by` | `uuid` | Usuario autor. |
| `origin_device_id` | `uuid NULL` | Dispositivo de origen. |
| `last_synced_at` | — | **Solo en Room** (columna local por fila, junto con `sync_state`); el servidor registra `devices.last_sync_at`. |

**Tipos y precisión** (🧭, política exacta en [D‑25](16-decisiones-pendientes.md#d-25)): dinero `NUMERIC(19,4)`; cantidades `NUMERIC(19,6)`; tasas `NUMERIC(18,6)`; porcentajes `NUMERIC(9,6)`; monedas `CHAR(3)`; estados como `text` + `CHECK` (no enums nativos, más fáciles de migrar). Nunca `float`/`double`.

**Tiempo**: todo en UTC; la *fecha de negocio* (`business_date`) se deriva de la zona horaria de la empresa.

## 3.2 Glosario ES-EN

| Español (UI y documentos) | Inglés (tablas y código) |
|---|---|
| Organización (cliente) · Empresa · Sucursal | `organization` (tenant) · `company` · `branch` |
| Insumo / materia prima | `raw_material` |
| Producto o servicio | `product` (`kind = PRODUCT \| SERVICE`) |
| **Valor IPV** (precio/valor registrado y vigente) | `ipv_value` |
| Ficha de Costo · versión · línea | `cost_sheet` · `cost_sheet_version` · `cost_sheet_line` |
| **Control IPV** · línea de control | `ipv_control` · `ipv_control_line` |
| Tasa de referencia · instantánea de tasas | `exchange_rate_sample` · `rate_snapshot` |
| Licencia · dispositivo · derecho de uso | `license` · `device` · `entitlement` |
| Movimiento de inventario | `inventory_movement` |
| Rendimiento (raciones, copas, comensales) | `yield` |

> Los dos sentidos de "IPV" se separan a propósito ([D‑01](16-decisiones-pendientes.md#d-01)).

## 3.3 Diagramas

### 3.3.1 Tenencia, identidad y acceso

```mermaid
erDiagram
    ORGANIZATIONS ||--o{ COMPANIES : "posee"
    COMPANIES ||--o{ BRANCHES : "tiene"
    ORGANIZATIONS ||--o{ USERS : "agrupa"
    USERS ||--o{ ROLE_ASSIGNMENTS : "recibe"
    ROLES ||--o{ ROLE_ASSIGNMENTS : "se asigna en"
    ROLES ||--o{ ROLE_PERMISSIONS : "incluye"
    PERMISSIONS ||--o{ ROLE_PERMISSIONS : "pertenece a"
    USERS ||--o{ MFA_FACTORS : "registra"
    USERS ||--o{ DEVICES : "usa"
    USERS ||--o{ SESSIONS : "abre"
    DEVICES ||--o{ SESSIONS : "origina"

    ORGANIZATIONS {
        uuid id PK
        text name
        text status
        timestamptz deleted_at
    }
    COMPANIES {
        uuid id PK
        uuid organization_id FK
        text name
        text tax_id
        text base_currency
        text timezone
        jsonb settings
    }
    BRANCHES {
        uuid id PK
        uuid company_id FK
        text code
        text name
        bool active
    }
    USERS {
        uuid id PK
        uuid organization_id FK
        citext email
        text password_hash
        text status
        bool mfa_enabled
        timestamptz last_login_at
    }
    ROLES {
        uuid id PK
        uuid organization_id FK "null = rol de sistema"
        text code
        bool is_system
    }
    PERMISSIONS {
        text code PK
        text description
    }
    ROLE_PERMISSIONS {
        uuid role_id FK
        text permission_code FK
    }
    ROLE_ASSIGNMENTS {
        uuid id PK
        uuid user_id FK
        uuid role_id FK
        text scope_type "ORG, COMPANY o BRANCH"
        uuid scope_id
        timestamptz valid_to
    }
    MFA_FACTORS {
        uuid id PK
        uuid user_id FK
        text type "TOTP o WEBAUTHN"
        bytea secret_enc
    }
    DEVICES {
        uuid id PK
        uuid user_id FK
        text platform "ANDROID o WEB"
        text installation_hash
        text status
        timestamptz last_sync_at
    }
    SESSIONS {
        uuid id PK
        uuid user_id FK
        uuid device_id FK
        text refresh_hash
        uuid family_id
        timestamptz revoked_at
    }
```

### 3.3.2 Catálogo, valores IPV e inventario

```mermaid
erDiagram
    COMPANIES ||--o{ CATEGORIES : "define"
    CATEGORIES ||--o{ RAW_MATERIALS : "clasifica"
    CATEGORIES ||--o{ PRODUCTS : "clasifica"
    UNITS ||--o{ RAW_MATERIALS : "unidad base"
    SUPPLIERS ||--o{ IPV_VALUES : "provee"
    RAW_MATERIALS ||--o{ IPV_VALUES : "se valora en"
    PRODUCTS ||--o{ IPV_VALUES : "se valora en"
    BRANCHES ||--o{ WAREHOUSES : "tiene"
    WAREHOUSES ||--o{ INVENTORY_MOVEMENTS : "registra"
    RAW_MATERIALS ||--o{ INVENTORY_MOVEMENTS : "mueve"
    WAREHOUSES ||--o{ STOCK_BALANCES : "resume"
    RAW_MATERIALS ||--o{ STOCK_BALANCES : "resume"

    CATEGORIES {
        uuid id PK
        uuid company_id FK
        text code
        text name
        text kind "BEBIDAS, COMIDAS, PRODUCTOS, SERVICIOS o CUSTOM"
        uuid parent_id FK
    }
    UNITS {
        uuid id PK
        text code
        text dimension "MASA, VOLUMEN, CONTEO"
        numeric to_base_factor
    }
    SUPPLIERS {
        uuid id PK
        uuid company_id FK
        text name
        text tax_id
    }
    RAW_MATERIALS {
        uuid id PK
        uuid company_id FK
        uuid category_id FK
        uuid base_unit_id FK
        text code
        text name
        numeric min_stock
        bool active
    }
    PRODUCTS {
        uuid id PK
        uuid company_id FK
        uuid category_id FK
        text kind "PRODUCT o SERVICE"
        text code
        text name
        bool active
    }
    IPV_VALUES {
        uuid id PK
        uuid company_id FK
        uuid branch_id FK
        uuid raw_material_id FK
        uuid product_id FK
        uuid supplier_id FK
        char currency
        numeric unit_price
        date valid_from
        date valid_to
        text source_ref
    }
    WAREHOUSES {
        uuid id PK
        uuid branch_id FK
        text name
    }
    INVENTORY_MOVEMENTS {
        uuid id PK
        uuid warehouse_id FK
        uuid raw_material_id FK
        text type "IN, OUT, ADJUST, TRANSFER o COUNT"
        numeric quantity
        timestamptz occurred_at
        date business_date
        uuid mutation_id
    }
    STOCK_BALANCES {
        uuid warehouse_id FK
        uuid raw_material_id FK
        numeric quantity
        timestamptz as_of
    }
```

### 3.3.3 Fichas de Costo, reglas y aprobaciones

```mermaid
erDiagram
    PRODUCTS ||--o{ COST_SHEETS : "tiene ficha"
    COST_SHEETS ||--|{ COST_SHEET_VERSIONS : "versiona"
    COST_SHEET_VERSIONS ||--|{ COST_SHEET_LINES : "compone"
    COST_SHEET_LINES }o--o| IPV_VALUES : "usa valor"
    COST_SHEET_VERSIONS }o--o| RATE_SNAPSHOTS : "congela tasa"
    COST_SHEET_VERSIONS }o--o| RULE_SET_VERSIONS : "validada con"
    COST_SHEET_VERSIONS ||--o{ RULE_EVALUATIONS : "evalua"
    RULE_SETS ||--|{ RULE_SET_VERSIONS : "versiona"
    RULE_EVALUATIONS ||--o{ RULE_OVERRIDES : "justifica"
    APPROVAL_POLICIES ||--o{ APPROVAL_REQUESTS : "rige"
    APPROVAL_REQUESTS ||--|{ APPROVAL_STEPS : "pasos"
    COST_SHEET_VERSIONS }o--o| COST_SHEET_VERSIONS : "deriva de"

    COST_SHEETS {
        uuid id PK
        uuid company_id FK
        uuid branch_id FK "null = todas"
        uuid product_id FK
        text code
    }
    COST_SHEET_VERSIONS {
        uuid id PK
        uuid cost_sheet_id FK
        int version_no
        text status "BORRADOR a ANULADA"
        uuid parent_version_id FK
        date valid_from
        date valid_to
        numeric yield_qty
        char calc_currency
        numeric total_cost
        numeric unit_cost
        uuid rate_snapshot_id FK
        uuid rule_set_version_id FK
        bytea content_hash
        text annul_reason
    }
    COST_SHEET_LINES {
        uuid id PK
        uuid version_id FK
        int line_no
        text line_type "MATERIAL, SUBFICHA, MANO_OBRA, INDIRECTO u OTRO"
        uuid raw_material_id FK
        uuid sub_version_id FK
        uuid ipv_value_id FK
        numeric quantity
        numeric waste_pct
        numeric unit_cost_snapshot
        char unit_currency
        numeric rate_to_calc
        numeric line_cost
    }
    RULE_SETS {
        uuid id PK
        uuid company_id FK
        uuid category_id FK
        text name
    }
    RULE_SET_VERSIONS {
        uuid id PK
        uuid rule_set_id FK
        int version_no
        jsonb definition
        bytea checksum
        date effective_from
    }
    RULE_EVALUATIONS {
        uuid id PK
        uuid version_id FK
        uuid rule_set_version_id FK
        jsonb results
        timestamptz evaluated_at
    }
    RULE_OVERRIDES {
        uuid id PK
        uuid evaluation_id FK
        text rule_id
        text justification
        uuid approved_by
    }
    APPROVAL_POLICIES {
        uuid id PK
        uuid company_id FK
        text entity_type
        jsonb steps
        bool four_eyes
    }
    APPROVAL_REQUESTS {
        uuid id PK
        text entity_type "polimorfico"
        uuid entity_id
        uuid requested_by
        text status
    }
    APPROVAL_STEPS {
        uuid id PK
        uuid request_id FK
        int step_no
        text role_code
        uuid decided_by
        text decision
        text comment
    }
```

### 3.3.4 Control IPV

```mermaid
erDiagram
    BRANCHES ||--o{ IPV_CONTROLS : "controla"
    IPV_CONTROLS ||--|{ IPV_CONTROL_LINES : "contiene"
    IPV_CONTROL_LINES }o--|| COST_SHEET_VERSIONS : "contra version"
    IPV_CONTROL_LINES }o--|| PRODUCTS : "producto"
    COMPANIES ||--o{ DOCUMENT_SEQUENCES : "numera"

    IPV_CONTROLS {
        uuid id PK
        uuid branch_id FK
        text control_no "asignado por el servidor"
        text mode "CONSISTENCIA, DERIVA_COSTOS o CONSUMO"
        date period_start
        date period_end
        text status "PENDIENTE, EN_PROCESO, VALIDADO o CON_DIFERENCIAS"
        timestamptz closed_at
    }
    IPV_CONTROL_LINES {
        uuid id PK
        uuid control_id FK
        uuid product_id FK
        uuid cost_sheet_version_id FK
        numeric expected_qty
        numeric expected_unit_cost
        numeric observed_qty
        numeric observed_unit_cost
        text observation_source "IPV_VALUES, INVENTORY_COUNT o MANUAL"
        numeric variance_value
        numeric variance_pct
        text reason_code
    }
    DOCUMENT_SEQUENCES {
        uuid company_id FK
        text doc_type
        int year
        bigint last_number
    }
    STATUS_HISTORY {
        uuid id PK
        text entity_type "polimorfico"
        uuid entity_id
        text from_status
        text to_status
        uuid actor_id
        text reason
        timestamptz at
    }
```

### 3.3.5 Tasas, comercial y licencias

```mermaid
erDiagram
    RATE_INSTRUMENTS ||--o{ EXCHANGE_RATE_SAMPLES : "tiene muestras"
    RATE_PROVIDER_RUNS ||--o{ EXCHANGE_RATE_SAMPLES : "produce"
    RATE_SNAPSHOTS ||--|{ RATE_SNAPSHOT_ITEMS : "agrupa"
    RATE_SNAPSHOT_ITEMS }o--|| EXCHANGE_RATE_SAMPLES : "congela"
    COMPANIES ||--o| RATE_POLICIES : "configura"
    ORGANIZATIONS ||--o{ CONTRACTS : "contrata"
    CONTRACTS ||--|{ CONTRACT_ITEMS : "incluye"
    PRICE_CATALOG_ITEMS ||--o{ CONTRACT_ITEMS : "fija precio"
    CONTRACT_ITEMS }o--o| EXCHANGE_RATE_SAMPLES : "tasa usada"
    CONTRACTS ||--o{ PAYMENTS : "recibe"
    PAYMENTS ||--o| RECEIPTS : "emite"
    CONTRACT_ITEMS ||--o{ LICENSES : "origina"
    USERS ||--o{ LICENSES : "titular"
    LICENSES ||--o{ LICENSE_DEVICES : "activa"
    DEVICES ||--o{ LICENSE_DEVICES : "ocupa"
    LICENSES ||--o{ LICENSE_ENTITLEMENTS : "otorga"
    LICENSES ||--o{ LICENSE_EVENTS : "registra"

    RATE_INSTRUMENTS {
        text code PK "USD, EUR, MLC, CAD, MXN, ZELLE, CLA"
        text display_name
        text kind
        bool enabled
    }
    EXCHANGE_RATE_SAMPLES {
        uuid id PK
        text instrument_code FK
        numeric value
        text source "ELTOQUE_API, MANUAL o SEED_TEST"
        uuid provider_run_id FK
        jsonb source_ts_raw
        timestamptz source_ts_utc
        timestamptz fetched_at
        bytea payload_hash
        bool is_test
    }
    RATE_PROVIDER_RUNS {
        uuid id PK
        text provider
        text outcome
        int http_status
        int latency_ms
        int ratelimit_remaining
        int retry_after_s
        timestamptz started_at
    }
    RATE_SNAPSHOTS {
        uuid id PK
        timestamptz captured_at
        bytea content_hash
        text status_at_capture
    }
    RATE_SNAPSHOT_ITEMS {
        uuid snapshot_id FK
        text instrument_code FK
        uuid sample_id FK
        numeric value
    }
    RATE_POLICIES {
        uuid company_id FK
        text primary_instrument
        jsonb source_priority
        int max_stale_minutes
        bool allow_manual_override
    }
    PRICE_CATALOG_ITEMS {
        uuid id PK
        text policy_code "IPV-MENSUAL etc."
        text kind "LICENSE, DEVELOPMENT o SERVICE"
        int duration_days
        numeric price_usd
        bool active
        date valid_from
    }
    CONTRACTS {
        uuid id PK
        uuid organization_id FK
        text number
        text type
        text status
        numeric total_usd
    }
    CONTRACT_ITEMS {
        uuid id PK
        uuid contract_id FK
        uuid price_item_id FK
        numeric price_usd
        numeric cup_reference_value
        numeric exchange_rate_used
        date pricing_date
        uuid rate_sample_id FK
    }
    PAYMENTS {
        uuid id PK
        uuid contract_id FK
        numeric amount
        char currency
        text method
        timestamptz paid_at
        text status
    }
    RECEIPTS {
        uuid id PK
        uuid payment_id FK
        text number
        bytea pdf_sha256
    }
    LICENSES {
        uuid id PK
        uuid user_id FK
        uuid contract_item_id FK
        text keygen_license_id
        text policy_code
        text status_cached
        timestamptz first_activated_at
        timestamptz expires_at
        timestamptz last_validated_at
        int max_devices
    }
    LICENSE_DEVICES {
        uuid license_id FK
        uuid device_id FK
        text keygen_machine_id
        timestamptz file_expires_at
        timestamptz deactivated_at
    }
    LICENSE_ENTITLEMENTS {
        uuid license_id FK
        text entitlement_code
        text source "POLICY o LICENSE"
    }
    LICENSE_EVENTS {
        uuid id PK
        uuid license_id FK
        text type
        jsonb details
        timestamptz at
    }
    KEYGEN_WEBHOOK_EVENTS {
        text id PK "id del evento de Keygen"
        text type
        bool signature_ok
        jsonb payload
        timestamptz processed_at
    }
```

### 3.3.6 Transversales: auditoría, sincronización y notificaciones

```mermaid
erDiagram
    AUDIT_BLOCKS ||--|{ AUDIT_EVENTS : "sella"
    USERS ||--o{ AUDIT_EVENTS : "actor"
    DEVICES ||--o{ SYNC_MUTATIONS : "envia"
    SYNC_MUTATIONS ||--o| SYNC_CONFLICTS : "puede generar"
    USERS ||--o{ NOTIFICATIONS : "recibe"

    AUDIT_EVENTS {
        uuid id PK
        uuid organization_id FK
        uuid block_id FK
        timestamptz occurred_at
        uuid actor_id
        text action
        text entity_type
        uuid entity_id
        jsonb before
        jsonb after
        text reason
        text result
        inet ip
        uuid device_id
        bytea event_hash
    }
    AUDIT_BLOCKS {
        uuid id PK
        uuid organization_id FK
        bigint from_seq
        bigint to_seq
        bytea merkle_root
        bytea prev_block_hash
        bytea signature
        int key_version
    }
    SYNC_CHANGE_LOG {
        bigint seq PK
        uuid organization_id FK
        text entity_type
        uuid entity_id
        text op "UPSERT o DELETE"
        bigint entity_version
        uuid branch_id
    }
    SYNC_MUTATIONS {
        uuid mutation_id PK
        uuid device_id FK
        bigint seq_no
        text entity_type
        uuid entity_id
        text op
        bigint base_version
        text status "APPLIED, CONFLICT o REJECTED"
        text result_code
    }
    SYNC_CONFLICTS {
        uuid id PK
        uuid mutation_id FK
        jsonb server_state
        jsonb client_payload
        text state
    }
    NOTIFICATIONS {
        uuid id PK
        uuid user_id FK
        text type
        jsonb payload
        timestamptz read_at
    }
    OUTBOX_EVENTS {
        uuid id PK
        text topic
        jsonb payload
        int attempts
        timestamptz processed_at
    }
```

## 3.4 Invariantes y su aplicación

Estas reglas protegen la **corrección de datos** (prioridad 3). Cada una se aplica en la capa más baja posible (la base de datos) y se repite en el servicio para dar mensajes útiles.

| Id | Invariante | Aplicación 🧭 |
|---|---|---|
| I‑01 | Ninguna fila cruza organizaciones. | `organization_id NOT NULL`; **RLS forzado** (`FORCE ROW LEVEL SECURITY`; rol de la app sin `BYPASSRLS`); FK compuestas `(organization_id, id)`; pruebas de fuga. |
| I‑02 | A lo sumo **una versión VIGENTE** por ficha. | Índice único parcial `ON cost_sheet_versions(cost_sheet_id) WHERE status='VIGENTE' AND deleted_at IS NULL`. |
| I‑03 | `version_no` único por ficha. | `UNIQUE (cost_sheet_id, version_no)`. |
| I‑04 | La vigencia de versiones VIGENTE/REEMPLAZADA no se solapa. | `EXCLUDE USING gist (cost_sheet_id WITH =, daterange(valid_from, valid_to, '[)') WITH &&) WHERE status IN ('VIGENTE','REEMPLAZADA')` (extensión `btree_gist`). |
| I‑05 | El contenido queda **congelado** al salir de BORRADOR. | Trigger que rechaza `UPDATE/DELETE` sobre líneas y columnas de contenido salvo en BORRADOR; `content_hash` (SHA‑256 del JSON canónico) calculado al enviar y **re‑verificado** al validar, aprobar y activar. |
| I‑06 | Solo transiciones de estado válidas. | Tabla de transiciones permitidas + trigger sobre `status`; el cambio solo se hace vía casos de uso (nunca `UPDATE` directo). |
| I‑07 | El aprobador es distinto del autor cuando la política lo exige. | Servicio + `CHECK` en `approval_steps`. |
| I‑08 | Una línea de ficha **no pierde** su referencia ni su costo. | Referencia a `ipv_value_id` con `ON DELETE RESTRICT`; copia de `unit_cost_snapshot`, moneda y `rate_to_calc`. **Sin** `SET NULL`. |
| I‑09 | Una línea de control solo puede apuntar a una versión **no anulada y vigente para el período**. | Trigger al insertar + servicio: `status` ∈ {VIGENTE, REEMPLAZADA} (nunca ANULADA), `valid_from ≤ period_start` y (`valid_to IS NULL` o `period_end ≤ valid_to`). Caso offline: [doc 9](09-flujo-android-offline-sync.md#94-política-de-conflictos-por-entidad). |
| I‑10 | Los valores IPV no se solapan por sujeto, sucursal y moneda. | `EXCLUDE USING gist` sobre `daterange(valid_from, valid_to)`; exactamente uno de `raw_material_id`/`product_id` (`CHECK`). |
| I‑11 | Importes ≥ 0 y tasas > 0. | `CHECK` en columnas monetarias y de tasa. |
| I‑12 | Las muestras de tasa son **inmutables**. | Trigger contra `UPDATE/DELETE`; las correcciones son filas nuevas con `supersedes_id`. |
| I‑13 | Las instantáneas de tasas son inmutables y sin duplicados. | `content_hash UNIQUE`; `UNIQUE (snapshot_id, instrument_code)`. |
| I‑14 | La auditoría es **solo de anexado**. | El rol de la app solo tiene `INSERT`; trigger rechaza `UPDATE/DELETE/TRUNCATE`; verificación periódica de bloques ([doc 12](12-estrategia-auditoria.md)). |
| I‑15 | Una mutación de sync se aplica una sola vez. | `sync_mutations.mutation_id` PK; el resultado se guarda y se devuelve idéntico en reintentos. |
| I‑16 | Se borra de forma **lógica**. | `deleted_at`; `DELETE` físico solo para el rol del trabajo de retención. |
| I‑17 | El inventario es un **libro mayor de solo anexado**. | Sin `UPDATE/DELETE` en `inventory_movements`; correcciones por asientos de reversa; `stock_balances` se concilia por trabajo. |
| I‑18 | `keygen_license_id` es único; una licencia activa por (usuario, organización). | `UNIQUE` + índice parcial (regla exacta ⛔ [D‑10](16-decisiones-pendientes.md#d-10)). |
| I‑19 | Numeración de documentos sin duplicados por (empresa, tipo, año). | `document_sequences` actualizado con bloqueo de fila; **el número definitivo lo asigna el servidor** al sincronizar (en el dispositivo se muestra una referencia local). |
| I‑20 | Las monedas de las líneas pertenecen al conjunto permitido de la empresa. | `CHECK`/servicio (conjunto ⛔ [D‑27](16-decisiones-pendientes.md#d-27)). |

## 3.5 Catálogo de entidades

`R` = se replica a Android solo lectura · `RW` = lectura y escritura offline · `—` = nunca se replica.

### Tenencia, identidad y acceso

| Entidad | Propósito | Columnas/ideas clave | Android |
|---|---|---|---|
| `organizations` | Cliente/tenant. | `name`, `status`. Dueña de licencias y contratos. | — |
| `companies` | Entidad operadora. | `tax_id`, `base_currency`, `timezone`, `settings` (política de redondeo y de tasas). | R |
| `branches` | Sucursal/unidad. | `code`, `name`, `active`. | R |
| `users` | Personas con acceso. | `email` (`citext`, único por org), `password_hash` (Argon2id), `status`. | R (solo el propio perfil) |
| `mfa_factors` | Segundo factor. | Semilla TOTP **cifrada**; códigos de recuperación como *hash* aparte. | — |
| `roles`, `permissions`, `role_permissions` | RBAC. | Roles de sistema (`organization_id` nulo) y por organización. Permisos `modulo:accion` y `costs:view`. | R (capacidades del usuario) |
| `role_assignments` | Rol con alcance. | `scope_type` ∈ ORG/COMPANY/BRANCH, `scope_id`, vigencia. | R (propias) |
| `devices` | Dispositivos/instalaciones. | `platform`, `installation_hash` (nunca ID de hardware), `last_sync_at`. | — |
| `sessions` | Sesiones y *refresh tokens*. | Hash del token, `family_id` (detección de reutilización), revocación. | — |

### Catálogo e inventario

| Entidad | Propósito | Columnas/ideas clave | Android |
|---|---|---|---|
| `categories` | Bebidas, Comidas, Productos, Servicios y personalizadas. | `kind`, `parent_id`; enlaza conjuntos de reglas. | R |
| `units` | Unidades y conversión a la base. | `dimension`, `to_base_factor` (⛔ [D‑26](16-decisiones-pendientes.md#d-26)). | R |
| `suppliers` | Proveedores. | `tax_id`, contacto. | R |
| `raw_materials` | Identidad del insumo. | `code` único por empresa, unidad base, `min_stock`. | R |
| `products` | Productos y servicios. | `kind`, categoría, `code`. | R |
| `ipv_values` | **Valor IPV**: precio/valor vigente con respaldo documental (antes `materials`). | Moneda, `unit_price`, vigencia sin solape, `source_ref`, proveedor. | R |
| `warehouses` | Almacenes por sucursal. | — | R |
| `inventory_movements` | Libro mayor de inventario. | `type`, `quantity`, `business_date`, `mutation_id`. | RW |
| `stock_balances` | Saldos derivados. | Conciliados contra el libro. | R |
| `inventory_counts` (+ líneas) | Conteos físicos. | Origen de ajustes. | RW |

### Costeo, reglas y flujo

| Entidad | Propósito | Columnas/ideas clave | Android |
|---|---|---|---|
| `cost_sheets` | Identidad estable de la ficha de un producto. | `product_id`, `branch_id` (nulo = todas). | R |
| `cost_sheet_versions` | **Versión inmutable** con estado. | `version_no`, `status`, `valid_from/to`, rendimiento, totales, `rate_snapshot_id`, `rule_set_version_id`, `content_hash`, `parent_version_id`, sellos de envío/validación/aprobación/activación/anulación, `annul_reason`. Instantánea de tasa **nula en BORRADOR** y obligatoria después (`CHECK`). | R (RW solo en BORRADOR, etapa 6c) |
| `cost_sheet_lines` | Líneas de la versión. | `line_type`, material **o** subficha, `ipv_value_id`, cantidad, merma, `unit_cost_snapshot`, moneda, `rate_to_calc`, `line_cost`. Ciclos de subfichas detectados en el servicio. | R |
| `status_history` | Línea de tiempo de estados. | `from/to`, actor, motivo. | R |
| `approval_policies`, `approval_requests`, `approval_steps` | Aprobaciones configurables. | Pasos por rol, regla de cuatro ojos. | R (estado) |
| `rule_sets`, `rule_set_versions` | Reglas **versionadas e inmutables**. | `definition` JSON (DSL cerrado), `checksum`, vigencia. | R |
| `rule_evaluations`, `rule_overrides` | Resultado de validar y excepciones justificadas. | `results` (errores/avisos), justificación y aprobador. | R |

### Control IPV

| Entidad | Propósito | Columnas/ideas clave | Android |
|---|---|---|---|
| `ipv_controls` | Cabecera del control por sucursal y período. | `control_no` asignado por servidor, **`mode`** (qué se controla: ⛔ [D‑01](16-decisiones-pendientes.md#d-01); ver [doc 6](06-flujo-ficha-ipv.md#67-control-ipv)), período, estado. | RW |
| `ipv_control_lines` | Contraste **esperado vs observado** (neutro respecto al modo). | `cost_sheet_version_id` (no 1:1), esperado **copiado** de la versión (instantánea), observado + `observation_source`, varianza, causa. | RW |
| `document_sequences` | Numeración. | `(company, doc_type, year)`. | — |

### Tasas, comercial y licencias

| Entidad | Propósito | Columnas/ideas clave | Android |
|---|---|---|---|
| `rate_instruments` | Instrumentos (USD, EUR, MLC, CAD, MXN, ZELLE, CLA…) con su **etiqueta tal como la da la fuente**. | `kind` ⛔ (no se interpreta qué es MLC/ZELLE/CLA más allá de la etiqueta de elTOQUE). | R |
| `rate_provider_mappings` | Correspondencia código del proveedor → instrumento (p. ej. `ECU` → `EUR`). | Se rellena y valida con el contrato real (spike S‑2). | — |
| `exchange_rate_samples` | Muestras inmutables de cada fuente. | `source`, `value`, `source_ts_raw`, `source_ts_utc` (nulo hasta confirmar zona horaria), `fetched_at`, `payload_hash`, `is_test`. | R (últimas) |
| `exchange_rate_current` | Puntero a la muestra vigente por instrumento + estado. | `FRESH/STALE/CACHED/UNAVAILABLE/MANUAL/TEST`. | R |
| `rate_provider_runs` | Bitácora de cada llamada al proveedor. | Resultado, HTTP, latencia, cuota restante, `retry_after`. | — |
| `rate_snapshots`, `rate_snapshot_items` | **Instantánea inmutable** de tasas citada por fichas y contratos. | `content_hash` único; sin `organization_id` (datos no sensibles y deduplicados). | R |
| `rate_policies` | Política de tasas por empresa. | Instrumento principal, prioridad de fuentes, vejez máxima, permiso de tasa manual. | R |
| `price_catalog_items` | Precios de políticas de licencia, desarrollo y servicios, **editables sin código**. | `policy_code`, `duration_days`, `price_usd`, vigencia (el historial son filas nuevas). | — |
| `contracts`, `contract_items`, `payments`, `receipts` | Cliente, contrato, precio, moneda, pago y comprobante (**Keygen no es facturación**). | `price_usd`, `cup_reference_value`, `exchange_rate_used`, `pricing_date`, `rate_sample_id`. | — |
| `licenses` | Espejo de la licencia de Keygen, por **usuario**. | `keygen_license_id`, política, estado en caché, expiración, `max_devices`, motivos de suspensión/revocación. **No** guarda la clave de licencia. | R (propia) |
| `license_devices`, `license_entitlements`, `license_events` | Dispositivos activados, derechos y bitácora. | `keygen_machine_id`, `file_expires_at`. | R (propios) |
| `keygen_webhook_events` | Eventos entrantes con firma verificada. | `id` de Keygen como PK (idempotencia). | — |

### Transversales

| Entidad | Propósito | Android |
|---|---|---|
| `audit_events`, `audit_blocks` | Auditoría de solo anexado, sellada por bloques ([doc 12](12-estrategia-auditoria.md)). | — |
| `sync_change_log` | Cursor monótono de cambios por organización/sucursal. | — |
| `sync_mutations`, `sync_conflicts` | Idempotencia y conflictos revisables. | — (el cliente tiene su `outbox`) |
| `notifications`, `outbox_events` | Bandeja del usuario y publicación fiable de eventos. | R |

## 3.6 Qué se replica a Android

- **Alcance**: solo lo que el usuario puede ver según su rol, empresa y sucursal; los datos de costos se envían solo si tiene `costs:view`.
- **Escritura offline** (`RW`): conteos de inventario, movimientos y líneas de Control IPV, y —en la etapa 6c— borradores de ficha. **Nunca** se escriben offline los cambios de estado de una ficha ([ADR‑0001](adr/0001-reabrir-offline-y-multisucursal.md#5-despliegue-escalonado)).
- **Nunca** en el dispositivo: auditoría, contratos, pagos, sesiones, datos de otros usuarios.
- Al revocarse un permiso o salir un dato del alcance, el servidor envía una **lápida** (`DELETE` en `sync_change_log`) y el cliente lo retira.

## 3.7 Migraciones y semillas

- **Flyway** con migraciones SQL versionadas e inmutables; cada migración con prueba en Testcontainers.
- **Semilla 100 % sintética** (generador reproducible): organizaciones, empresas, sucursales, usuarios por rol, catálogo, valores IPV, fichas en cada estado y tasas de prueba marcadas `is_test = true`. No se importan datos ni credenciales de los repos origen ([C‑11](01-analisis-arquitectonico.md#c-11)).
- Las **políticas y derechos de Keygen** no son SQL: se aprovisionan con un script idempotente ([doc 7](07-flujo-licencias-keygen.md)).
