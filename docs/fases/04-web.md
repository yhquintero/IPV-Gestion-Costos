# Fase 4 · Web (Next.js + BFF)

| | |
|---|---|
| **Estado** | En curso — sitio, panel, BFF y E2E escritos |
| **Fecha** | 2026‑10‑01 |
| **Gate** | Playwright recorre borrador→vigente→control; axe sin críticas; CSP estricta; ZAP *baseline* (CI / entorno con red) |

## Entregables

| Pieza | Ubicación |
|---|---|
| Next.js 15.5.27 App Router + Tailwind | `web/` |
| BFF (login/logout/proxy) con cookies HttpOnly | `web/app/api/bff/` |
| Almacén de demostración si `API_URL` está vacío | `web/lib/mock-backend.ts` |
| Sitio público (USD + CUP derivado etiquetado) | `web/app/(public)/` |
| Panel por rol | `web/app/(panel)/app/` |
| Plataforma | `web/app/(panel)/plataforma/` (organizaciones, catálogo, contratos, licencias · Fase 7) |
| CSP con *nonce* + cabeceras | `web/middleware.ts`, `web/next.config.ts` |
| Playwright + axe | `web/e2e/` |
| CI web | `.github/workflows/ci-web.yml` |

## Corte vertical cubierto en el navegador

Login (cookie HttpOnly, sin JWT en JS) → catálogo → valores IPV → ficha BORRADOR (línea 1.005 × 10 = **10.05** HALF_UP) → EN_REVISION → VALIDADA (revisor, cuatro ojos) → APROBADA → VIGENTE → Control IPV numerado por el servidor → `GET /audit/verify`.

Cuentas sintéticas: `*@alpha.test` / `Seed-Passw0rd!`.

## Cómo verificar

```bash
cd web
npm ci
npm run typecheck
npm run lint
npm run build
# con el servidor en marcha (`npm start` o `npm run dev`):
npm run test:bff          # corte vertical HTTP (sin navegador)
npx playwright install chromium
npm run test:e2e          # UI + axe
```

`APP_ENV=prod` **exige** `API_URL` (el BFF se niega a usar el almacén de demostración).

## ZAP *baseline*

No se ejecutó en este entorno (sin contenedor OWASP ZAP). Las cabeceras (`CSP`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `COOP`) y el BFF (cookies HttpOnly, CSRF de doble envío) están listas para el *baseline* en CI/staging. Fase 9 lo endurece.

## Lo que no entra aquí

- Escritura offline (Fase 6)
- Keygen / comercial (Fase 7)
- Tasas reales de elTOQUE (Fase 8) — semilla TEST etiquetada
- ⛔ D‑02, D‑04, D‑05, D‑06
