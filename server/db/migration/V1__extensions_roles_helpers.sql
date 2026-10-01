-- IPV Gestión de Costos · Fase 2
-- Extensiones, roles de BD y funciones auxiliares.
-- El rol de la aplicación NUNCA tiene BYPASSRLS (I-01).

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SCHEMA IF NOT EXISTS app;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_rw') THEN
        CREATE ROLE app_rw NOSUPERUSER NOCREATEDB NOCREATEROLE NOLOGIN NOINHERIT NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'migrator') THEN
        CREATE ROLE migrator NOSUPERUSER NOCREATEDB NOCREATEROLE NOLOGIN NOINHERIT BYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_writer') THEN
        CREATE ROLE audit_writer NOSUPERUSER NOCREATEDB NOCREATEROLE NOLOGIN NOINHERIT NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'reporting_ro') THEN
        CREATE ROLE reporting_ro NOSUPERUSER NOCREATEDB NOCREATEROLE NOLOGIN NOINHERIT NOBYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'retention_job') THEN
        CREATE ROLE retention_job NOSUPERUSER NOCREATEDB NOCREATEROLE NOLOGIN NOINHERIT NOBYPASSRLS;
    END IF;
END
$$;

-- UUID v7 (timestamp ms + random). Compatible con PostgreSQL 17.
CREATE OR REPLACE FUNCTION app.uuid_v7() RETURNS uuid
LANGUAGE plpgsql AS $$
DECLARE
    unix_ts_ms bytea;
    uuid_bytes bytea;
BEGIN
    unix_ts_ms := substring(int8send((extract(epoch FROM clock_timestamp()) * 1000)::bigint) FROM 3);
    uuid_bytes := unix_ts_ms || gen_random_bytes(10);
    uuid_bytes := set_byte(uuid_bytes, 6, (get_byte(uuid_bytes, 6) & 15) | 112); -- version 7
    uuid_bytes := set_byte(uuid_bytes, 8, (get_byte(uuid_bytes, 8) & 63) | 128); -- RFC 4122 variant
    RETURN encode(uuid_bytes, 'hex')::uuid;
END;
$$;

CREATE OR REPLACE FUNCTION app.current_organization_id() RETURNS uuid
LANGUAGE plpgsql STABLE AS $$
DECLARE
    raw text;
BEGIN
    raw := current_setting('app.organization_id', true);
    IF raw IS NULL OR btrim(raw) = '' THEN
        RETURN NULL;
    END IF;
    RETURN raw::uuid;
END;
$$;

CREATE OR REPLACE FUNCTION app.bump_row() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.version := OLD.version + 1;
    NEW.updated_at := clock_timestamp();
    NEW.created_at := OLD.created_at;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION app.init_row() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.created_at := clock_timestamp();
    NEW.updated_at := NEW.created_at;
    NEW.version := 1;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION app.reject_physical_delete() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF current_user <> 'app_rw' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'I-16 physical DELETE is forbidden on % (use deleted_at)'
        , TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE OR REPLACE FUNCTION app.reject_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_user = 'retention_job' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION '% is append-only (invariant %)'
        , TG_TABLE_NAME, TG_ARGV[0]
        USING ERRCODE = 'restrict_violation';
END;
$$;

GRANT USAGE ON SCHEMA app TO app_rw, audit_writer, reporting_ro, retention_job, migrator;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA app TO app_rw, audit_writer, reporting_ro, retention_job, migrator;
