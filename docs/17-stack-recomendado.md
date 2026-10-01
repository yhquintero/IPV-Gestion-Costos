# 17. Stack recomendado

> Entregable §39‑17 · [Índice](README.md) · Justificación de la comparación de backends: [doc 15](15-trade-offs.md#152-comparación-de-opciones-de-backend). **No se fijan números de versión en este documento**: se verifica la versión estable vigente al **iniciar cada fase** y se registra en `libs.versions.toml`, *lockfiles* y ADR. (Los repos origen fijan versiones de 2023‑2024: Kotlin 2.0.21, AGP 8.5.2/8.7.3, Compose BOM 2024.10.01, Room 2.6.1, Hilt 2.52, WorkManager 2.9.1 → se actualizan.)

## 17.1 Resumen por capa

| Capa | Elección provisional 🧭 | Motivo | Descartado y por qué |
|---|---|---|---|
| **Backend** | **Kotlin + Spring Boot** (monolito modular) sobre JDK LTS | Seguridad y trazabilidad maduras; **motor `core:domain` compartido con Android**; autoalojable | NestJS/FastAPI (duplican el motor), Ktor (más ensamblaje manual), Supabase/Firebase (dependencia de SaaS estadounidense y modelo) |
| **Base de datos** | **PostgreSQL** (versión mayor estable vigente) | RLS, restricciones de exclusión, `NUMERIC`, JSONB, particionado, madurez | SQLite (un solo escritor, sin RLS), NoSQL |
| **Web** | **Next.js (App Router) + React + TypeScript estricto**, con BFF | SSG público rápido; tokens fuera de JS; ecosistema | SPA estática (alternativa), SvelteKit |
| **Android** | **Kotlin + Jetpack Compose + Material 3**, MVVM/UDF, **Hilt**, **Room + SQLCipher**, **Retrofit/OkHttp**, **WorkManager**, Coroutines/Flow | Pedido del prompt; reutiliza el esqueleto de `inventario` | XML Views (legado), EncryptedSharedPreferences (obsoleta) |
| **Contrato** | **OpenAPI 3.1** como fuente de verdad | Clientes tipados, pruebas de contrato, detección de rupturas | Contratos implícitos |
| **Proxy/TLS** | **Caddy** | TLS automático, configuración simple (ya usado en `inventario`) | Nginx (más manual) |
| **Contenedores** | Docker Compose, imágenes no‑root | Un servidor; reproducible | Kubernetes (excesivo ahora) |
| **CI/CD** | GitHub Actions | Ya presente en los repos origen | — |

## 17.2 Backend

| Aspecto | Elección |
|---|---|
| HTTP | Spring Web MVC (con hilos virtuales si la versión de JDK lo permite); sin WebFlux salvo necesidad probada |
| Seguridad | Spring Security (servidor de recursos JWT, seguridad a nivel de método), Argon2id (Spring Security Crypto + BouncyCastle), Nimbus JOSE para JWT, TOTP (RFC 6238) con biblioteca revisada |
| Modularidad | Spring Modulith (o ArchUnit) para **verificar** fronteras |
| Persistencia | **jOOQ** (SQL tipado generado desde las migraciones) + **Flyway**; HikariCP; sin ORM que oculte SQL en dinero/versionado/RLS |
| Validación | Bean Validation + validación de dominio en `core:domain` |
| Resiliencia | Resilience4j (cortacircuitos, reintentos), Caffeine (caché), ShedLock o bloqueo asesor de PostgreSQL (planificador con líder), Bucket4j (límites) |
| OpenAPI | springdoc‑openapi, con el contrato revisado en `contracts/openapi/` |
| Observabilidad | Micrometer → Prometheus, registro JSON, identificador de petición, OpenTelemetry opcional |
| Exportación | Apache POI (XLSX), OpenPDF (PDF), CSV |
| Pruebas | JUnit 5, Kotest (propiedades), Testcontainers (PostgreSQL), WireMock (proveedores), pruebas de arquitectura |

## 17.3 Base de datos

- PostgreSQL con extensiones `pgcrypto`, `btree_gist`, `citext`, `pg_trgm`.
- **RLS forzado**, roles `app_rw`, `migrator`, `audit_writer`, `reporting_ro` ([doc 11](11-estrategia-seguridad.md#117-protección-de-datos)).
- Particionado mensual de `audit_events` y de las tablas de ejecuciones/muestras si crecen.
- Conexiones: pool acotado; PgBouncer solo si hace falta.

## 17.4 Web

| Aspecto | Elección |
|---|---|
| UI | Tailwind + componentes accesibles (Radix/shadcn) |
| Datos | TanStack Query y TanStack Table; cliente tipado generado desde OpenAPI |
| Formularios | React Hook Form + Zod |
| i18n | next‑intl (español por defecto) |
| Gráficos | Recharts o ECharts |
| Calidad | ESLint, Prettier, `tsc` estricto, Vitest, **Playwright** (E2E) y **axe** (accesibilidad) |

## 17.5 Android

| Módulo/uso | Elección |
|---|---|
| Arquitectura | MVVM con flujo unidireccional (StateFlow), multimódulo ([doc 4](04-modulos.md#44-módulos-de-la-app-android-gradle)) |
| Navegación y listas | Navigation Compose, Paging 3 |
| Datos | Room + **SQLCipher** (`net.zetetic:sqlcipher-android` con `SupportOpenHelperFactory`), kotlinx.serialization |
| Red | Retrofit + OkHttp (gzip, pines, interceptor de idempotencia) |
| Seguridad | Android Keystore, BiometricPrompt, **DataStore + Keystore/Tink** (no `security-crypto`) |
| Segundo plano | WorkManager |
| Rendimiento en gama baja | Baseline Profiles, R8, medición con Macrobenchmark (opcional) |
| Pruebas | JUnit, Truth, Turbine, MockK, pruebas de migración de Room, pruebas de UI de Compose |

## 17.6 Contrato, calidad y seguridad de la cadena

- **Contrato**: OpenAPI 3.1; clientes generados (TypeScript) y DTO compartidos con Android; detección de cambios incompatibles (`oasdiff`) y pruebas de contrato (Schemathesis).
- **Calidad**: `detekt`, `ktlint`/Spotless, Android Lint, ESLint/`tsc`.
- **Seguridad**: gitleaks, CodeQL o Semgrep, OWASP dependency‑check/Trivy, `npm audit`, **SBOM CycloneDX**, ZAP *baseline*/API, Renovate ([doc 11](11-estrategia-seguridad.md#1111-cadena-de-suministro-y-ciclo-de-desarrollo-seguro)).
- **Carga**: k6 contra `staging`.

## 17.7 Despliegue

Topología y variantes: [doc 2](02-diagrama-arquitectura.md#23-topología-de-despliegue-inicial). Contenedores no‑root con sistema de archivos de solo lectura y `cap_drop ALL` (se **reutiliza** el endurecimiento de `docker-compose.yml` de IPV); solo Caddy publica puertos; secretos por variables o archivos montados ([doc 20](20-secretos-y-configuracion.md)).

## 17.8 Dimensionamiento (estimación gruesa a validar en la Fase 10)

| Componente | Memoria aproximada |
|---|---|
| PostgreSQL | 0,5‑1 GB |
| API (JVM ajustada) | 0,5‑0,8 GB |
| Next.js | 0,2‑0,4 GB |
| Keygen CE (Rails + proceso de trabajo + Redis + su BD), **si D‑05 = CE** | 1‑1,5 GB |
| Caddy | < 0,1 GB |
| **Total** | **≈ 2,3‑3,8 GB** → servidor de piloto de **4 vCPU / 8 GB** (sin Keygen CE bastaría uno menor) |

No se estiman costes en dinero: dependen de proveedor y jurisdicción ([D‑06](16-decisiones-pendientes.md#d-06)).

## 17.9 Copias de seguridad y recuperación

| Elemento | Propuesta 🧭 (objetivos RPO/RTO ⛔ [D‑22](16-decisiones-pendientes.md#d-22)) |
|---|---|
| Copia continua | Archivo de WAL + copia base periódica (pgBackRest o WAL‑G) |
| Cifrado | Cifrado **antes** de salir del servidor; clave de copias separada y custodiada ([R‑13](14-riesgos.md#r-13)) |
| Destino | Almacenamiento **externo** al servidor (regla 3‑2‑1) |
| Verificación | **Simulacro periódico**: restaurar en una BD de pruebas, ejecutar comprobaciones de integridad **incluida la verificación de bloques de auditoría**, y medir RTO |
| Efecto en clientes | Tras una restauración se incrementa `server_epoch`: los dispositivos detectan `RESYNC_REQUIRED` y reconcilian sin perder mutaciones pendientes (escenario E‑5, [ADR‑0001](adr/0001-reabrir-offline-y-multisucursal.md#3-condiciones-para-reabrir-y-gates-verificables)) |
| Claves | Procedimiento de custodia y rotación ([doc 20](20-secretos-y-configuracion.md)) |

## 17.10 Lo que se evita de forma explícita

`Double` para dinero · `EncryptedSharedPreferences`/`security-crypto` (obsoleta ✅) · preguntas de seguridad · credenciales demo · estado de aplicación en un único JSON · confianza en CA de usuario en *release* · secretos en clientes o repositorio · *scraping* de elTOQUE · banderas "premium" locales · criptografía propia.
