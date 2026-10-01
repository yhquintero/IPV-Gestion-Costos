-- Catálogo, valores IPV e inventario (doc 3.3.2).

CREATE TABLE units (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    dimension       text NOT NULL CHECK (dimension IN ('MASA', 'VOLUMEN', 'CONTEO')),
    to_base_factor  numeric(19, 6) NOT NULL CHECK (to_base_factor > 0)
);

CREATE TABLE categories (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    code            text NOT NULL,
    name            text NOT NULL,
    kind            text NOT NULL CHECK (kind IN ('BEBIDAS', 'COMIDAS', 'PRODUCTOS', 'SERVICIOS', 'CUSTOM')),
    parent_id       uuid,
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
    FOREIGN KEY (organization_id, parent_id) REFERENCES categories (organization_id, id)
);

CREATE TABLE suppliers (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    name            text NOT NULL,
    tax_id          text,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    origin_created_at timestamptz,
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid,
    origin_device_id uuid,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id)
);

CREATE TABLE raw_materials (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    category_id     uuid NOT NULL,
    base_unit_id    uuid NOT NULL REFERENCES units (id),
    code            text NOT NULL,
    name            text NOT NULL,
    min_stock       numeric(19, 6) NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
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
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id),
    FOREIGN KEY (organization_id, category_id) REFERENCES categories (organization_id, id)
);

CREATE TABLE products (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    category_id     uuid NOT NULL,
    kind            text NOT NULL CHECK (kind IN ('PRODUCT', 'SERVICE')),
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
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id),
    FOREIGN KEY (organization_id, category_id) REFERENCES categories (organization_id, id)
);

-- I-10: exactamente uno de raw_material_id / product_id; vigencia sin solape.
CREATE TABLE ipv_values (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    branch_id       uuid,
    raw_material_id uuid,
    product_id      uuid,
    supplier_id     uuid,
    currency        text NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    unit_price      numeric(19, 4) NOT NULL CHECK (unit_price >= 0), -- I-11
    valid_from      date NOT NULL,
    valid_to        date,
    source_ref      text,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    origin_created_at timestamptz,
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid,
    origin_device_id uuid,
    UNIQUE (organization_id, id),
    CHECK ((raw_material_id IS NOT NULL) <> (product_id IS NOT NULL)),
    CHECK (valid_to IS NULL OR valid_to > valid_from),
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches (organization_id, id),
    FOREIGN KEY (organization_id, raw_material_id) REFERENCES raw_materials (organization_id, id),
    FOREIGN KEY (organization_id, product_id) REFERENCES products (organization_id, id),
    FOREIGN KEY (organization_id, supplier_id) REFERENCES suppliers (organization_id, id)
);

ALTER TABLE ipv_values ADD CONSTRAINT ipv_values_no_overlap
    EXCLUDE USING gist (
        organization_id WITH =,
        company_id WITH =,
        coalesce(branch_id, '00000000-0000-0000-0000-000000000000') WITH =,
        coalesce(raw_material_id, '00000000-0000-0000-0000-000000000000') WITH =,
        coalesce(product_id, '00000000-0000-0000-0000-000000000000') WITH =,
        currency WITH =,
        daterange(valid_from, COALESCE(valid_to, 'infinity'::date), '[)') WITH &&
    ) WHERE (deleted_at IS NULL);

CREATE TABLE warehouses (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    branch_id       uuid NOT NULL,
    name            text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    origin_created_at timestamptz,
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid,
    origin_device_id uuid,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, branch_id) REFERENCES branches (organization_id, id)
);

-- I-17 libro mayor de solo anexado.
CREATE TABLE inventory_movements (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    warehouse_id    uuid NOT NULL,
    raw_material_id uuid NOT NULL,
    type            text NOT NULL CHECK (type IN ('IN', 'OUT', 'ADJUST', 'TRANSFER', 'COUNT')),
    quantity        numeric(19, 6) NOT NULL CHECK (quantity > 0), -- I-11
    occurred_at     timestamptz NOT NULL,
    business_date   date NOT NULL,
    mutation_id     uuid,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by      uuid,
    origin_device_id uuid,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, warehouse_id) REFERENCES warehouses (organization_id, id),
    FOREIGN KEY (organization_id, raw_material_id) REFERENCES raw_materials (organization_id, id)
);

CREATE TABLE stock_balances (
    organization_id uuid NOT NULL,
    warehouse_id    uuid NOT NULL,
    raw_material_id uuid NOT NULL,
    quantity        numeric(19, 6) NOT NULL,
    as_of           timestamptz NOT NULL,
    PRIMARY KEY (organization_id, warehouse_id, raw_material_id),
    FOREIGN KEY (organization_id, warehouse_id) REFERENCES warehouses (organization_id, id),
    FOREIGN KEY (organization_id, raw_material_id) REFERENCES raw_materials (organization_id, id)
);

CREATE TABLE inventory_counts (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    warehouse_id    uuid NOT NULL,
    status          text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'COUNTED', 'POSTED', 'CANCELLED')),
    counted_at      timestamptz,
    business_date   date NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    origin_created_at timestamptz,
    version         bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    deleted_at      timestamptz,
    created_by      uuid,
    updated_by      uuid,
    origin_device_id uuid,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, warehouse_id) REFERENCES warehouses (organization_id, id)
);

CREATE TABLE inventory_count_lines (
    id              uuid PRIMARY KEY DEFAULT app.uuid_v7(),
    organization_id uuid NOT NULL,
    count_id        uuid NOT NULL,
    raw_material_id uuid NOT NULL,
    expected_qty    numeric(19, 6),
    observed_qty    numeric(19, 6) NOT NULL,
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, count_id) REFERENCES inventory_counts (organization_id, id),
    FOREIGN KEY (organization_id, raw_material_id) REFERENCES raw_materials (organization_id, id)
);

CREATE TABLE company_allowed_currencies (
    organization_id uuid NOT NULL,
    company_id      uuid NOT NULL,
    currency        text NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    PRIMARY KEY (organization_id, company_id, currency),
    FOREIGN KEY (organization_id, company_id) REFERENCES companies (organization_id, id)
);
