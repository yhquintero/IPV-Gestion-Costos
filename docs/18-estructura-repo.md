# 18. Estructura inicial del repositorio

> Entregable §39‑18 · [Índice](README.md) · Monorepo propuesto 🧭. **En esta fase solo existen** `README.md`, `.gitignore` y `docs/`; el resto se crea en las fases indicadas, **tras validar el diseño**.

## 18.1 Árbol

```text
IPV-Gestion-Costos/
├─ README.md                         ← (existe) presentación y enlaces
├─ .gitignore                        ← (existe) evita secretos y artefactos
├─ docs/                             ← (existe) este paquete de análisis · Fase 1
│  ├─ README.md  01-…20-*.md
│  ├─ adr/                           ← decisiones de arquitectura (ADR‑0001…)
│  └─ anexos/                        ← brechas de repos origen, fuentes verificadas
│
├─ contracts/
│  └─ openapi/ipv-gc.yaml            ← contrato fuente de verdad · Fase 3
│
├─ core/                             ← build Gradle propio (sin Android SDK)
│  └─ domain/                        ← Kotlin/JVM: Money, cálculo, reglas, estados,
│     ├─ src/main/…                    evaluador de licencia · Fase 2‑3
│     └─ src/test/resources/golden/  ← vectores dorados compartidos (JVM/Android/web)
│
├─ server/                           ← build Gradle (usa core:domain)
│  ├─ app/                           ← Spring Boot: composición, seguridad, configuración
│  ├─ modules/                       ← identity · access · tenancy · catalog · ipvvalues
│  │                                   inventory · costing · rules · workflow · ipvcontrol
│  │                                   rates · licensing · commerce · audit · sync
│  │                                   reporting · notifications · platformadmin
│  └─ db/migration/                  ← Flyway V1__…sql · Fase 2
│
├─ android/                          ← build Gradle propio (includeBuild de ../core)
│  ├─ app/  core-network/  core-data/  core-security/  core-designsystem/
│  ├─ feature-auth/ feature-home/ feature-catalog/ feature-ipv/ feature-costing/
│  │  feature-control/ feature-inventory/ feature-rates/ feature-license/ feature-sync/
│  └─ gradle/libs.versions.toml      ← catálogo de versiones · Fase 5
│
├─ web/                              ← Next.js (App Router) · Fase 4
│  └─ app/(public)  app/(panel)  app/(plataforma)
│
├─ deploy/                           ← Fase 10 (y spikes)
│  ├─ compose/                       ← docker-compose.yml, Caddyfile
│  ├─ keygen/                        ← policies.yaml + CLI de aprovisionamiento idempotente
│  ├─ backup/                        ← scripts de copia y restauración
│  └─ runbooks/                      ← guías operativas y de incidentes
│
├─ tools/
│  ├─ seed/                          ← generador de datos 100 % sintéticos
│  ├─ audit-verify/                  ← verificador de auditoría sin conexión
│  └─ mock-eltoque/                  ← servidor simulado de la API de tasas
│
└─ .github/
   ├─ workflows/                     ← CI por componente (con filtros de ruta)
   ├─ CODEOWNERS
   └─ pull_request_template.md
```

**Por qué dos *builds* Gradle (`core`+`server` y `android`)**: compilar el servidor no debe exigir el SDK de Android. Android incorpora `core:domain` con **`includeBuild("../core")`** (compilación compuesta), de modo que el **mismo código** corre en ambos sin publicarlo.

En **Fase 2** hay un *build* raíz que incluye `core:domain`, `server:app` y `tools:seed` (sin Android). El *build* Android se añade en la Fase 5 como proyecto aparte.

## 18.2 Convenciones

| Tema | Regla |
|---|---|
| Idioma | Documentación y UI en español; **código, tablas y columnas en inglés** (`snake_case` en SQL) con el glosario del [doc 3](03-modelo-er.md#32-glosario-es-en) |
| Ramas y PR | Trabajo en ramas cortas; PR con plantilla (alcance, riesgos, pruebas, captura); revisión obligatoria |
| Commits | *Conventional Commits* (`feat:`, `fix:`, `docs:`…) |
| ADR | Toda decisión de arquitectura nueva = un ADR; los aceptados no se editan, se reemplazan |
| Migraciones | Flyway; inmutables una vez fusionadas; con prueba |
| Secretos | **Nunca** en el repositorio ([doc 20](20-secretos-y-configuracion.md)); `.gitignore` excluye `.env*` salvo `.env.example` |
| Datos | Solo sintéticos en el repositorio |
| Estilo | `ktlint`/`detekt`, ESLint/Prettier; CI falla ante infracciones |

## 18.3 CI/CD (flujos previstos)

| Flujo | Contenido |
|---|---|
| `ci-core-server` | Build, pruebas unitarias e integración (Testcontainers), `detekt`/`ktlint`, pruebas de arquitectura, SBOM |
| `ci-android` | Lint, pruebas unitarias, migraciones de Room, *build* debug/release sin firmar, análisis del APK (sin secretos) |
| `ci-web` | `tsc`, ESLint, Vitest, Playwright, axe |
| `ci-contract` | Validación y *diff* de OpenAPI, pruebas de contrato |
| `ci-security` | gitleaks, CodeQL/Semgrep, Trivy (código y contenedores), dependency‑check, ZAP contra *staging* |
| `ci-sync` | Suite E‑1…E‑5 (gate G‑3) en cada cambio que toque sincronización |
| `ci-license` | Evaluador por tabla, Fake `LicenseProvider`, renovación FROM_EXPIRY |
| `release-*` | Firma y publicación de imágenes/APK con secretos de alcance mínimo |

## 18.4 Relación con los repositorios origen

- **No se copian** al nuevo repositorio (evita arrastrar datos reales, credenciales demo y licencias propietarias).
- Su conocimiento se traslada vía [anexo A](anexos/A-analisis-de-brechas.md) y casos de aceptación.
- Qué hacer con ellos cuando el producto unificado alcance paridad (archivar, congelar o seguir) es decisión del propietario.

## 18.5 Qué se crea y cuándo

| Fase | Se crea |
|---|---|
| 1 (ahora) | `docs/`, `README.md`, `.gitignore` |
| 2 | Esqueleto Gradle (`core`, `server`), migraciones, `tools/seed`, CI base — **hecho** (ver [fases/02](fases/02-modelo-de-datos.md)) |
| 3 | API en `server/app` (paquetes = módulos), `contracts/openapi`, `GET /audit/verify` — **hecho** (ver [fases/03](fases/03-backend-api.md)) |
| 4 | `web/` (Next.js + BFF + Playwright) — **hecho** (ver [fases/04](fases/04-web.md)) |
| 5 | `android/` (multimódulo, `includeBuild` de `core`) — **hecho** (ver [fases/05](fases/05-android.md)) |
| 6 | Sync (módulo `sync`, outbox Android, suite E‑1…E‑5) — **hecho** (ver [fases/06](fases/06-sync-offline.md)) |
| 7 | `deploy/keygen`, módulos `licensing` y `commerce` — **hecho** (Fake; ver [fases/07](fases/07-licencias.md)) |
| 8 | Proveedores de tasas, `tools/mock-eltoque` |
| 9 | Pruebas de carga y seguridad, endurecimiento |
| 10 | `deploy/compose`, `deploy/backup`, `deploy/runbooks`, flujos de release |
