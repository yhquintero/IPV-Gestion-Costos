# 19. Roadmap por fases

> Entregable §39‑19 · [Índice](README.md) · Las 10 fases del prompt, **incrementales y verificables**. El esfuerzo se expresa en tamaño relativo (S, M, L, XL), **no en fechas**: estimar calendario sin conocer el equipo y las decisiones pendientes sería inventar.

## 19.1 Principios

1. **No avanzar de fase sin cumplir su *gate*** (criterios medibles).
2. **Las pruebas se escriben en cada fase**, no solo en la 9 (la fase 9 es endurecimiento y aceptación).
3. **Un corte vertical antes que muchas capas a medias**: el MVP recorre auth → catálogo → valores IPV → ficha (estados) → control IPV → auditoría → licencia.
4. Cada decisión pendiente tiene **fase límite** ([doc 16](16-decisiones-pendientes.md)); si no se resuelve, se avanza solo con lo que no depende de ella.
5. **Prioridad**: seguridad > trazabilidad > corrección de datos > mantenibilidad > offline > rendimiento > estética.

## 19.2 Visión general

| Fase | Objetivo | Entregable principal | Gate de salida | Esfuerzo |
|---|---|---|---|---|
| **1** Análisis | Validar el diseño | Este paquete + decisiones y *spikes* iniciados | Propietario valida; D‑01…D‑07 decididas o con plan; ADR‑0001 aceptado | M |
| **2** Modelo de datos | Esquema y dominio base | Migraciones, RLS, triggers, `core:domain` (dinero) | Invariantes I‑01…I‑20 con prueba verde; 0 fugas entre organizaciones | L |
| **3** Backend/API | Reglas de negocio autoritativas | API REST + OpenAPI, identidad, ficha, control, auditoría | Contrato y pruebas verdes; IDOR y auditoría verificadas | XL |
| **4** Web | Panel y sitio público | Next.js con BFF | E2E del flujo completo; accesibilidad sin críticos; CSP estricta | L |
| **5** Android | App modular en línea + lectura cacheada | App con Room+SQLCipher | Pruebas instrumentadas; lista MASVS; sin secretos en el APK | L |
| **6** Sync/offline | Edición offline segura | Protocolo, outbox, conflictos | **Gates G‑2 y G‑3** del ADR en verde | XL |
| **7** Keygen | Licencias y comercial | Proveedor, activación, archivos offline, renovación | Evaluador de estados 100 % probado; conciliación; spike S‑1 resuelto | L |
| **8** elTOQUE | Tasas reales | Proveedor, planificador, caché, etiquetas | Pruebas de contrato y fallos; lista de cumplimiento; D‑04 respondida | M |
| **9** Pruebas | Endurecimiento y aceptación | Regresión, seguridad, carga, caos, prueba de penetración | Criterios de aceptación (§36) cumplidos; sin hallazgos críticos/altos | L |
| **10** Despliegue | Puesta en producción controlada | Compose + Caddy, CI/CD, monitoreo, copias, *runbooks* | Simulacro de restauración OK; alertas probadas; **G‑4** | M |

## 19.3 Detalle

### Fase 1 · Análisis y validación (entregada)

- **Alcance**: este paquete; ADR‑0001; decisiones D‑xx; consultas externas (token y términos de elTOQUE, dictamen sobre Keygen y hosting).
- **Tras la validación del diseño**: *spikes* [S‑1 a S‑7](#spikes) (prototipos **descartables**, no producto).
- **Gate**: validación del propietario; decisiones prioritarias resueltas ([§16.1](16-decisiones-pendientes.md#161-las-decisiones-que-condicionan-todo-lo-demás-resolver-primero)).

### Fase 2 · Modelo de datos (en curso)

- **Alcance**: esqueleto Gradle (`core`, `server`); migraciones Flyway; RLS forzado; *triggers* de inmutabilidad; generador de semillas sintéticas; `core:domain` con `Money`/redondeo y vectores dorados; CI base; ADR‑0002…0007 según decisiones.
- **Criterios**: cada invariante I‑01…I‑20 con **prueba en Testcontainers**; pruebas de propiedades de dinero; migraciones reproducibles desde cero.
- **Código**: [docs/fases/02-modelo-de-datos.md](fases/02-modelo-de-datos.md) · `core/domain` · `server/db/migration` · `server/app` · `tools/seed`.

### Fase 3 · Backend/API (en curso)

- **Alcance**: identidad (Argon2id, MFA, sesiones), acceso (RBAC+ABAC), tenencia, catálogo, valores IPV, **ciclo de vida de la ficha** (7 estados), reglas v1, aprobaciones, Control IPV (`CONSISTENCIA` y `DERIVA_COSTOS`), auditoría con bloques firmados, **puerto de tasas + tasa manual**, notificaciones básicas, reportes básicos, OpenAPI, `Idempotency-Key`, `ETag`.
- **Criterios**: contrato verde; **los comportamientos cubiertos por las 123 pruebas de IPV reproducidos como casos de aceptación**; pruebas IDOR por endpoint; `GET /audit/verify` sin rupturas; ASVS L2 (parcial) documentado.
- **Código**: [docs/fases/03-backend-api.md](fases/03-backend-api.md) · `contracts/openapi/ipv-gc.yaml` · `server/app`.

### Fase 4 · Web (en curso)

- **Alcance**: BFF y sesión; panel por rol (catálogo, valores IPV, fichas con flujo, controles, tasas, usuarios, auditoría, estado de licencia); sitio público con precios en USD y **CUP derivado etiquetado** (con tasa manual/de prueba hasta la Fase 8).
- **Criterios**: Playwright recorre borrador→vigente→control; axe sin violaciones críticas; CSP estricta; ZAP *baseline* limpio.
- **Código**: [docs/fases/04-web.md](fases/04-web.md) · `web/`.

### Fase 5 · Android (en curso)

- **Alcance**: app multimódulo; login/MFA/biometría; catálogo, valores IPV, fichas y controles **en línea con caché de lectura**; Room + SQLCipher; evaluador de licencia con **archivos dorados** (sin proveedor aún).
- **Criterios**: pruebas instrumentadas; migraciones de Room probadas; lista MASVS L1; el APK **no contiene secretos**; *build* reproducible.
- **Código**: [docs/fases/05-android.md](fases/05-android.md) · `android/` · `core/domain/.../license`.

### Fase 6 · Sincronización y offline (ADR‑0001)

- **Alcance**: protocolo (bootstrap/push/pull/lápidas/`RESYNC_REQUIRED`), outbox, centro de conflictos, WorkManager, ahorro de datos. Etapas **6a** (conteos), **6b** (controles y movimientos), **6c** (borradores).
- **Criterios**: **E‑1…E‑5 en verde en CI**; revisión de G‑2 (aislamiento, amenazas, conflictos, recuperación); 2 semanas de uso interno en 6a **sin pérdida de datos**.

### Fase 7 · Keygen, licencias y comercial

- **Alcance**: adaptadores `LicenseProvider`; aprovisionamiento idempotente de políticas y derechos; activación y archivos de máquina; asiento web; *webhooks*; aplicación de derechos; renovación; catálogo de precios, contratos, pagos, recibos; pantallas de plataforma.
- **Criterios**: evaluador de estados **dirigido por tabla** (los 7 estados + orden de evaluación); conciliación de renovaciones; pruebas con proveedor simulado **y** con Keygen real/CE (según D‑05).

### Fase 8 · elTOQUE

- **Alcance**: `ElToqueApiProvider`, `CachedProvider`, planificador con líder, validación, estados, etiquetas, alertas, instantáneas, servidor simulado.
- **Criterios**: pruebas de contrato con *fixtures* reales saneados; inyección de fallos; lista de cumplimiento de términos; D‑04 respondida.

### Fase 9 · Pruebas y endurecimiento

- **Alcance**: regresión completa, seguridad (ZAP, ASVS L2, MASVS), carga (k6), caos de sync, accesibilidad, **prueba de penetración externa**, simulacro de restauración.
- **Criterios**: los 30 criterios de aceptación del prompt (§36) verificados uno a uno (matriz de trazabilidad por construir al validar el diseño); sin hallazgos críticos/altos abiertos.

### Fase 10 · Despliegue

- **Alcance**: Compose + Caddy, secretos aprovisionados, CI/CD con firma, monitoreo y alertas, copias externas, *runbooks*, lista de salida a producción, **piloto controlado**.
- **Criterios**: simulacro de restauración cumple RPO/RTO acordados; alertas probadas; criterios de salida del piloto cumplidos (**G‑4**).

## 19.4 Spikes

<a id="spikes"></a>

Prototipos **descartables** que reducen riesgo antes de comprometer diseño. Requieren el visto bueno del propietario; no forman parte del producto. Tamaño sugerido entre paréntesis.

| Id | Objetivo | Criterio de éxito | Precondición |
|---|---|---|---|
| **S‑1** (M) | **Keygen**: políticas de [doc 7](07-flujo-licencias-keygen.md); archivo de máquina con `ECDSA_P256_SIGN`; verificación en Android (DER vs P1363); ¿incluye la clave?; `FROM_FIRST_ACTIVATION` con asiento web; `RESET_EXPIRY`; renovar sin activar; *webhooks*; idempotencia; huella de Keygen CE | Informe + **archivos de licencia dorados** válidos e inválidos; decisión sobre `scheme` | Usar **CE local** salvo dictamen que permita Cloud ([D‑05](16-decisiones-pendientes.md#d-05)) |
| **S‑2** (S) | **elTOQUE**: pocas llamadas reales para fijar el contrato (campos, zona horaria, instrumentos, cabeceras) | *Fixture* saneado + mapeo de códigos; lista de instrumentos disponibles | Token concedido ([D‑04](16-decisiones-pendientes.md#d-04)); **la clave no entra en el repositorio** |
| **S‑3** (M) | **Sync**: *outbox* + push/pull idempotentes entre dos dispositivos simulados y PostgreSQL | Esqueleto de E‑1…E‑5 en verde | — |
| **S‑4** (S) | **Room + SQLCipher** en gama baja: tamaño, apertura, *bootstrap* de decenas de miles de filas | Medidas dentro de umbrales acordados | Dispositivo(s) de prueba |
| **S‑5** (M, ≤ 1 semana) | **Backend**: corte fino (login + enviar versión de ficha + evento de auditoría) en Spring Boot frente a Ktor | Memoria, arranque, esfuerzo; confirma [D‑31](16-decisiones-pendientes.md#d-31) | — |
| **S‑6** (S) | **RLS + Flyway + jOOQ**: aislamiento, `SET LOCAL`, exclusión de vigencias, *triggers* de inmutabilidad | Suite de fuga en verde; coste de rendimiento aceptable | — |
| **S‑7** (S) | **Auditoría**: Merkle + firma (Ed25519 vs ECDSA) + verificador sin conexión + detección de manipulación | Verificación correcta y ruptura detectada | — |

## 19.5 Corte vertical del MVP

Registrar organización/empresa/usuarios → catálogo → **Valores IPV** → **ficha**: borrador → revisión → validada → aprobada → vigente (con instantánea de tasa manual) → **Control IPV** → auditoría verificable → licencia (aplicada en servidor) → Android en línea con caché. Offline editable, elTOQUE real, comercial completo y Cuadre llegan después, en el orden de las fases.

## 19.6 Definición de terminado (general)

Código revisado · pruebas automáticas (unidad + integración + contrato donde aplique) en verde · sin secretos ni datos reales · migraciones probadas · auditoría de las operaciones nuevas · documentación y ADR actualizados · cabeceras/etiquetas de seguridad y de tasa verificadas · accesibilidad revisada en pantallas nuevas · sin hallazgos críticos de las herramientas de seguridad.

## 19.7 Trazabilidad con los criterios de aceptación (§36)

Cada fase define **criterios propios verificables** (arriba). La matriz que cruza los 30 criterios del §36 con fases y pruebas se construye al validar el diseño, con el texto íntegro de esos criterios en la mano.
