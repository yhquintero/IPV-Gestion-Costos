#!/bin/sh
# Copia lógica diaria a /backups. Cifrado y destino externo: runbook (D-22).
# No es pgBackRest; es el mínimo del piloto. No sustituye un simulacro medido.
set -eu
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="/backups/ipvgc-${STAMP}.sql.gz"
pg_dump --no-owner --format=plain | gzip -c > "$OUT"
# Conservar 7 copias locales; el offsite es manual hasta D-22.
ls -1t /backups/ipvgc-*.sql.gz 2>/dev/null | tail -n +8 | xargs -r rm --
echo "wrote $OUT"
