-- I-01 RLS forzado. El rol app_rw no tiene BYPASSRLS ni DDL.

CREATE OR REPLACE FUNCTION app.install_tenant_rls(p_table regclass) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', p_table);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', p_table);
    EXECUTE format(
        'DROP POLICY IF EXISTS tenant_isolation ON %s',
        p_table
    );
    EXECUTE format(
        $f$
        CREATE POLICY tenant_isolation ON %s
            USING (organization_id = app.current_organization_id())
            WITH CHECK (organization_id = app.current_organization_id())
        $f$,
        p_table
    );
END;
$$;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'companies', 'branches', 'users', 'role_assignments', 'mfa_factors',
        'devices', 'sessions', 'categories', 'suppliers', 'raw_materials',
        'products', 'ipv_values', 'warehouses', 'inventory_movements',
        'stock_balances', 'inventory_counts', 'inventory_count_lines',
        'company_allowed_currencies', 'cost_sheets', 'cost_sheet_versions',
        'cost_sheet_lines', 'rule_sets', 'rule_set_versions', 'rule_evaluations',
        'rule_overrides', 'approval_policies', 'approval_requests',
        'approval_steps', 'status_history', 'ipv_controls', 'ipv_control_lines',
        'document_sequences', 'rate_policies', 'contracts', 'contract_items',
        'payments', 'receipts', 'licenses', 'license_devices',
        'license_entitlements', 'license_events', 'audit_events', 'audit_blocks',
        'audit_event_counters', 'sync_change_log', 'sync_mutations',
        'sync_conflicts', 'notifications'
    ]
    LOOP
        PERFORM app.install_tenant_rls(t::regclass);
    END LOOP;
END
$$;

ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE organizations FORCE ROW LEVEL SECURITY;
CREATE POLICY organizations_isolation ON organizations
    USING (id = app.current_organization_id())
    WITH CHECK (id = app.current_organization_id());

ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;
CREATE POLICY roles_isolation ON roles
    USING (organization_id IS NULL OR organization_id = app.current_organization_id())
    WITH CHECK (organization_id IS NULL OR organization_id = app.current_organization_id());

GRANT USAGE ON SCHEMA public TO app_rw, audit_writer, reporting_ro, retention_job, migrator;

GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO app_rw;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_rw;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA app TO app_rw;

REVOKE DELETE ON ALL TABLES IN SCHEMA public FROM app_rw;
REVOKE UPDATE ON audit_events, audit_blocks, inventory_movements,
    exchange_rate_samples, rate_snapshots, rate_snapshot_items,
    sync_mutations, rule_set_versions
    FROM app_rw;
REVOKE TRUNCATE ON audit_events, audit_blocks FROM PUBLIC, app_rw, reporting_ro;

GRANT INSERT ON audit_events, audit_blocks TO audit_writer;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO reporting_ro;
GRANT SELECT, DELETE ON ALL TABLES IN SCHEMA public TO retention_job;

GRANT SELECT ON permissions, units, rate_instruments, rate_provider_mappings,
    rate_provider_runs, exchange_rate_samples, exchange_rate_current,
    rate_snapshots, rate_snapshot_items, price_catalog_items,
    cost_sheet_status_transitions, ipv_control_status_transitions,
    role_permissions, keygen_webhook_events, outbox_events
    TO app_rw;

GRANT INSERT ON exchange_rate_samples, rate_provider_runs, rate_snapshots,
    rate_snapshot_items, exchange_rate_current, outbox_events,
    keygen_webhook_events, role_permissions
    TO app_rw;

GRANT UPDATE ON exchange_rate_current, outbox_events, stock_balances,
    document_sequences
    TO app_rw;
