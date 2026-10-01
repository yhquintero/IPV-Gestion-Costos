-- Fase 8: estado del proveedor de tasas, anomalías y detalle de ejecuciones.

ALTER TABLE exchange_rate_samples
    ADD COLUMN IF NOT EXISTS anomaly boolean NOT NULL DEFAULT false;

ALTER TABLE rate_provider_runs
    ADD COLUMN IF NOT EXISTS details jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS unknown_keys jsonb NOT NULL DEFAULT '[]'::jsonb;

CREATE TABLE rate_provider_state (
    provider              text PRIMARY KEY,
    paused                boolean NOT NULL DEFAULT false,
    pause_reason          text,
    last_success_at       timestamptz,
    last_outcome          text,
    last_http_status      integer,
    ratelimit_remaining   integer,
    circuit_open_until    timestamptz,
    consecutive_failures  integer NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    updated_at            timestamptz NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO rate_provider_state (provider) VALUES ('ELTOQUE')
ON CONFLICT (provider) DO NOTHING;

GRANT SELECT, INSERT, UPDATE ON rate_provider_state TO app_rw;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_rw;
