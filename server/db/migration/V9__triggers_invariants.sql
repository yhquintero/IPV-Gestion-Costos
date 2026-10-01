-- Triggers de invariantes I-05, I-06, I-07, I-09, I-12, I-13, I-14, I-16, I-17, I-20.

-- I-06 transiciones de ficha.
CREATE OR REPLACE FUNCTION app.enforce_cost_sheet_status() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'BORRADOR' AND current_user = 'app_rw' THEN
            RAISE EXCEPTION 'I-06 new cost sheet version must start as BORRADOR'
                USING ERRCODE = 'check_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        IF NOT EXISTS (
            SELECT 1 FROM cost_sheet_status_transitions
            WHERE from_status = OLD.status AND to_status = NEW.status
        ) THEN
            RAISE EXCEPTION 'I-06 illegal transition % -> %', OLD.status, NEW.status
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cost_sheet_status
    BEFORE INSERT OR UPDATE OF status ON cost_sheet_versions
    FOR EACH ROW
    EXECUTE FUNCTION app.enforce_cost_sheet_status();

CREATE OR REPLACE FUNCTION app.enforce_ipv_control_status() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'PENDIENTE' AND current_user = 'app_rw' THEN
            RAISE EXCEPTION 'I-06 new control must start as PENDIENTE'
                USING ERRCODE = 'check_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        IF NOT EXISTS (
            SELECT 1 FROM ipv_control_status_transitions
            WHERE from_status = OLD.status AND to_status = NEW.status
        ) THEN
            RAISE EXCEPTION 'I-06 illegal control transition % -> %', OLD.status, NEW.status
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ipv_control_status
    BEFORE INSERT OR UPDATE OF status ON ipv_controls
    FOR EACH ROW
    EXECUTE FUNCTION app.enforce_ipv_control_status();

-- I-05 contenido congelado al salir de BORRADOR.
CREATE OR REPLACE FUNCTION app.enforce_cost_sheet_freeze() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF current_user <> 'app_rw' THEN
        RETURN NEW;
    END IF;
    -- Volver a BORRADOR descongela (doc 6.3).
    IF NEW.status = 'BORRADOR' THEN
        RETURN NEW;
    END IF;
    IF OLD.status = 'BORRADOR' THEN
        RETURN NEW;
    END IF;
    IF NEW.yield_qty IS DISTINCT FROM OLD.yield_qty
        OR NEW.calc_currency IS DISTINCT FROM OLD.calc_currency
        OR NEW.total_cost IS DISTINCT FROM OLD.total_cost
        OR NEW.unit_cost IS DISTINCT FROM OLD.unit_cost
        OR NEW.rate_snapshot_id IS DISTINCT FROM OLD.rate_snapshot_id
        OR NEW.rule_set_version_id IS DISTINCT FROM OLD.rule_set_version_id
        OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
        OR NEW.parent_version_id IS DISTINCT FROM OLD.parent_version_id
        OR NEW.version_no IS DISTINCT FROM OLD.version_no
        OR NEW.cost_sheet_id IS DISTINCT FROM OLD.cost_sheet_id
    THEN
        RAISE EXCEPTION 'I-05 cost sheet content is frozen after BORRADOR'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cost_sheet_freeze
    BEFORE UPDATE ON cost_sheet_versions
    FOR EACH ROW
    EXECUTE FUNCTION app.enforce_cost_sheet_freeze();

CREATE OR REPLACE FUNCTION app.enforce_cost_sheet_line_freeze() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    st text;
    vid uuid;
BEGIN
    IF current_user <> 'app_rw' THEN
        IF TG_OP = 'DELETE' THEN RETURN OLD; ELSE RETURN NEW; END IF;
    END IF;
    vid := COALESCE(NEW.version_id, OLD.version_id);
    SELECT status INTO st FROM cost_sheet_versions WHERE id = vid;
    IF st IS NULL THEN
        RAISE EXCEPTION 'I-05 missing cost sheet version %', vid;
    END IF;
    IF st <> 'BORRADOR' THEN
        RAISE EXCEPTION 'I-05 lines of a frozen cost sheet cannot change'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cost_sheet_line_freeze
    BEFORE INSERT OR UPDATE OR DELETE ON cost_sheet_lines
    FOR EACH ROW
    EXECUTE FUNCTION app.enforce_cost_sheet_line_freeze();

-- I-07 cuatro ojos.
CREATE OR REPLACE FUNCTION app.enforce_four_eyes() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    req_by uuid;
    four boolean;
BEGIN
    IF NEW.decided_by IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT r.requested_by, COALESCE(p.four_eyes, true)
      INTO req_by, four
    FROM approval_requests r
    JOIN approval_policies p ON p.id = r.policy_id
    WHERE r.id = NEW.request_id;
    IF four AND NEW.decided_by = req_by THEN
        RAISE EXCEPTION 'I-07 approver must differ from requester'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_approval_four_eyes
    BEFORE INSERT OR UPDATE ON approval_steps
    FOR EACH ROW
    EXECUTE FUNCTION app.enforce_four_eyes();

-- I-09 línea de control vs versión.
CREATE OR REPLACE FUNCTION app.enforce_control_line_version() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    st text;
    vf date;
    vt date;
    ps date;
    pe date;
BEGIN
    SELECT v.status, v.valid_from, v.valid_to, c.period_start, c.period_end
      INTO st, vf, vt, ps, pe
    FROM cost_sheet_versions v
    JOIN ipv_controls c ON c.id = NEW.control_id AND c.organization_id = NEW.organization_id
    WHERE v.id = NEW.cost_sheet_version_id
      AND v.organization_id = NEW.organization_id;

    IF st IS NULL THEN
        RAISE EXCEPTION 'I-09 cost sheet version not found'
            USING ERRCODE = 'foreign_key_violation';
    END IF;
    IF st NOT IN ('VIGENTE', 'REEMPLAZADA') THEN
        RAISE EXCEPTION 'I-09 version status % cannot be used on a control line', st
            USING ERRCODE = 'check_violation';
    END IF;
    IF vf IS NULL OR vf > ps OR (vt IS NOT NULL AND pe > vt) THEN
        RAISE EXCEPTION 'I-09 version is not valid for the control period'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_control_line_version
    BEFORE INSERT OR UPDATE OF cost_sheet_version_id, control_id ON ipv_control_lines
    FOR EACH ROW
    EXECUTE FUNCTION app.enforce_control_line_version();

-- I-20 moneda de línea en el conjunto de la empresa.
CREATE OR REPLACE FUNCTION app.enforce_line_currency() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    org uuid;
    company uuid;
BEGIN
    IF NEW.unit_currency IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT csv.organization_id, cs.company_id
      INTO org, company
    FROM cost_sheet_versions csv
    JOIN cost_sheets cs
      ON cs.id = csv.cost_sheet_id AND cs.organization_id = csv.organization_id
    WHERE csv.id = NEW.version_id;

    IF NOT EXISTS (
        SELECT 1 FROM company_allowed_currencies c
        WHERE c.organization_id = org
          AND c.company_id = company
          AND c.currency = NEW.unit_currency
    ) THEN
        RAISE EXCEPTION 'I-20 currency % is not allowed for the company', NEW.unit_currency
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_line_currency
    BEFORE INSERT OR UPDATE OF unit_currency, version_id ON cost_sheet_lines
    FOR EACH ROW
    EXECUTE FUNCTION app.enforce_line_currency();

-- I-12, I-13, I-14, I-17 append-only.
CREATE TRIGGER trg_samples_append
    BEFORE UPDATE OR DELETE ON exchange_rate_samples
    FOR EACH ROW EXECUTE FUNCTION app.reject_mutation('I-12');

CREATE TRIGGER trg_snapshots_append
    BEFORE UPDATE OR DELETE ON rate_snapshots
    FOR EACH ROW EXECUTE FUNCTION app.reject_mutation('I-13');

CREATE TRIGGER trg_snapshot_items_append
    BEFORE UPDATE OR DELETE ON rate_snapshot_items
    FOR EACH ROW EXECUTE FUNCTION app.reject_mutation('I-13');

CREATE TRIGGER trg_audit_events_append
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION app.reject_mutation('I-14');

CREATE TRIGGER trg_audit_blocks_append
    BEFORE UPDATE OR DELETE ON audit_blocks
    FOR EACH ROW EXECUTE FUNCTION app.reject_mutation('I-14');

CREATE TRIGGER trg_movements_append
    BEFORE UPDATE OR DELETE ON inventory_movements
    FOR EACH ROW EXECUTE FUNCTION app.reject_mutation('I-17');

CREATE TRIGGER trg_sync_mutations_append
    BEFORE UPDATE OR DELETE ON sync_mutations
    FOR EACH ROW EXECUTE FUNCTION app.reject_mutation('I-15');

-- I-16 DELETE físico en tablas de negocio sincronizables.
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'organizations', 'companies', 'branches', 'users', 'categories',
        'suppliers', 'raw_materials', 'products', 'ipv_values', 'warehouses',
        'cost_sheets', 'cost_sheet_versions', 'ipv_controls', 'contracts',
        'licenses'
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%s_no_delete BEFORE DELETE ON %I FOR EACH ROW EXECUTE FUNCTION app.reject_physical_delete()',
            t, t
        );
    END LOOP;
END
$$;
