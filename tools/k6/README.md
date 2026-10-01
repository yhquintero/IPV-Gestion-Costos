# k6 (Fase 9)

El script `auth-and-health.js` es para **staging**. No se corre aquí (sin entorno de carga)
ni contra producción. Umbrales iniciales 🧭: error rate < 5 %, p95 < 800 ms, 5 VUs / 30 s.

```bash
k6 run -e BASE=https://staging.ejemplo.tld/api/v1 tools/k6/auth-and-health.js
```
