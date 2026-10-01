# Fases de implementación

Estado al **2026‑10‑01** en `arena/01a0f5bb-ipv-gestion-costos`. «En curso» = código en el repo, *gate* de salida **no** cerrado. ⛔ = decisión o entorno que no se finge.

| Fase | Objetivo | En el repo | Gate / qué falta | Pedirme después |
|---|---|---|---|---|
| **1** Análisis | Diseño y ADR | [docs](../README.md) | Entregada | Validar ADR‑0001; texto íntegro §36/§37 |
| **2** Modelo de datos | Flyway, RLS, `Money` | V1–V14, `core:domain` | I‑01…I‑20 en CI con JDK+Docker | Ejecutar `gradle test` en runner con JDK 21 |
| **3** Backend/API | Identidad, ficha, auditoría | `server/app`, OpenAPI 0.9.0 | IDOR/`audit/verify` en Testcontainers | Enrollment MFA UI; cifrar TOTP en reposo |
| **4** Web | Panel + BFF + CSP | `web/` | Playwright/axe en CI ubuntu (Chromium CDN bloqueado aquí) | `npm run test:e2e` en CI; ZAP *baseline* |
| **5** Android | Compose, SQLCipher, licencia | `android/` | MASVS L1 parcial; APK release firmado ⛔ | Instrumentación en dispositivo; pines TLS reales |
| **6** Sync/offline | E‑1…E‑5, etapa **6a** | dominio + API + Room v2 | G‑2 sin firma humana; 2 semanas 6a no corridas | Revisar [06-g2](06-g2-revision.md); piloto interno 6a |
| **7** Licencias | `LicenseProvider` Fake | Fake, V13, plataforma | Cloud/CE ⛔ **D‑05**; S‑1 ECDSA | Dictamen D‑05; spike S‑1 (CE local) |
| **8** elTOQUE | Parser, MOCK/SEED | `rates/`, `tools/mock-eltoque` | API live ⛔ **D‑04** | Token + S‑2; zona horaria; CAD/MXN/ZELLE/CLA |
| **9** Endurecimiento | Throttle, caos, gitleaks, matriz | [09](09-endurecimiento.md) | Pentest, ZAP, k6, restauración **no ejecutados** | Staging + k6/ZAP; pentest; §36 1:1 |
| **10** Despliegue | Compose, Caddy, dump, runbooks | [10](10-despliegue.md) | G‑4 no firmado; D‑06 hosting; simulacro ⛔ | Levantar compose; simulacro restore; firmar G‑4 |

## Bloqueos que condicionan producción

| Id | Tema | Sin esto no se puede |
|---|---|---|
| **D‑02** | Norma cubana de Ficha/IPV | Fórmulas y formatos «oficiales» |
| **D‑04** | Token y términos elTOQUE | Tasas live; `APP_ENV=prod` con semilla |
| **D‑05** | Dictamen Keygen Cloud/CE | Licencias reales; machine files ECDSA |
| **D‑06** | Hosting y jurisdicción | Dónde corre Compose y las copias |
| **D‑22** | RPO/RTO, guardia, presupuesto | Gate **G‑4** y simulacro medido |
| **G‑2** | Revisor técnico independiente | Offline editable a clientes reales |
