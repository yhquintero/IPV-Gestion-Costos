# Fase 10 · Despliegue

| | |
|---|---|
| **Estado** | Artefactos de piloto escritos. **No hay entorno de producción ni simulacro medido** |
| **Fecha** | 2026‑10‑01 |
| **Gate** | Restauración RPO/RTO ⛔ D-22; alertas no probadas; **G-4 no firmado** |

## Entregables

| Pieza | Ubicación |
|---|---|
| Compose (Caddy + web + api + postgres + dump diario) | `deploy/compose/` |
| Caddyfile (TLS, HSTS, proxy `/api`) | `deploy/compose/Caddyfile` |
| Dockerfiles no-root | `Dockerfile.api`, `Dockerfile.web` |
| Dump / restore | `deploy/backup/` |
| Runbooks | `deploy/runbooks/` |
| Plantilla G-4 | [10-g4-piloto.md](10-g4-piloto.md) |

## Qué no se afirma

- Hosting/jurisdicción (D-06).
- Let's Encrypt contra un dominio real.
- Copias cifradas offsite (3-2-1): el sidecar solo deja `.sql.gz` locales.
- Keygen CE en el compose (D-05).
- `APP_ENV=prod` + `ELTOQUE_PROVIDER_MODE=SEED` **falla a propósito**.
- Piloto con usuarios reales.

```bash
cd deploy/compose && docker compose config   # validar YAML
```
