# 11. Estrategia de seguridad

> Entregable §39‑11 · [Índice](README.md) · Prioridad #1 del prompt. Complementos: [auditoría](12-estrategia-auditoria.md) · [secretos](20-secretos-y-configuracion.md) · [licencias](07-flujo-licencias-keygen.md). No es un dictamen legal ni una certificación.

## 11.1 Principios

1. **Denegar por defecto**; mínimo privilegio en usuarios, servicios y base de datos.
2. **El servidor decide**: el cliente (web o Android) es un entorno no confiable.
3. **Defensa en profundidad**: cada invariante crítica se aplica en la BD **y** en el servicio.
4. **Sin secretos en clientes ni en el repositorio.**
5. **Fallar cerrado**: ante duda sobre licencia, permisos o integridad, se rechaza y se audita.
6. **Todo lo sensible deja rastro** (auditoría encadenada y firmada).

## 11.2 Activos y clasificación

| Activo | Clasificación | Protección principal |
|---|---|---|
| Costos, fichas, valores IPV, controles | **Confidencial de negocio** | RLS por organización, `costs:view`, cifrado en tránsito y en reposo |
| Credenciales, semillas TOTP, *refresh tokens* | **Secreto** | Argon2id, cifrado por campo, *hash* de tokens, rotación |
| Claves (JWT, auditoría, cifrado de campo, copias) | **Secreto crítico** | Fuera del repositorio, separadas por propósito, rotación y custodia |
| Token de elTOQUE y de Keygen | **Secreto** | Solo en el servidor |
| Datos personales (nombre, correo, IP, dispositivo) | **Personal** | Minimización, retención, seudonimización en auditoría ([D‑17](16-decisiones-pendientes.md#d-17)) |
| Auditoría | **Integridad crítica** | Solo anexado, firmada, anclada fuera |
| Archivo de licencia en dispositivo | **Verificable, no secreto** | Firma asimétrica; guardado con Keystore |

## 11.3 Modelo de amenazas

| Id | Amenaza (STRIDE) | Controles | Verificación |
|---|---|---|---|
| A‑01 | **S** Relleno de credenciales / *phishing* | Argon2id, MFA (obligatorio a roles privilegiados), límite de tasa y bloqueo progresivo, comprobación de contraseñas filtradas, respuesta uniforme | Pruebas de fuerza bruta y enumeración |
| A‑02 | **S/I** Robo de sesión (XSS) | BFF con cookie `HttpOnly; Secure; SameSite`, CSP estricta con *nonce*, tokens cortos, *refresh* rotatorio con **detección de reutilización** | ZAP, pruebas de CSP, prueba de reutilización |
| A‑03 | **T** Alterar una ficha aprobada | Inmutabilidad por *triggers* + `content_hash` + auditoría firmada | Pruebas de BD y verificación de bloques |
| A‑04 | **T** Manipular BD/outbox en el dispositivo | SQLCipher; el servidor **recalcula y revalida todo**; nunca confía en totales del cliente | Pruebas de payload malicioso |
| A‑05 | **T/E** Falsificar licencia o retroceder el reloj | Firma asimétrica verificada, comprobación de `alg`, reloj fiable, TTL, aplicación en servidor ([doc 7](07-flujo-licencias-keygen.md)) | Archivos dorados válidos/inválidos; pruebas de reloj |
| A‑06 | **R** Negar una acción | Auditoría con actor, dispositivo, IP (si es lícito), motivo, antes/después; bloques firmados y anclados | Verificación periódica |
| A‑07 | **I** Fuga entre organizaciones | **RLS forzado**, FK compuestas, autorización a nivel de objeto, errores sin filtrar datos | Suite de fuga entre organizaciones (G‑2) |
| A‑08 | **I** Costos visibles a quien no debe | `costs:view` aplicado en consultas y serialización; alcance de sync | Pruebas por rol |
| A‑09 | **I** Filtración de secretos (repo, logs, clientes) | Escáner de secretos, variables de entorno/secretos de contenedor, redacción en logs, nada en Android/JS | `gitleaks` en CI; análisis del APK |
| A‑10 | **I** Datos personales hacia terceros | Keygen solo con identificadores opacos | Revisión de payloads |
| A‑11 | **D** Inundación de la API o abuso de sync | Límites por IP/usuario/dispositivo, tamaños máximos, paginación acotada, *backpressure* | Pruebas de carga |
| A‑12 | **D** Caída de elTOQUE o Keygen | Caché, cortacircuitos, archivos offline, proveedor intercambiable | Inyección de fallos |
| A‑13 | **E** IDOR/BOLA (acceder a objetos ajenos) | Autorización por objeto en **cada** endpoint; consultas siempre acotadas por alcance | Pruebas automáticas por endpoint |
| A‑14 | **E** Inyección SQL | SQL parametrizado/jOOQ; rol de BD sin DDL ni `BYPASSRLS` | SAST + pruebas |
| A‑15 | **E** *Payloads* de sync maliciosos | Validación de esquema, límites, recalculo en servidor | Fuzzing de `/sync/push` |
| A‑16 | **T** Dependencia comprometida | Versiones fijadas, SBOM, escaneo, *builds* firmados | CI |
| A‑17 | **E** SSRF por URL de proveedor/*webhook* | **Lista blanca** de destinos; ninguna URL aceptada del usuario | Pruebas |
| A‑18 | **R/E** Abuso de operador | Acceso *break‑glass* auditado, mínimo privilegio, doble control para restaurar | Revisión de accesos |
| A‑19 | **I** Robo de copias de seguridad | Copias cifradas; claves separadas de los datos | Simulacro de restauración |
| A‑20 | **T** Repetición de *webhooks* / mutaciones | `id` de evento y `mutation_id` idempotentes; firma verificada | Pruebas de reintento |

## 11.4 Identidad y autenticación

| Tema | Decisión 🧭 |
|---|---|
| Contraseñas | **Argon2id** con parámetros según la hoja vigente de OWASP (verificar en Fase 3; mínimo inicial sugerido: m = 19 MiB, t = 2, p = 1). Longitud mínima 12, sin reglas de composición arbitrarias, comprobación contra lista de contraseñas filtradas (sin enviar la contraseña a terceros). Caducidad periódica forzada: **no** por defecto (NIST SP 800‑63B) — configurable ([D‑18](16-decisiones-pendientes.md#d-18)). |
| MFA | **TOTP** (RFC 6238) obligatorio a `PLATFORM_ADMIN`, `ORG_ADMIN` y `APROBADOR`; códigos de recuperación de un solo uso (*hash*); WebAuthn/passkeys en fase posterior. Semillas TOTP **cifradas** en reposo. |
| Recuperación | Enlace por correo de un solo uso y corta vida + MFA; **sin "preguntas de seguridad"** (las usa `inventario`). |
| Tokens (Android/API) | *Access token* JWT de **10‑15 min** firmado con clave **asimétrica** (EdDSA/ES256) con `kid` y rotación; *refresh token* **opaco**, rotatorio, guardado como *hash*, ligado a dispositivo y con **detección de reutilización** (familia revocada). |
| Web | **BFF**: el navegador solo tiene una cookie de sesión `HttpOnly; Secure; SameSite`; protección CSRF; **ningún token en JavaScript**. |
| Bloqueo | Progresivo por cuenta y por IP; alertas por *password spraying* (patrón ya presente en IPV). |
| Sesiones | Lista y revocación por el usuario y el administrador; tope de sesiones simultáneas ([D‑10](16-decisiones-pendientes.md#d-10)). |
| Cuentas de servicio / `API_ACCESS` | Tokens personales o de servicio **propios** (no de Keygen), con alcance mínimo, caducidad y auditoría. |

## 11.5 Autorización (RBAC + ABAC)

- **RBAC con alcance**: `role_assignments(user, role, scope_type, scope_id)`; los permisos son `módulo:acción` (+ `costs:view`).
- **ABAC** para reglas contextuales: autor ≠ validador ≠ aprobador (configurable); solo el autor edita su borrador; ventanas de vigencia de asignaciones; estado de licencia y derechos.
- **Punto único de decisión** en el módulo `access`; toda consulta se acota por alcance (organización/empresa/sucursal) **antes** de ejecutarse.
- El cliente recibe un **conjunto de capacidades** solo como ayuda de interfaz.

Matriz de partida (⛔ [D‑18](16-decisiones-pendientes.md#d-18); ✔ permitido · ◐ condicionado · — no):

| Capacidad | ORG_ADMIN | COMPANY_ADMIN | COSTEADOR | REVISOR | APROBADOR | CONTROLADOR_IPV | ALMACENERO | ECONOMICO | AUDITOR | LECTOR |
|---|---|---|---|---|---|---|---|---|---|---|
| Gestionar usuarios y roles | ✔ | ◐ su empresa | — | — | — | — | — | — | — | — |
| Catálogo y valores IPV (editar) | ✔ | ✔ | ✔ | — | — | — | — | — | — | — |
| Ver costos (`costs:view`) | ✔ | ✔ | ✔ | ✔ | ✔ | ◐ | — | ✔ | ✔ | — |
| Crear/editar borrador y enviar | ✔ | ✔ | ✔ | — | — | — | — | — | — | — |
| Validar o devolver | — | — | — | ✔ | ◐ | — | — | — | — | — |
| Aprobar, activar y anular | — | — | — | — | ✔ | — | — | — | — | — |
| Crear/capturar Control IPV | ✔ | ✔ | — | — | — | ✔ | — | — | — | — |
| Validar/cerrar Control IPV | ✔ | ✔ | — | — | ✔ | ✔ | — | ◐ | — | — |
| Inventario (mover/contar) | ✔ | ✔ | — | — | — | — | ✔ | — | — | — |
| Tasa manual | ✔ | ✔ | — | — | — | — | — | ✔ | — | — |
| Editar reglas | — | ✔ | — | — | — | — | — | — | — | — |
| Reportes | ✔ | ✔ | ✔ | ✔ | ✔ | ◐ | — | ✔ | ✔ | ◐ |
| Exportar datos (`DATA_EXPORT`) | ✔ | ◐ | — | — | — | — | — | ✔ | ◐ | — |
| Consultar auditoría (`ADVANCED_AUDIT`) | ◐ | ◐ | — | — | — | — | — | — | ✔ | — |

## 11.6 Transporte y perímetro

- **TLS 1.2+ (1.3 preferido)** con certificados públicos (Let's Encrypt vía Caddy); **HSTS**; redirección HTTP→HTTPS.
- Cabeceras web: **CSP estricta con *nonce*** (sin `unsafe-inline`), `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, `frame-ancestors 'none'`, `Cross-Origin-Opener-Policy`.
- **CORS**: ninguno para el panel (mismo origen vía BFF); lista cerrada para clientes de `API_ACCESS`.
- **Límites de tasa** en el borde y en la aplicación; tamaño máximo de cuerpo; tiempos de espera.
- Errores en formato `problem+json` **sin trazas** ni detalles internos.

## 11.7 Protección de datos

| Tema | Decisión 🧭 |
|---|---|
| En reposo | Disco cifrado del servidor + **cifrado por campo** para secretos (semillas TOTP, material sensible) con claves de datos envueltas por una clave maestra. |
| Copias | Cifradas **antes** de salir del servidor; clave separada de los datos; **simulacros de restauración** periódicos ([doc 17](17-stack-recomendado.md)). |
| Claves | Una por propósito (JWT, firma de auditoría, cifrado de campo, copias); versionadas con `kid`/`key_version`; procedimiento de rotación y custodia ([doc 20](20-secretos-y-configuracion.md)). **Perder la clave de copias o de campo = pérdida irrecuperable**. |
| Base de datos | Roles separados: `app_rw` (sin DDL ni `BYPASSRLS`), `migrator` (solo migraciones), `audit_writer` (solo `INSERT`), `reporting_ro`; `SET LOCAL app.organization_id` por transacción; tiempos límite de sentencia. |
| Privacidad | Minimización; IP registrada "donde sea lícito": configurable (completa / truncada / con *hash*) — ⛔ [D‑17](16-decisiones-pendientes.md#d-17); derecho de supresión resuelto por **seudonimización** (la auditoría conserva el `user_id`, los datos personales viven en `users` y pueden anonimizarse). Marco legal de protección de datos: ⛔ revisión legal. |

## 11.8 Android (alineado con OWASP MASVS)

| Área | Controles |
|---|---|
| Almacenamiento | SQLCipher + clave envuelta por Keystore; `allowBackup=false`; sin datos sensibles en logs/portapapeles; `FLAG_SECURE` en pantallas de costos. |
| Criptografía | AES‑GCM y firmas estándar; **sin criptografía propia ni claves incrustadas**; Keystore con clave no exportable; `setUserAuthenticationRequired` + `BIOMETRIC_STRONG`/`CryptoObject` para el bloqueo. |
| Red | HTTPS obligatorio (`cleartextTrafficPermitted=false`); en *release* **solo CA del sistema**; **pines SPKI con respaldo y caducidad** en `network_security_config` (si la caducidad pasa, los pines se ignoran en lugar de bloquear a todos); plan de rotación de pines. |
| Plataforma | Componentes no exportados salvo lanzador; enlaces profundos verificados; sin `WebView` con puentes JS. |
| Calidad de código | R8, sin logs de depuración en *release*, análisis de dependencias, Android Lint y `detekt`. |
| Resiliencia | Detección **blanda** de *root*/emulador/APK alterado (aviso y telemetría), sin depender de ella; Play Integrity solo si aplica ([D‑19](16-decisiones-pendientes.md#d-19)). |
| Privacidad | Sin analítica por defecto; sin identificadores de hardware; permisos mínimos. |
| Distribución | Firma de la app protegida; si es APK directo, **manifiesto de actualización firmado** ([D‑19](16-decisiones-pendientes.md#d-19)). |

**Fijación de certificados**: IPV usa TOFU (fija la primera CA que ve, [H‑07](01-analisis-arquitectonico.md#12-hallazgos-que-más-condicionan-el-diseño)), lo que no protege el primer uso. El nuevo diseño fija **claves SPKI conocidas de antemano** (la clave propia del servidor, estable entre renovaciones, y al menos una de respaldo).

## 11.9 Integraciones externas

- **Lista blanca** de destinos salientes (`tasas.eltoque.com`; Keygen Cloud o la URL de Keygen CE); ninguna URL proviene del usuario.
- Secretos solo en el servidor; **tiempos de espera**, reintentos acotados y cortacircuitos.
- **Webhooks de Keygen**: verificación de firma, idempotencia por `id`, tolerancia de tiempo.
- Respuestas externas **se validan** como entrada no confiable (esquema, rangos).
- Ninguna llamada de clientes a proveedores.

## 11.10 Licencias

Resumen: firma asimétrica verificada en el dispositivo, `alg` esperado, reloj fiable, TTL como gracia, aplicación autoritativa en el servidor, sin bandera booleana ([doc 7](07-flujo-licencias-keygen.md#78-lo-que-no-puede-impedirse-realismo)).

## 11.11 Cadena de suministro y ciclo de desarrollo seguro

| Control | Detalle |
|---|---|
| Repositorio | Rama protegida, revisión obligatoria, historial lineal, **escaneo de secretos** (gitleaks) también como *pre‑commit*. |
| Dependencias | Versiones fijadas con *lockfiles*, Renovate/Dependabot, `OWASP dependency-check`/Trivy, `npm audit`, **SBOM CycloneDX** en cada *build*. |
| Análisis estático | `detekt`, `ktlint`, Android Lint, ESLint + `tsc` estricto, CodeQL o Semgrep. |
| Contenedores | Imágenes mínimas, no‑root, sistema de archivos de solo lectura, `cap_drop ALL`, escaneo con Trivy. |
| Dinámico | ZAP *baseline* y *API scan* contra *staging* (práctica heredada de IPV), pruebas E2E con Playwright. |
| Artefactos | Firma del APK/AAB y de imágenes; claves de firma fuera de la CI cuando sea posible; secretos de CI de alcance mínimo. |
| Contrato | OpenAPI como fuente de verdad; pruebas de contrato y detección de cambios incompatibles. |

## 11.12 Operación segura y respuesta a incidentes

- Registro **JSON** sin secretos ni PII; identificador de petición; métricas y alertas ([doc 17](17-stack-recomendado.md)).
- **Guías de respuesta** (⛔ responsable y contacto: [D‑22](16-decisiones-pendientes.md#d-22)): secreto filtrado (rotar y revocar), cuenta comprometida (revocar sesiones, forzar MFA), sospecha de manipulación de auditoría (verificar bloques y congelar), fuga de datos (notificación según marco legal ⛔), revocación de la clave de elTOQUE o caída de Keygen (modo caché/manual y archivos offline).
- Copias probadas con **restauración real** de forma periódica.
- Sincronización de reloj (NTP/chrony) en servidores.

## 11.13 Verificación

| Objetivo | Cómo |
|---|---|
| OWASP **ASVS** nivel 2 (versión vigente) para API y web | Lista de comprobación por fase; evidencias en el repositorio |
| OWASP **MASVS** L1 + controles L2 seleccionados | Lista de comprobación Android; revisión del APK |
| Pruebas de seguridad automáticas | IDOR por endpoint, fuga RLS, reutilización de *refresh token*, CSRF/CSP, fuzzing de sync, archivos de licencia inválidos |
| Revisión externa | **Prueba de penetración** antes de salir a producción (⛔ presupuesto) |
| Modelo de amenazas | Revisión al cierre de cada fase |

## 11.14 Riesgos residuales

Dispositivos con *root* usados estrictamente offline durante la gracia; administrador legítimo malicioso; ingeniería social; dependencia legal/contractual de proveedores externos ([R‑01](14-riesgos.md#r-01), [R‑02](14-riesgos.md#r-02)); pérdida de claves sin custodia.
