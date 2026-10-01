-- Fase 3: identidad (login SECURITY DEFINER), permisos por rol, idempotencia.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS failed_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until timestamptz;

INSERT INTO permissions (code, description) VALUES
    ('costing:edit', 'Crear y editar borradores de ficha')
ON CONFLICT (code) DO NOTHING;

-- Matriz de partida doc 11.5 (roles de sistema).
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN (VALUES
    ('catalog:edit'), ('costing:edit'), ('costing:submit'), ('costs:view')
) AS p(code)
WHERE r.code = 'COSTEADOR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN (VALUES ('costing:validate'), ('costs:view')) AS p(code)
WHERE r.code = 'REVISOR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN (VALUES
    ('costing:approve'), ('costing:activate'), ('costing:annul'), ('costs:view'),
    ('ipvcontrol:close')
) AS p(code)
WHERE r.code = 'APROBADOR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN (VALUES
    ('ipvcontrol:capture'), ('ipvcontrol:close'), ('costs:view')
) AS p(code)
WHERE r.code = 'CONTROLADOR_IPV'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN (VALUES ('inventory:move')) AS p(code)
WHERE r.code = 'ALMACENERO'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN (VALUES ('rates:manual'), ('costs:view'), ('DATA_EXPORT')) AS p(code)
WHERE r.code = 'ECONOMICO'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN (VALUES ('ADVANCED_AUDIT'), ('costs:view')) AS p(code)
WHERE r.code = 'AUDITOR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'costs:view'
FROM roles r
WHERE r.code = 'COMPANY_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'COMPANY_ADMIN'
  AND p.code IN (
      'users:manage', 'catalog:edit', 'costing:edit', 'costing:submit',
      'ipvcontrol:capture', 'ipvcontrol:close', 'inventory:move',
      'rates:manual', 'rules:edit', 'DATA_EXPORT', 'costs:view'
  )
ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION app.lookup_user_for_login(p_email citext)
RETURNS TABLE (
    id uuid,
    organization_id uuid,
    password_hash text,
    status text,
    mfa_enabled boolean,
    display_name text,
    failed_attempts integer,
    locked_until timestamptz
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT u.id, u.organization_id, u.password_hash, u.status, u.mfa_enabled,
           u.display_name, u.failed_attempts, u.locked_until
    FROM users u
    WHERE u.email = p_email AND u.deleted_at IS NULL
$$;

CREATE OR REPLACE FUNCTION app.register_login_failure(p_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    UPDATE users
       SET failed_attempts = failed_attempts + 1,
           locked_until = CASE
               WHEN failed_attempts + 1 >= 5 THEN clock_timestamp() + interval '15 minutes'
               ELSE locked_until
           END,
           status = CASE
               WHEN failed_attempts + 1 >= 5 THEN 'LOCKED'
               ELSE status
           END
     WHERE id = p_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION app.register_login_success(p_user_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    UPDATE users
       SET failed_attempts = 0,
           locked_until = NULL,
           last_login_at = clock_timestamp(),
           status = CASE WHEN status = 'LOCKED' THEN 'ACTIVE' ELSE status END
     WHERE id = p_user_id;
END;
$$;

CREATE TABLE idempotency_keys (
    organization_id uuid NOT NULL,
    key             text NOT NULL,
    method          text NOT NULL,
    path            text NOT NULL,
    request_hash    text NOT NULL,
    response_status integer NOT NULL,
    response_body   jsonb NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (organization_id, key)
);

ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON idempotency_keys
    USING (organization_id = app.current_organization_id())
    WITH CHECK (organization_id = app.current_organization_id());

GRANT SELECT, INSERT, UPDATE ON idempotency_keys TO app_rw;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA app TO app_rw, audit_writer, reporting_ro, retention_job, migrator;
