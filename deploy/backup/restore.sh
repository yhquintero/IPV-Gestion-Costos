#!/bin/sh
# Restaurar un dump en una BD de PRUEBA. Nunca contra producción sin runbook.
# Tras restaurar el servidor de sync: incrementar epoch (E-5 / RESYNC_REQUIRED).
set -eu
DUMP=${1:?usage: restore.sh /backups/ipvgc-YYYYMMDD.sql.gz}
TARGET_DB=${2:-ipvgc_restore}
psql -d postgres -c "CREATE DATABASE ${TARGET_DB};" || true
gzip -dc "$DUMP" | psql -d "$TARGET_DB"
echo "restored into ${TARGET_DB}. Run GET /api/v1/audit/verify next."
echo "Then: UPDATE sync_server_state SET epoch = epoch + 1;"
