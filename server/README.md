# Servidor (`server:app`)

Spring Boot de las Fases 2–3: Flyway + JDBC + API REST. Los paquetes `cu.ipvgc.server.*` mapean 1:1 a los módulos del [doc 4](../docs/04-modulos.md); la separación en artefactos Gradle queda para endurecimiento.

```bash
# Pruebas de invariantes I-01…I-20 (requieren Docker)
gradle :server:app:test
```

Migraciones: [`db/migration`](db/migration). Roles de BD: `app_rw` (sin `BYPASSRLS`), `migrator`, `audit_writer`, `reporting_ro`, `retention_job`.
