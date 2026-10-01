-- Tenencia, identidad y acceso (doc 3.3.1). I-01: organization_id + UNIQUE (organization_id, id).

CREATE TABLE organizations (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    name            text NOT NULL,
    status          text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    origin_created_at timestamptz,
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid,
    origin_device_id uuid
);

CREATE TABLE companies (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL REFERENCES organizations (id),
    name            text NOT NULL,
    tax_id          text,
    base_currency   text NOT NULL DEFAULT 'CUP' CHECK (base_currency ~ '^[A-Z]{3}$'),
    timezone        text NOT NULL DEFAULT 'America/Havana',
    settings        jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    origin_created_at timestamptz,
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid,
    origin_device_id uuid,
    UNIQUE (organization_id, id)
);

CREATE TABLE branches (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    code            text NOT NULL,
    name            text NOT NULL,
    active          boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    origin_created_at timestamptz,
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid,
    origin_device_id uuid,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, company_id, code),
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id)
);

CREATE TABLE users (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL REFERENCES organizations (id),
    email           citext NOT NULL,
    password_hash   text NOT NULL,
    display_name    text NOT NULL,
    status          text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    mfa_enabled     boolean NOT NULL DEFAULT false,
    last_login_at   timestamptz,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    origin_created_at timestamptz,
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid,
    origin_device_id uuid,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, email)
);

CREATE TABLE permissions (
    code            text PRIMARY KEY,
    description     text NOT NULL
);

CREATE TABLE roles (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid REFERENCES organizations (id),
    code            text NOT NULL,
    name            text NOT NULL,
    is_system       boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    UNIQUE (id, organization_id),
    UNIQUE (organization_id, code)
);

CREATE UNIQUE INDEX uq_roles_system_code ON roles (code) WHERE organization_id IS NULL AND deleted_at IS NULL;

CREATE TABLE role_permissions (
    role_id         uuid NOT NULL REFERENCES roles (id),
    permission_code text NOT NULL REFERENCES permissions (code),
    PRIMARY KEY (role_id, permission_code)
);

CREATE TABLE role_assignments (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    user_id         uuid NOT NULL,
    role_id         uuid NOT NULL REFERENCES roles (id),
    scope_type      text NOT NULL CHECK (scope_type IN ('ORG', 'COMPANY', 'BRANCH')),
    scope_id        uuid NOT NULL,
    valid_from      timestamptz NOT NULL DEFAULT clock_timestamp(),
    valid_to        timestamptz,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, user_id) REFERENCES users (organization_id, id)
);

CREATE TABLE mfa_factors (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    user_id         uuid NOT NULL,
    type            text NOT NULL CHECK (type IN ('TOTP', 'WEBAUTHN', 'RECOVERY')),
    secret_enc      bytea,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, user_id) REFERENCES users (organization_id, id)
);

CREATE TABLE devices (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    user_id         uuid NOT NULL,
    platform        text NOT NULL CHECK (platform IN ('ANDROID', 'WEB')),
    installation_hash text NOT NULL,
    status          text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED', 'LOST')),
    last_sync_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, installation_hash),
    FOREIGN KEY (organization_id, user_id) REFERENCES users (organization_id, id)
);

CREATE TABLE sessions (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    user_id         uuid NOT NULL,
    device_id       uuid,
    refresh_hash    text NOT NULL,
    family_id       uuid NOT NULL,
    revoked_at      timestamptz,
    expires_at      timestamptz NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, user_id) REFERENCES users (organization_id, id)
);
