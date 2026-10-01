#!/usr/bin/env bash
# Falla si el árbol Android contiene secretos de proveedores o claves privadas.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATTERN='ELTOQUE_API_KEY|KEYGEN_ACCOUNT_TOKEN|KEYGEN_PRODUCT_TOKEN|KEYGEN_TOKEN|BEGIN RSA PRIVATE|BEGIN EC PRIVATE|BEGIN OPENSSH|Seed-Passw0rd'
if grep -RInE --exclude-dir=build --exclude-dir=.gradle --exclude='*.apk' --exclude='assert-no-secrets.sh' "$PATTERN" "$ROOT"; then
  echo "secretos prohibidos en android/" >&2
  exit 1
fi
echo "android/: sin secretos de proveedor ni claves privadas"
