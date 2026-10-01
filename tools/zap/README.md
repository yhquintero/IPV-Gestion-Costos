# ZAP baseline (Fase 9)

No se ejecutó en este entorno (sin *staging* ni contenedor OWASP ZAP).

Cuando exista staging:

```bash
docker run --rm -t ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \
  -t https://staging.ejemplo.tld -I
```

Esperado: sin hallazgos **críticos/altos**. Medios se trian. El panel ya envía CSP,
`X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `COOP`.
La API añade `nosniff`, `DENY`, `no-store` (Fase 9).
