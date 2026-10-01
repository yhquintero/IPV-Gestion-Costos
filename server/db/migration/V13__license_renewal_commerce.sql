-- Fase 7: solicitudes de renovación, asiento web y catálogo comercial inicial (D-08).
-- KEYGEN Cloud sigue bloqueado (D-05 / ADR-0003).

CREATE TABLE license_renewal_requests (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    license_id      uuid NOT NULL,
    contract_item_id uuid,
    idempotency_key text NOT NULL,
    status          text NOT NULL DEFAULT 'SOLICITADA'
                    CHECK (status IN ('SOLICITADA', 'PAGO_CONFIRMADO', 'APLICADA', 'FALLIDA', 'CANCELADA')),
    expiry_before   timestamptz,
    expiry_after    timestamptz,
    provider_error  text,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (organization_id, id),
    UNIQUE (idempotency_key),
    FOREIGN KEY (organization_id, license_id) REFERENCES licenses (organization_id, id)
);

CREATE TABLE license_web_seats (
    organization_id uuid NOT NULL,
    license_id      uuid NOT NULL,
    fingerprint     text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (license_id),
    UNIQUE (organization_id, license_id),
    FOREIGN KEY (organization_id, license_id) REFERENCES licenses (organization_id, id)
);

SELECT app.install_tenant_rls('license_renewal_requests'::regclass);
SELECT app.install_tenant_rls('license_web_seats'::regclass);

GRANT SELECT, INSERT, UPDATE ON license_renewal_requests, license_web_seats TO app_rw;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_rw;

-- Catálogo editable (prices.json / D-08). No son tarifas legales.
INSERT INTO price_catalog_items (policy_code, kind, duration_days, price_usd, active, valid_from)
SELECT v.policy_code, 'LICENSE', v.duration_days, v.price_usd, true, DATE '2026-01-01'
FROM (VALUES
    ('IPV-TRIAL-7D',    7,   25.0000),
    ('IPV-MENSUAL',    30,   25.0000),
    ('IPV-TRIMESTRAL', 90,   75.0000),
    ('IPV-SEMESTRAL', 180,  195.0000),
    ('IPV-ANUAL',     365,  360.0000),
    ('IPV-BIENAL',    730,  600.0000),
    ('IPV-TRIENAL',  1095, 1020.0000)
) AS v(policy_code, duration_days, price_usd)
WHERE NOT EXISTS (
    SELECT 1 FROM price_catalog_items p WHERE p.policy_code = v.policy_code AND p.active
);
