-- Control IPV y numeración de documentos (doc 3.3.4).

CREATE TABLE ipv_control_status_transitions (
    from_status text NOT NULL,
    to_status   text NOT NULL,
    PRIMARY KEY (from_status, to_status)
);

CREATE TABLE ipv_controls (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    branch_id       uuid NOT NULL,
    control_no      text, -- asignado por el servidor (I-19)
    mode            text NOT NULL CHECK (mode IN ('CONSISTENCIA', 'DERIVA_COSTOS', 'CONSUMO')),
    period_start    date NOT NULL,
    period_end      date NOT NULL,
    status          text NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (status IN ('PENDIENTE', 'EN_PROCESO', 'VALIDADO', 'CON_DIFERENCIAS')),
    closed_at       timestamptz,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    origin_created_at timestamptz,
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid,
    origin_device_id uuid,
    UNIQUE (organization_id, id),
    CHECK (period_end >= period_start),
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches (organization_id, id)
);

CREATE UNIQUE INDEX uq_ipv_controls_number
    ON ipv_controls (organization_id, company_id, control_no)
    WHERE control_no IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE ipv_control_lines (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    control_id      uuid NOT NULL,
    product_id      uuid NOT NULL,
    cost_sheet_version_id uuid NOT NULL,
    expected_qty    numeric(19, 6) NOT NULL CHECK (expected_qty >= 0),
    expected_unit_cost numeric(19, 4) NOT NULL CHECK (expected_unit_cost >= 0),
    observed_qty    numeric(19, 6) CHECK (observed_qty IS NULL OR observed_qty >= 0),
    observed_unit_cost numeric(19, 4) CHECK (observed_unit_cost IS NULL OR observed_unit_cost >= 0),
    observation_source text CHECK (observation_source IS NULL OR observation_source IN ('IPV_VALUES', 'INVENTORY_COUNT', 'MANUAL')),
    variance_value  numeric(19, 4),
    variance_pct    numeric(9, 6),
    reason_code     text,
    version_annulled boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, control_id) REFERENCES ipv_controls (organization_id, id),
    FOREIGN KEY (organization_id, product_id) REFERENCES products (organization_id, id),
    FOREIGN KEY (organization_id, cost_sheet_version_id) REFERENCES cost_sheet_versions (organization_id, id)
);

-- I-19 numeración con bloqueo de fila.
CREATE TABLE document_sequences (
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    doc_type        text NOT NULL,
    year            integer NOT NULL CHECK (year >= 2000),
    last_number     bigint NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    PRIMARY KEY (organization_id, company_id, doc_type, year),
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id)
);

CREATE OR REPLACE FUNCTION app.next_document_number(
    p_org uuid,
    p_company uuid,
    p_type text,
    p_year integer
) RETURNS bigint
LANGUAGE plpgsql AS $$
DECLARE
    n bigint;
BEGIN
    INSERT INTO document_sequences (organization_id, company_id, doc_type, year, last_number)
    VALUES (p_org, p_company, p_type, p_year, 1)
    ON CONFLICT (organization_id, company_id, doc_type, year)
    DO UPDATE SET last_number = document_sequences.last_number + 1
    RETURNING last_number INTO n;
    RETURN n;
END;
$$;
