-- Fase 6: epoch de restauración, cursor por dispositivo, sombra de entidades y payload del change_log.

CREATE TABLE sync_server_state (
    organization_id uuid PRIMARY KEY REFERENCES organizations (id),
    epoch           bigint NOT NULL DEFAULT 1 CHECK (epoch >= 1),
    min_retained_seq bigint NOT NULL DEFAULT 0 CHECK (min_retained_seq >= 0)
);

ALTER TABLE sync_change_log
    ADD COLUMN payload jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE sync_mutations
    ADD COLUMN client_payload jsonb,
    ADD COLUMN result_payload jsonb,
    ADD COLUMN entity_version bigint;

ALTER TABLE devices
    ADD COLUMN sync_cursor bigint,
    ADD COLUMN sync_epoch bigint;

CREATE TABLE sync_shadow_entities (
    organization_id uuid NOT NULL,
    entity_type     text NOT NULL,
    entity_id       uuid NOT NULL,
    version         bigint NOT NULL,
    payload         jsonb NOT NULL DEFAULT '{}'::jsonb,
    deleted_at      timestamptz,
    PRIMARY KEY (organization_id, entity_type, entity_id)
);

INSERT INTO sync_server_state (organization_id)
SELECT id FROM organizations
ON CONFLICT DO NOTHING;

SELECT app.install_tenant_rls('sync_server_state'::regclass);
SELECT app.install_tenant_rls('sync_shadow_entities'::regclass);

GRANT SELECT, INSERT, UPDATE ON sync_server_state, sync_shadow_entities TO app_rw;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_rw;
