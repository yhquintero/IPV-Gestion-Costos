-- Tasas, comercial y licencias (doc 3.3.5). Las tasas de prueba NUNCA son constantes de código.

CREATE TABLE rate_instruments (
    code            text PRIMARY KEY CHECK (code ~ '^[A-Z]{3,8}$'),
    display_name    text NOT NULL,
    kind            text NOT NULL DEFAULT 'LABEL',
    enabled         boolean NOT NULL DEFAULT true
);

CREATE TABLE rate_provider_mappings (
    provider        text NOT NULL,
    provider_code   text NOT NULL,
    instrument_code text NOT NULL REFERENCES rate_instruments (code),
    PRIMARY KEY (provider, provider_code)
);

CREATE TABLE rate_provider_runs (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    provider        text NOT NULL,
    outcome         text NOT NULL,
    http_status     integer,
    latency_ms      integer,
    ratelimit_remaining integer,
    retry_after_s   integer,
    started_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    finished_at     timestamptz
);

-- I-12 inmutables; correcciones = fila nueva con supersedes_id.
CREATE TABLE exchange_rate_samples (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    instrument_code text NOT NULL REFERENCES rate_instruments (code),
    value           numeric(18, 6) NOT NULL CHECK (value > 0), -- I-11
    source          text NOT NULL CHECK (source IN ('ELTOQUE_API', 'MANUAL', 'SEED_TEST')),
    provider_run_id uuid REFERENCES rate_provider_runs (id),
    source_ts_raw   jsonb,
    source_ts_utc   timestamptz,
    fetched_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    payload_hash    bytea,
    is_test         boolean NOT NULL DEFAULT false,
    supersedes_id   uuid REFERENCES exchange_rate_samples (id)
);

CREATE TABLE exchange_rate_current (
    instrument_code text PRIMARY KEY REFERENCES rate_instruments (code),
    sample_id       uuid NOT NULL REFERENCES exchange_rate_samples (id),
    status          text NOT NULL CHECK (status IN ('FRESH', 'STALE', 'CACHED', 'UNAVAILABLE', 'MANUAL', 'TEST')),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- I-13 instantáneas inmutables, content_hash único.
CREATE TABLE rate_snapshots (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    captured_at     timestamptz NOT NULL DEFAULT clock_timestamp(),
    content_hash    bytea NOT NULL UNIQUE,
    status_at_capture text NOT NULL,
    is_test         boolean NOT NULL DEFAULT false
);

CREATE TABLE rate_snapshot_items (
    snapshot_id     uuid NOT NULL REFERENCES rate_snapshots (id),
    instrument_code text NOT NULL REFERENCES rate_instruments (code),
    sample_id       uuid NOT NULL REFERENCES exchange_rate_samples (id),
    value           numeric(18, 6) NOT NULL CHECK (value > 0),
    PRIMARY KEY (snapshot_id, instrument_code) -- I-13
);

ALTER TABLE cost_sheet_versions
    ADD CONSTRAINT cost_sheet_versions_rate_snapshot_fk
    FOREIGN KEY (rate_snapshot_id) REFERENCES rate_snapshots (id);

CREATE TABLE rate_policies (
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    primary_instrument text NOT NULL REFERENCES rate_instruments (code),
    source_priority jsonb NOT NULL DEFAULT '["ELTOQUE_API","MANUAL"]'::jsonb,
    max_stale_minutes integer NOT NULL DEFAULT 1440 CHECK (max_stale_minutes > 0),
    allow_manual_override boolean NOT NULL DEFAULT true,
    PRIMARY KEY (organization_id, company_id),
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id)
);

CREATE TABLE price_catalog_items (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    policy_code     text NOT NULL,
    kind            text NOT NULL CHECK (kind IN ('LICENSE', 'DEVELOPMENT', 'SERVICE')),
    duration_days   integer CHECK (duration_days IS NULL OR duration_days > 0),
    price_usd       numeric(19, 4) NOT NULL CHECK (price_usd >= 0), -- I-11
    active          boolean NOT NULL DEFAULT true,
    valid_from      date NOT NULL,
    valid_to        date
);

CREATE TABLE contracts (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL REFERENCES organizations (id),
    number          text NOT NULL,
    type            text NOT NULL,
    status          text NOT NULL DEFAULT 'DRAFT',
    total_usd       numeric(19, 4) NOT NULL DEFAULT 0 CHECK (total_usd >= 0),
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, number)
);

CREATE TABLE contract_items (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    contract_id     uuid NOT NULL,
    price_item_id   uuid REFERENCES price_catalog_items (id),
    price_usd       numeric(19, 4) NOT NULL CHECK (price_usd >= 0),
    cup_reference_value numeric(19, 4),
    exchange_rate_used numeric(18, 6) CHECK (exchange_rate_used IS NULL OR exchange_rate_used > 0),
    pricing_date    date,
    rate_sample_id  uuid REFERENCES exchange_rate_samples (id),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, contract_id) REFERENCES contracts (organization_id, id)
);

CREATE TABLE payments (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    contract_id     uuid NOT NULL,
    amount          numeric(19, 4) NOT NULL CHECK (amount >= 0), -- I-11
    currency        text NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    method          text NOT NULL,
    paid_at         timestamptz,
    status          text NOT NULL DEFAULT 'PENDING',
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, contract_id) REFERENCES contracts (organization_id, id)
);

CREATE TABLE receipts (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    payment_id      uuid NOT NULL,
    number          text NOT NULL,
    pdf_sha256      bytea,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, number),
    FOREIGN KEY (organization_id, payment_id) REFERENCES payments (organization_id, id)
);

CREATE TABLE licenses (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    user_id         uuid NOT NULL,
    contract_item_id uuid,
    keygen_license_id text,
    policy_code     text NOT NULL,
    status_cached   text NOT NULL DEFAULT 'INACTIVE',
    first_activated_at timestamptz,
    expires_at      timestamptz,
    last_validated_at timestamptz,
    max_devices     integer NOT NULL DEFAULT 2 CHECK (max_devices >= 1),
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, user_id) REFERENCES users (organization_id, id),
    FOREIGN KEY (organization_id, contract_item_id) REFERENCES contract_items (organization_id, id)
);

-- I-18
CREATE UNIQUE INDEX uq_licenses_keygen_id
    ON licenses (keygen_license_id)
    WHERE keygen_license_id IS NOT NULL;

CREATE UNIQUE INDEX uq_licenses_one_active_per_user_org
    ON licenses (organization_id, user_id)
    WHERE deleted_at IS NULL AND status_cached NOT IN ('REVOKED', 'EXPIRED', 'INACTIVE');

CREATE TABLE license_devices (
    organization_id uuid NOT NULL,
    license_id      uuid NOT NULL,
    device_id       uuid NOT NULL,
    keygen_machine_id text,
    file_expires_at timestamptz,
    deactivated_at  timestamptz,
    PRIMARY KEY (license_id, device_id),
    FOREIGN KEY (organization_id, license_id) REFERENCES licenses (organization_id, id),
    FOREIGN KEY (organization_id, device_id) REFERENCES devices (organization_id, id)
);

CREATE TABLE license_entitlements (
    organization_id uuid NOT NULL,
    license_id      uuid NOT NULL,
    entitlement_code text NOT NULL,
    source          text NOT NULL CHECK (source IN ('POLICY', 'LICENSE')),
    PRIMARY KEY (license_id, entitlement_code),
    FOREIGN KEY (organization_id, license_id) REFERENCES licenses (organization_id, id)
);

CREATE TABLE license_events (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    license_id      uuid NOT NULL,
    type            text NOT NULL,
    details         jsonb NOT NULL DEFAULT '{}'::jsonb,
    at              timestamptz NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (organization_id, license_id) REFERENCES licenses (organization_id, id)
);

CREATE TABLE keygen_webhook_events (
    id              text PRIMARY KEY,
    type            text NOT NULL,
    signature_ok    boolean NOT NULL,
    payload         jsonb NOT NULL,
    processed_at    timestamptz
);
