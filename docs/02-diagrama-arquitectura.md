# 2. Diagrama de arquitectura (texto)

> Entregable §39‑2 · [Índice](README.md) · Todo lo marcado 🧭 es propuesta provisional.

## 2.1 Vista general

```text
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                        USUARIOS                                         │
│        Navegador (sitio público + panel Web)                 App Android (Kotlin)        │
└──────────────┬────────────────────────────────────────────────────────────┬─────────────┘
               │ HTTPS (TLS 1.2+/1.3, HSTS)                                  │ HTTPS (+ pines con respaldo)
               ▼                                                             │
┌───────────────────────────────────────────────┐                            │
│ BORDE · Caddy                                  │◄───────────────────────────┘
│ TLS automático · límites de tasa · cabeceras   │
└───────┬──────────────────────┬─────────────────┘
        │ /  (sitio y panel)   │ /api/*  y  /sync/*
        ▼                      ▼
┌────────────────────┐   ┌──────────────────────────────────────────────────────────────┐
│ WEB · Next.js      │   │ API REST · monolito modular (Kotlin + Spring Boot)           │
│ SSG público +      │──►│  ┌ núcleo ───────────────────────────────────────────────┐   │
│ panel con BFF      │   │  │ identity · access · tenancy · audit · sync            │   │
│ (cookie HttpOnly,  │   │  ├ negocio ──────────────────────────────────────────────┤   │
│ sin tokens en JS)  │   │  │ catalog · ipv-values · inventory · costing · rules    │   │
└────────────────────┘   │  │ workflow · ipv-control · reporting · notifications    │   │
                         │  ├ plataforma ───────────────────────────────────────────┤   │
                         │  │ rates · licensing · commerce · platform-admin         │   │
                         │  └───────────────────────────────────────────────────────┘   │
                         │  core:domain (Kotlin/JVM): cálculo, reglas, estados,         │
                         │  evaluador de licencias  ← el MISMO código corre en Android  │
                         └────────┬───────────────────┬──────────────────┬──────────────┘
                                  │ JDBC (RLS)         │ HTTPS saliente    │ HTTPS saliente
                                  ▼                    ▼                   ▼
                       ┌─────────────────┐  ┌────────────────────┐  ┌───────────────────────┐
                       │ PostgreSQL      │  │ ExchangeRateProvider│  │ LicenseProvider        │
                       │ (única BD; solo │  │ ElToqueApi → Cached │  │ Keygen Cloud | CE |    │
                       │  la API accede) │  │ Manual              │  │ propio (mismo formato) │
                       └────────┬────────┘  └─────────┬──────────┘  └───────────┬───────────┘
                                │                     ▼                         ▼
                  WAL + copias base cifradas    tasas.eltoque.com       api.keygen.sh  /  Keygen CE
                  → almacenamiento externo      (clave solo en servidor)  (token solo en servidor)
```

**Reglas visibles en el diagrama**

1. Web y Android **nunca** hablan con PostgreSQL ni con elTOQUE ni con Keygen: solo con la API.
2. Los secretos de proveedores viven únicamente en el servidor.
3. El mismo módulo `core:domain` calcula costos y evalúa reglas/licencias en servidor y en Android.

## 2.2 Dispositivo Android

```text
┌──────────────────────────────── App Android ────────────────────────────────┐
│ UI Compose (Material 3) ──► ViewModel (StateFlow) ──► Repositorios            │
│                                     │                                          │
│        core:domain ◄────────────────┤  (cálculo, reglas, estados, licencia)    │
│                                     ▼                                          │
│  Room + SQLCipher ◄── Outbox de mutaciones ◄── WorkManager (SyncWorker)        │
│  Android Keystore: clave de BD · refresh token · archivo de máquina            │
│  Retrofit/OkHttp (pines con respaldo, gzip) ─────────────────────► API REST     │
└────────────────────────────────────────────────────────────────────────────────┘
```

## 2.3 Topología de despliegue inicial

```text
 Servidor único (piloto)  ·  Docker Compose  ·  red interna "backend"
┌───────────────────────────────────────────────────────────────────────────────┐
│  caddy ──► web (Next.js)      ──► api (Spring Boot) ──► postgres               │
│   :443        no publica puerto        no publica puerto     volumen cifrado    │
│                                              │                                  │
│                                              └─► keygen (solo si D‑05 = CE)     │
│                                                   rails + worker + redis + BD   │
│  backup  (pgBackRest/restic) ──► almacenamiento externo cifrado                 │
│  observabilidad (opcional): Prometheus · Grafana · Loki                         │
└───────────────────────────────────────────────────────────────────────────────┘
 Solo caddy expone puertos. Contenedores no‑root, sistema de archivos de solo lectura,
 cap_drop ALL, sin secretos en imágenes ni en el repositorio.
```

**Variantes de despliegue** (⛔ [D‑06](16-decisiones-pendientes.md#d-06), [D‑07](16-decisiones-pendientes.md#d-07)):

| Variante | Descripción | Efecto sobre elTOQUE | Efecto sobre Keygen | Offline |
|---|---|---|---|---|
| **A. SaaS central** (defecto) | Una instancia multi‑organización. | Una sola clave, nunca sale del servidor. | Un solo proveedor. | Sin cambios. |
| **B. Instalación por cliente** | Una instancia por organización (red local o hosting propio). | **Cada instalación necesitaría su propia clave** (la clave es por aplicación y no puede entregarse a terceros). | Una instancia/cuenta por cliente o CE local. | Más sencillo en LAN. |
| **C. Híbrida** | Plano de control central (licencias, tasas) + datos por cliente. | La instalación del cliente consume tasas del plano central (⛔ confirmar que los términos lo permiten). | Central. | Igual que A/B. |

## 2.4 Fronteras de confianza

| Frontera | De → a | Amenaza principal | Control |
|---|---|---|---|
| TB‑1 | Internet → Borde | Escaneo, DoS, MITM | TLS, límites de tasa, HSTS |
| TB‑2 | Navegador → Next.js (BFF) | XSS, CSRF, robo de sesión | Cookie `HttpOnly; Secure; SameSite`, CSP estricta, sin tokens en JS |
| TB‑3 | Android → API | **Dispositivo no confiable** (root, APK modificado, reloj alterado) | El servidor decide; licencia verificada con firma; pines; sin secretos |
| TB‑4 | Next.js → API | Suplantación interna | Red interna + autenticación de servicio; reenvío del contexto de usuario |
| TB‑5 | API → PostgreSQL | Fuga entre organizaciones, inyección | Rol sin `BYPASSRLS`, RLS forzado, SQL parametrizado |
| TB‑6 | API → proveedores externos | SSRF, filtración de claves, respuestas manipuladas | Lista blanca de destinos, TLS, secretos solo aquí, validación estricta de respuestas |
| TB‑7 | Operador → infraestructura | Abuso de privilegios | SSH con llaves/VPN, acceso *break‑glass* auditado |

## 2.5 Capas dentro de cada módulo del API (arquitectura hexagonal)

```text
 adaptadores de entrada          aplicación                     dominio                 adaptadores de salida
 (REST, jobs, webhooks)   ──►  casos de uso / comandos  ──►  entidades + reglas  ◄──  (jOOQ/JDBC, HTTP externo,
                                (transacción + auditoría)    (core:domain)             SMTP, almacenamiento)
```

- Un módulo **solo** accede a sus propias tablas; para datos de otros módulos usa su API pública o eventos.
- Toda mutación se ejecuta en un caso de uso que, **en la misma transacción**, aplica reglas, escribe el cambio, el registro de cambios para sync y el evento de auditoría.

## 2.6 Superficie de API (borrador)

Base: `/api/v1`. Contrato fuente de verdad: OpenAPI 3.1 (ver [doc 17](17-stack-recomendado.md)).

| Área | Endpoints principales |
|---|---|
| Autenticación | `POST /auth/login` · `POST /auth/mfa/verify` · `POST /auth/refresh` · `POST /auth/logout` · `GET/DELETE /auth/sessions` · `POST /auth/password/forgot` y `/reset` · `POST /auth/mfa/enroll` y `/confirm` |
| Tenencia | `/organizations` · `/companies` · `/branches` |
| Acceso | `/users` · `/roles` · `/role-assignments` |
| Catálogo | `/categories` · `/units` · `/suppliers` · `/raw-materials` · `/products` |
| Valores IPV | `/ipv-values` (+ historial) |
| Inventario | `/warehouses` · `/inventory/movements` · `/inventory/balances` · `/inventory/counts` |
| Costeo | `/cost-sheets` · `POST /cost-sheets/{id}/versions` (nuevo borrador desde una versión) · `PUT /cost-sheet-versions/{id}` (solo borrador) · `POST …/submit·return·validate·approve·activate·annul` · `GET …/evaluation` · `GET …/history` |
| Control IPV | `/ipv-controls` · `/ipv-controls/{id}/lines` · `POST …/close` |
| Tasas | `GET /rates/current` · `GET /rates/history` · `GET /rates/status` · `POST /rates/manual` (admin) · `GET /rate-snapshots/{id}` |
| Licencias | `POST /licenses/activate` · `POST /licenses/refresh` · `GET /licenses/me` · `GET/DELETE /licenses/devices` · admin: `/admin/licenses` + `…/renew·suspend·reinstate·revoke` · `POST /webhooks/keygen` |
| Comercial | `/price-catalog` · `/contracts` · `/payments` · `/receipts` |
| Auditoría | `GET /audit/events` · `GET /audit/verify` |
| Sync | `POST /sync/push` · `GET /sync/changes?cursor=` · `GET /sync/bootstrap` |
| Reportes | `GET /reports/{tipo}` · `POST /exports` |
| Notificaciones | `GET /notifications` · `POST /notifications/{id}/read` |
| Operación | `GET /health/live` · `GET /health/ready` |

**Convenciones transversales**

| Tema | Decisión 🧭 |
|---|---|
| Errores | `application/problem+json` (RFC 9457) con código de dominio estable |
| Idempotencia | Cabecera `Idempotency-Key` en `POST` no idempotentes; `mutation_id` en sync |
| Concurrencia | `ETag`/`If-Match` con la columna `version` |
| Paginación | Por cursor (`?cursor=&limit=`) |
| Dinero y tasas | **Cadenas decimales** en JSON (`"755.000000"`), nunca números de coma flotante |
| Fechas | ISO‑8601 en UTC; la *fecha de negocio* se calcula en la zona horaria de la empresa (por defecto `America/Havana`, configurable) |
| Identificadores | UUID v7 |
| Idioma | `Accept-Language`; mensajes de error por código + texto localizado |
