# Migraciones Flyway

Fuente de verdad del esquema PostgreSQL (Fase 2). Inmutables una vez fusionadas.

Ubicación: `server/db/migration/V*.sql`

Se copian al classpath de `server:app` como `db/migration` y las pruebas las aplican con Testcontainers (`postgres:17`).

| Versión | Contenido |
|---|---|
| V1 | Extensiones, roles (`app_rw` sin `BYPASSRLS`), funciones |
| V2 | Tenencia, identidad, acceso |
| V3 | Catálogo, valores IPV, inventario |
| V4 | Fichas, reglas, aprobaciones |
| V5 | Control IPV, numeración |
| V6 | Tasas, comercial, licencias |
| V7 | Auditoría, sync, notificaciones |
| V8 | RLS forzado y GRANTs |
| V9 | Triggers I‑05…I‑20 |
| V10 | Permisos, transiciones, unidades, instrumentos |
| V11 | Login SECURITY DEFINER, matriz de roles, `idempotency_keys` |
