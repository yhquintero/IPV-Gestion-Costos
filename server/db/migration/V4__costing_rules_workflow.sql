-- Fichas de costo, reglas y aprobaciones (doc 3.3.3 / doc 6).

CREATE TABLE cost_sheet_status_transitions (
    from_status text NOT NULL,
    to_status   text NOT NULL,
    PRIMARY KEY (from_status, to_status)
);

CREATE TABLE cost_sheets (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    branch_id       uuid,
    product_id      uuid NOT NULL,
    code            text NOT NULL,
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
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches (organization_id, id),
    FOREIGN KEY (organization_id, product_id) REFERENCES products (organization_id, id)
);

CREATE TABLE cost_sheet_versions (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    cost_sheet_id   uuid NOT NULL,
    version_no      integer NOT NULL CHECK (version_no >= 1),
    status          text NOT NULL CHECK (status IN (
                        'BORRADOR', 'EN_REVISION', 'VALIDADA', 'APROBADA',
                        'VIGENTE', 'REEMPLAZADA', 'ANULADA'
                    )),
    parent_version_id uuid,
    valid_from      date,
    valid_to        date,
    yield_qty       numeric(19, 6) CHECK (yield_qty IS NULL OR yield_qty > 0),
    calc_currency   text CHECK (calc_currency IS NULL OR calc_currency ~ '^[A-Z]{3}$'),
    total_cost      numeric(19, 4) CHECK (total_cost IS NULL OR total_cost >= 0), -- I-11
    unit_cost       numeric(19, 4) CHECK (unit_cost IS NULL OR unit_cost >= 0),
    rate_snapshot_id uuid,
    rule_set_version_id uuid,
    content_hash    bytea,
    annul_reason    text,
    submitted_at    timestamptz,
    submitted_by    uuid,
    validated_at    timestamptz,
    validated_by    uuid,
    approved_at     timestamptz,
    approved_by     uuid,
    activated_at    timestamptz,
    activated_by    uuid,
    annulled_at     timestamptz,
    annulled_by     uuid,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    origin_created_at timestamptz,
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid,
    origin_device_id uuid,
    UNIQUE (organization_id, id),
    UNIQUE (cost_sheet_id, version_no), -- I-03
    CHECK (status = 'BORRADOR' OR rate_snapshot_id IS NOT NULL),
    CHECK (status NOT IN ('VIGENTE', 'REEMPLAZADA') OR valid_from IS NOT NULL),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from),
    FOREIGN KEY (organization_id, cost_sheet_id) REFERENCES cost_sheets (organization_id, id),
    FOREIGN KEY (organization_id, parent_version_id) REFERENCES cost_sheet_versions (organization_id, id)
);

-- I-02: a lo sumo una versión VIGENTE por ficha.
CREATE UNIQUE INDEX uq_cost_sheet_one_vigente
    ON cost_sheet_versions (cost_sheet_id)
    WHERE status = 'VIGENTE' AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_cost_sheet_one_draft
    ON cost_sheet_versions (cost_sheet_id)
    WHERE status = 'BORRADOR' AND deleted_at IS NULL;

-- I-04: vigencia de VIGENTE/REEMPLAZADA no se solapa.
ALTER TABLE cost_sheet_versions ADD CONSTRAINT cost_sheet_versions_no_overlap
    EXCLUDE USING gist (
        cost_sheet_id WITH =,
        daterange(valid_from, COALESCE(valid_to, 'infinity'::date), '[)') WITH &&
    ) WHERE (status IN ('VIGENTE', 'REEMPLAZADA') AND deleted_at IS NULL AND valid_from IS NOT NULL);

CREATE TABLE cost_sheet_lines (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    version_id      uuid NOT NULL,
    line_no         integer NOT NULL CHECK (line_no >= 1),
    line_type       text NOT NULL CHECK (line_type IN ('MATERIAL', 'SUBFICHA', 'MANO_OBRA', 'INDIRECTO', 'OTRO')),
    raw_material_id uuid,
    sub_version_id  uuid,
    ipv_value_id    uuid, -- I-08 ON DELETE RESTRICT (default)
    quantity        numeric(19, 6) NOT NULL CHECK (quantity >= 0), -- I-11
    waste_pct       numeric(9, 6) NOT NULL DEFAULT 0 CHECK (waste_pct >= 0),
    unit_cost_snapshot numeric(19, 4) CHECK (unit_cost_snapshot IS NULL OR unit_cost_snapshot >= 0),
    unit_currency   text CHECK (unit_currency IS NULL OR unit_currency ~ '^[A-Z]{3}$'),
    rate_to_calc    numeric(18, 6) CHECK (rate_to_calc IS NULL OR rate_to_calc > 0), -- I-11
    line_cost       numeric(19, 4) CHECK (line_cost IS NULL OR line_cost >= 0),
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    UNIQUE (organization_id, id),
    UNIQUE (version_id, line_no),
    CHECK (
        (line_type = 'MATERIAL' AND raw_material_id IS NOT NULL AND ipv_value_id IS NOT NULL
            AND unit_cost_snapshot IS NOT NULL AND unit_currency IS NOT NULL)
        OR (line_type = 'SUBFICHA' AND sub_version_id IS NOT NULL)
        OR (line_type IN ('MANO_OBRA', 'INDIRECTO', 'OTRO'))
    ),
    FOREIGN KEY (organization_id, version_id) REFERENCES cost_sheet_versions (organization_id, id),
    FOREIGN KEY (organization_id, raw_material_id) REFERENCES raw_materials (organization_id, id),
    FOREIGN KEY (organization_id, sub_version_id) REFERENCES cost_sheet_versions (organization_id, id),
    FOREIGN KEY (organization_id, ipv_value_id) REFERENCES ipv_values (organization_id, id)
);

CREATE TABLE rule_sets (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    category_id     uuid,
    name            text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id),
    FOREIGN KEY (organization_id, category_id) REFERENCES categories (organization_id, id)
);

CREATE TABLE rule_set_versions (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    rule_set_id     uuid NOT NULL,
    version_no      integer NOT NULL CHECK (version_no >= 1),
    definition      jsonb NOT NULL,
    checksum        bytea NOT NULL,
    effective_from  date NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (organization_id, id),
    UNIQUE (rule_set_id, version_no),
    FOREIGN KEY (organization_id, rule_set_id) REFERENCES rule_sets (organization_id, id)
);

CREATE TABLE rule_evaluations (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    version_id      uuid NOT NULL,
    rule_set_version_id uuid NOT NULL,
    results         jsonb NOT NULL,
    evaluated_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, version_id) REFERENCES cost_sheet_versions (organization_id, id),
    FOREIGN KEY (organization_id, rule_set_version_id) REFERENCES rule_set_versions (organization_id, id)
);

CREATE TABLE rule_overrides (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    evaluation_id   uuid NOT NULL,
    rule_id         text NOT NULL,
    justification   text NOT NULL,
    approved_by     uuid NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, evaluation_id) REFERENCES rule_evaluations (organization_id, id)
);

CREATE TABLE approval_policies (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    entity_type     text NOT NULL,
    steps           jsonb NOT NULL,
    four_eyes       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id)
);

CREATE TABLE approval_requests (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    policy_id       uuid NOT NULL,
    entity_type     text NOT NULL,
    entity_id       uuid NOT NULL,
    requested_by    uuid NOT NULL,
    status          text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'APPROVED', 'REJECTED', 'CANCELLED')),
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, policy_id) REFERENCES approval_policies (organization_id, id),
    FOREIGN KEY (organization_id, requested_by) REFERENCES users (organization_id, id)
);

CREATE TABLE approval_steps (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    request_id      uuid NOT NULL,
    step_no         integer NOT NULL CHECK (step_no >= 1),
    role_code       text NOT NULL,
    decided_by      uuid,
    decision        text CHECK (decision IS NULL OR decision IN ('APPROVE', 'REJECT', 'RETURN')),
    comment         text,
    decided_at      timestamptz,
    UNIQUE (organization_id, id),
    UNIQUE (request_id, step_no),
    FOREIGN KEY (organization_id, request_id) REFERENCES approval_requests (organization_id, id)
);

CREATE TABLE status_history (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    entity_type     text NOT NULL,
    entity_id       uuid NOT NULL,
    from_status     text,
    to_status       text NOT NULL,
    actor_id        uuid,
    reason          text,
    at              timestamptz NOT NULL DEFAULT clock_timestamp()
);
