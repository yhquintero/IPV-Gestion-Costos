-- Datos de referencia del sistema (no son datos de cliente). Semillas de negocio van en tools/seed.

INSERT INTO cost_sheet_status_transitions (from_status, to_status) VALUES
    ('BORRADOR', 'EN_REVISION'),
    ('EN_REVISION', 'BORRADOR'),
    ('EN_REVISION', 'VALIDADA'),
    ('EN_REVISION', 'ANULADA'),
    ('VALIDADA', 'BORRADOR'),
    ('VALIDADA', 'APROBADA'),
    ('VALIDADA', 'ANULADA'),
    ('APROBADA', 'VIGENTE'),
    ('APROBADA', 'ANULADA'),
    ('VIGENTE', 'REEMPLAZADA'),
    ('VIGENTE', 'ANULADA');

INSERT INTO ipv_control_status_transitions (from_status, to_status) VALUES
    ('PENDIENTE', 'EN_PROCESO'),
    ('EN_PROCESO', 'VALIDADO'),
    ('EN_PROCESO', 'CON_DIFERENCIAS');

INSERT INTO permissions (code, description) VALUES
    ('costs:view', 'Ver costos, fichas e importes'),
    ('costing:submit', 'Enviar ficha a revisión'),
    ('costing:validate', 'Validar o devolver ficha'),
    ('costing:approve', 'Aprobar o devolver ficha'),
    ('costing:activate', 'Activar ficha vigente'),
    ('costing:annul', 'Anular ficha'),
    ('catalog:edit', 'Editar catálogo y valores IPV'),
    ('ipvcontrol:capture', 'Crear y capturar Control IPV'),
    ('ipvcontrol:close', 'Validar o cerrar Control IPV'),
    ('inventory:move', 'Registrar movimientos y conteos'),
    ('rates:manual', 'Cargar tasa manual'),
    ('rules:edit', 'Editar conjuntos de reglas'),
    ('users:manage', 'Gestionar usuarios y roles'),
    ('DATA_EXPORT', 'Exportar datos'),
    ('ADVANCED_AUDIT', 'Consultar auditoría avanzada');

INSERT INTO roles (id, organization_id, code, name, is_system) VALUES
    ('00000000-0000-7000-8000-000000000001', NULL, 'PLATFORM_ADMIN', 'Administrador de plataforma', true),
    ('00000000-0000-7000-8000-000000000002', NULL, 'ORG_ADMIN', 'Administrador de organización', true),
    ('00000000-0000-7000-8000-000000000003', NULL, 'COMPANY_ADMIN', 'Administrador de empresa', true),
    ('00000000-0000-7000-8000-000000000004', NULL, 'COSTEADOR', 'Costeador', true),
    ('00000000-0000-7000-8000-000000000005', NULL, 'REVISOR', 'Revisor', true),
    ('00000000-0000-7000-8000-000000000006', NULL, 'APROBADOR', 'Aprobador', true),
    ('00000000-0000-7000-8000-000000000007', NULL, 'CONTROLADOR_IPV', 'Controlador IPV', true),
    ('00000000-0000-7000-8000-000000000008', NULL, 'ALMACENERO', 'Almacenero', true),
    ('00000000-0000-7000-8000-000000000009', NULL, 'ECONOMICO', 'Económico', true),
    ('00000000-0000-7000-8000-00000000000a', NULL, 'AUDITOR', 'Auditor', true),
    ('00000000-0000-7000-8000-00000000000b', NULL, 'LECTOR', 'Lector', true);

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('PLATFORM_ADMIN', 'ORG_ADMIN');

INSERT INTO units (id, code, name, dimension, to_base_factor) VALUES
    ('00000000-0000-7000-8000-000000000101', 'KG', 'Kilogramo', 'MASA', 1),
    ('00000000-0000-7000-8000-000000000102', 'G', 'Gramo', 'MASA', 0.001),
    ('00000000-0000-7000-8000-000000000103', 'L', 'Litro', 'VOLUMEN', 1),
    ('00000000-0000-7000-8000-000000000104', 'ML', 'Mililitro', 'VOLUMEN', 0.001),
    ('00000000-0000-7000-8000-000000000105', 'U', 'Unidad', 'CONTEO', 1);

INSERT INTO rate_instruments (code, display_name, kind) VALUES
    ('USD', 'Dólar estadounidense', 'ISO4217'),
    ('EUR', 'Euro', 'ISO4217'),
    ('CUP', 'Peso cubano', 'ISO4217'),
    ('MLC', 'MLC (etiqueta de fuente)', 'LABEL'),
    ('CAD', 'Dólar canadiense', 'ISO4217'),
    ('MXN', 'Peso mexicano', 'ISO4217'),
    ('ZELLE', 'ZELLE (etiqueta de fuente)', 'LABEL'),
    ('CLA', 'CLA (etiqueta de fuente)', 'LABEL');

INSERT INTO rate_provider_mappings (provider, provider_code, instrument_code) VALUES
    ('ELTOQUE', 'USD', 'USD'),
    ('ELTOQUE', 'ECU', 'EUR'),
    ('ELTOQUE', 'MLC', 'MLC');
