-- Auditoría, sincronización y notificaciones (doc 3.3.6 / doc 12).

CREATE TABLE audit_event_counters (
    organization_id uuid PRIMARY KEY REFERENCES organizations (id),
    last_seq        bigint NOT NULL DEFAULT 0 CHECK (last_seq >= 0)
);

-- I-14 solo anexado.
CREATE TABLE audit_events (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL REFERENCES organizations (id),
    seq             bigint NOT NULL,
    block_id        uuid,
    occurred_at     timestamptz NOT NULL DEFAULT clock_timestamp(),
    actor_type      text NOT NULL DEFAULT 'USER',
    actor_id        uuid,
    action          text NOT NULL,
    entity_type     text,
    entity_id       uuid,
    before          jsonb,
    after           jsonb,
    reason          text,
    result          text NOT NULL CHECK (result IN ('SUCCESS', 'DENIED', 'FAILED')),
    ip              inet,
    device_id       uuid,
    event_hash      bytea,
    UNIQUE (organization_id, seq)
);

CREATE TABLE audit_blocks (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL REFERENCES organizations (id),
    from_seq        bigint NOT NULL,
    to_seq          bigint NOT NULL,
    merkle_root     bytea NOT NULL,
    prev_block_hash bytea,
    signature       bytea,
    key_version     integer NOT NULL DEFAULT 1,
    sealed_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK (to_seq >= from_seq)
);

CREATE OR REPLACE FUNCTION app.assign_audit_seq() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    n bigint;
BEGIN
    INSERT INTO audit_event_counters (organization_id, last_seq)
    VALUES (NEW.organization_id, 1)
    ON CONFLICT (organization_id)
    DO UPDATE SET last_seq = audit_event_counters.last_seq + 1
    RETURNING last_seq INTO n;
    NEW.seq := n;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_audit_events_seq
    BEFORE INSERT ON audit_events
    FOR EACH ROW
    EXECUTE FUNCTION app.assign_audit_seq();

CREATE TABLE sync_change_log (
    seq             bigserial PRIMARY KEY,
    organization_id uuid NOT NULL,
    entity_type     text NOT NULL,
    entity_id       uuid NOT NULL,
    op              text NOT NULL CHECK (op IN ('UPSERT', 'DELETE')),
    entity_version  bigint NOT NULL,
    branch_id       uuid
);

-- I-15 mutation_id PK: una mutación se aplica una sola vez.
CREATE TABLE sync_mutations (
    mutation_id     uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    device_id       uuid NOT NULL,
    seq_no          bigint NOT NULL,
    entity_type     text NOT NULL,
    entity_id       uuid NOT NULL,
    op              text NOT NULL,
    base_version    bigint,
    status          text NOT NULL CHECK (status IN ('APPLIED', 'CONFLICT', 'REJECTED')),
    result_code     text,
    applied_at      timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE sync_conflicts (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    mutation_id     uuid NOT NULL REFERENCES sync_mutations (mutation_id),
    server_state    jsonb,
    client_payload  jsonb,
    state           text NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN', 'RESOLVED', 'DISCARDED'))
);

CREATE TABLE notifications (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    user_id         uuid NOT NULL,
    type            text NOT NULL,
    payload         jsonb NOT NULL DEFAULT '{}'::jsonb,
    read_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (organization_id, user_id) REFERENCES users (organization_id, id)
);

CREATE TABLE outbox_events (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    topic           text NOT NULL,
    payload         jsonb NOT NULL,
    attempts        integer NOT NULL DEFAULT 0,
    processed_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp()
);
