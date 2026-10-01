# 1. Análisis arquitectónico

> Entregable §39‑1 · Fase 1 (análisis) · Fecha: 2026‑10‑01 · [Índice](README.md)

Este documento resume **qué existe**, **qué se contradice**, **qué se reutiliza** y **qué principios gobiernan** el diseño del producto unificado. El detalle fila a fila está en el [anexo A (análisis de brechas)](anexos/A-analisis-de-brechas.md) y la trazabilidad de fuentes externas en el [anexo B](anexos/B-fuentes-y-verificaciones.md).

**Leyenda de confianza** (se usa en todo el paquete):

| Marca | Significado |
|---|---|
| ✅ | Verificado en fuente oficial leída el 2026‑10‑01 (URL en el anexo B). |
| 🔎 | Observado directamente en el código o la documentación de los repos origen (medido o leído). |
| 🧭 | Propuesta de diseño / decisión provisional. No es un hecho. |
| ⛔ | **PENDIENTE DE DEFINICIÓN**: depende de una decisión, norma o dato que no se ha confirmado. No se inventa. |

---

## 1.1 Alcance y método

**Examinado**

- `yhquintero/IPV_Fichas-Costos` @ `15faee5` (2026‑10‑01) y `yhquintero/inventario` @ `c42fc6f` (2026‑09‑30): README, documentación, esquemas SQL, rutas, Android (Gradle, manifiestos, entidades, sincronización, licencias), CI y despliegue.
- Documentación oficial de elTOQUE (API, `swagger.json`, términos) y de Keygen (políticas, licencias, máquinas, criptografía offline, términos, ediciones autoalojadas).
- Pruebas existentes, ejecutadas aquí: **IPV → 123 pruebas OK (3 omitidas)** con `python3 -m unittest discover -s tests`; **inventario → 14 pruebas JS de almacenes OK** con `node tools/test_almacenes.mjs`. 🔎

**No examinado / no hecho (y por qué)**

- No se compiló ni ejecutó ninguna app Android (no hay SDK en el entorno); las pruebas unitarias Android no se corrieron.
- No se llamó a la API de elTOQUE (requiere token por aplicación que aún no existe) ni a Keygen (no hay cuenta ni instancia).
- No se importó ningún dato real ni credencial de los repos origen (ver C‑11).
- No se escribió código de producto: el prompt maestro exige analizar y validar el diseño antes.

---

## 1.2 Hallazgos que más condicionan el diseño

| # | Hallazgo | Evidencia | Consecuencia |
|---|---|---|---|
| H‑01 | Los dos repos **no se pueden fusionar por copia**. IPV usa SQLite, IDs enteros y no tiene organización/sucursal; `inventario` guarda **todo el estado en una sola fila JSON** (`app_state`) y sincroniza reemplazando/subiendo ese estado completo con un número de versión (409 si otro guardó antes). | 🔎 `server.py` ~l.161‑250; `cuadre_server.py` l.371‑381, `state_put` | Rediseño de datos y API: esquema relacional PostgreSQL, UUID, multi‑tenant. Se reutiliza **conocimiento de dominio y pruebas como especificación**, no el código. |
| H‑02 | Dos Android muy distintos: IPV es un **prototipo** (1 archivo `MainActivity.kt` de 1 926 líneas, sin dependencias externas, solo Keystore/pinning/biometría). `inventario` ya usa **Compose + Hilt + Room + WorkManager** (5 626 líneas Kotlin). | 🔎 `wc -l`, `build.gradle.kts` de ambos | Base de arquitectura: la de `inventario` (modernizada) + la **seguridad** de IPV. |
| H‑03 | **Integridad monetaria**: `inventario` guarda dinero como `Double` en Room (`precioVentaUsd`, `importeUsd`, …). IPV usa `Decimal` como TEXT con `ROUND_HALF_UP`. | 🔎 `Entities.kt`; `server.py` | `NUMERIC`/`BigDecimal` en todo el sistema, política de redondeo explícita ([D‑25](16-decisiones-pendientes.md#d-25)) y pruebas de propiedades. Prohibido `Double` para dinero. |
| H‑04 | **Ficha de Costo hoy** = solo Borrador→Aprobada, editable únicamente en Borrador, **papelera con borrado físico**, líneas con `material_id ... ON DELETE SET NULL` (se pierde la referencia), sin instantánea de tasa de cambio. | 🔎 `server.py`; PR #4 de IPV | Versiones inmutables, soft delete, instantáneas de costo y de tasa, 7 estados ([doc 6](06-flujo-ficha-ipv.md)). |
| H‑05 | **Auditoría**: IPV tiene cadena HMAC (buena idea) pero **sin entidad, valor anterior/nuevo ni motivo**; `inventario` registra `(ts, user, action, details, ip)`. | 🔎 `audit.py`; `cuadre_server.py` l.381 | Esquema de eventos completo + bloques firmados ([doc 12](12-estrategia-auditoria.md)). |
| H‑06 | **Licencias**: hay **dos esquemas caseros** y **ninguno es Keygen**. IPV firma con ECDSA P‑256 (códigos `IPV1.…`). `inventario` usa **HMAC‑SHA256 simétrico** con el secreto *en el propio servidor* (`LICENSE_SECRET`, con respaldo a `BACKUP_KEY`) y crea una prueba automática; la app Android cachea `valid=true` en preferencias. | 🔎 `licencia.py`; `cuadre_server.py` l.94‑330; `LicenseManager.kt` | Con HMAC simétrico quien aloja el servidor **puede falsificar licencias**; el booleano cacheado es el anti‑patrón que el prompt prohíbe. Se sustituye por firmas asimétricas verificables con clave pública ([doc 7](07-flujo-licencias-keygen.md)). |
| H‑07 | **Seguridad móvil heterogénea**: ambas apps confían en **CA instaladas por el usuario** en *release* (para el HTTPS local con mkcert); el pinning de IPV es TOFU (fija la primera CA que ve); `inventario` depende de `security-crypto 1.1.0‑alpha06`, **biblioteca obsoleta** ✅. | 🔎 `network_security_config.xml`; `Security.kt`; ✅ anexo B | En producción: CA pública + pines con respaldo y caducidad; la CA de usuario solo en variante *debug/enterprise*; Keystore directo en lugar de `EncryptedSharedPreferences` ([doc 11](11-estrategia-seguridad.md)). |
| H‑08 | El documento `decision-sin-multisucursal-offline.md` (2026‑09‑29) **pospone** offline editable y multi‑sucursal; el prompt maestro los **exige**. | 🔎 doc de IPV; prompt | [ADR‑0001](adr/0001-reabrir-offline-y-multisucursal.md): se reabre, con sus condiciones convertidas en *gates* verificables. |
| H‑09 | **"IPV" se usa con dos sentidos en el código**: *Valores del IPV* (= tabla `materials`: precio, vigencia, stock; **precede** a la Ficha) y *Controles de IPV* (verificación periódica; **sigue** a la Ficha aprobada). | 🔎 `server.py` l.178‑196; `web/index.html` l.38‑40; `web/app.js` l.23,45 | Posible reconciliación del "orden" (hipótesis). No se codifica una dirección rígida: [D‑01](16-decisiones-pendientes.md#d-01). |
| H‑10 | **Línea base de calidad** existente y reutilizable como práctica: Ruff, Bandit, pip‑audit, ZAP (baseline + API), Playwright, Docker, build Android en CI. | 🔎 `.github/workflows/ci-cd.yml` | Se conserva la *disciplina* (SAST, DAST, E2E, auditoría de dependencias) en la nueva pila. |
| H‑11 | **Tres fuentes de precios** que no coinciden (ver C‑03/C‑04). | 🔎 `docs/precios-y-licencias.md`; prompt | Catálogo de precios editable por administrador; decisión pendiente [D‑08](16-decisiones-pendientes.md#d-08). |
| H‑12 | **elTOQUE y Keygen traen restricciones contractuales y de disponibilidad** no previstas en el prompt (una clave por aplicación; no revender/sublicenciar datos; "no modifiques los datos"; esquema de respuesta no documentado; Cuba bajo embargo integral de EE. UU.). | ✅ anexo B | Capa de proveedores intercambiables, revisión legal temprana y *spikes* antes de comprometer ([doc 10](10-flujo-eltoque-cache.md), [doc 7](07-flujo-licencias-keygen.md)). |

---

## 1.3 Estado de los repositorios origen (hechos medibles)

| Dimensión | `IPV_Fichas-Costos` | `inventario` (Cuadre Pinar) |
|---|---|---|
| Propósito | Productos/insumos ("Valores del IPV"), Fichas de Costo, Controles de IPV, inventario de insumos | Inventario de tienda, cuadre diario de caja, venta rápida (POS), almacenes, finanzas |
| Servidor | Python stdlib `http.server` (`server.py` 1 858 l.) | Python stdlib + `cryptography` (`cuadre_server.py` 1 287 l.) |
| Datos | SQLite (+SQLCipher opcional), IDs enteros, dinero `Decimal` TEXT | SQLite; **estado único JSON** (`app_state`); en Room: IDs enteros y `Double` |
| Autenticación | JWT HS256 propio, PBKDF2 310 000 it., TOTP + códigos de recuperación, bloqueo por intentos | Sesiones por cookie, PBKDF2 210 000 it., 2FA TOTP, recuperación por "pregunta de seguridad" |
| Autorización | Módulos × {ver, editar, costos}; roles admin/editor/viewer; costos ocultos en servidor | Roles ADMINISTRADOR/JEFE/ECONOMICO/ALMACENERO; matriz por sección |
| Auditoría | Cadena HMAC (sin entidad/antes/después/motivo) | Tabla simple |
| Web | JS vanilla SPA (`app.js` 2 068 l., `enterprise.js` 830, CSS 2 309), PWA (`sw.js`) | JS vanilla SPA (`app.js` 2 345 l.), PWA |
| Android | Un solo `MainActivity.kt` (1 926 l.), Keystore AES‑GCM, pinning TOFU, biometría, **online‑only** | Compose + Hilt + Room + WorkManager, `SyncManager`, exportación CSV/PDF |
| Licencia | ECDSA P‑256 propio (planes 1S/1M/3M/6M/1A/2A) | HMAC simétrico propio (`CP‑…`), prueba automática |
| HTTPS | CA local (`iniciar-https.ps1`) | Caddy + Let's Encrypt (`deploy/`) |
| CI/Pruebas | 123 pruebas OK; Ruff, Bandit, ZAP, Playwright, Docker, Android | 14 pruebas JS OK; prueba unitaria Android (`StockCalculatorTest`, no ejecutada) |
| Licencia del código | "Todos los derechos reservados" | "Todos los derechos reservados" |

---

## 1.4 Conflictos entre fuentes y propuesta

| ID | Conflicto | Propuesta (🧭) | Estado |
|---|---|---|---|
| <a id="c-01"></a>C‑01 | **Orden IPV ↔ Ficha**: el usuario describe el IPV como documento previo a la Ficha; el prompt pide el flujo *Ficha→IPV*; el código hace *Valores IPV → Ficha → Control IPV*. | Modelar dos objetos distintos (`ipv_values` y `ipv_controls`), enlazados por versión de ficha, sin forzar el orden: lo regula una política configurable. | ⛔ [D‑01](16-decisiones-pendientes.md#d-01) |
| <a id="c-02"></a>C‑02 | Aplazar offline/multi‑sucursal (2026‑09‑29) vs requerirlos. | [ADR‑0001](adr/0001-reabrir-offline-y-multisucursal.md) con *gates*. | Propuesto |
| <a id="c-03"></a>C‑03 | **Precios**: doc de IPV = por dispositivo (Web‑servidor 6/18/48/90/160/280 USD; Android 3/8/21/39/70/120 USD; plan semanal); prompt = paquete único 25/75/195/360/600/1 020 USD con trial 7 d. | Catálogo de precios en BD, editable sin código; ambos modelos son representables. | ⛔ [D‑08](16-decisiones-pendientes.md#d-08) |
| <a id="c-04"></a>C‑04 | **Renovación**: el doc de IPV dice que los días restantes *no se suman*; el prompt exige renovar *desde la expiración* (acumula). | Seguir el prompt (`renewalBasis=FROM_EXPIRY`) pero **advertir el caso de renovación tardía** ([doc 8](08-flujo-renovacion.md)). | ⛔ [D‑12](16-decisiones-pendientes.md#d-12) |
| <a id="c-05"></a>C‑05 | "Keygen" = SaaS keygen.sh (prompt) vs generador propio (IPV: ECDSA) vs HMAC (inventario). Disponibilidad/legalidad de Keygen Cloud para Cuba sin verificar. | Puerto `LicenseProvider` con Keygen (Cloud o CE) y alternativa propia con el mismo formato de archivo; revisión legal antes de contratar. | ⛔ [D‑05](16-decisiones-pendientes.md#d-05) |
| <a id="c-06"></a>C‑06 | Dos apps Android (`cu.ipvcostos.app`, `com.cuadrepinar.inventario`). | Una sola app con `applicationId` nuevo; sin migración de instalaciones (no hay datos que conservar en esta fase). | ⛔ [D‑23](16-decisiones-pendientes.md#d-23) |
| <a id="c-07"></a>C‑07 | Dos backends y dos modelos de autenticación (JWT vs cookie; PBKDF2 distintos). | Un único módulo de identidad (Argon2id, TOTP, tokens cortos). | 🧭 |
| <a id="c-08"></a>C‑08 | Dinero: `Decimal` TEXT vs `Double`. | `NUMERIC(19,4)` / `BigDecimal`; nunca `Double`. | 🧭 |
| <a id="c-09"></a>C‑09 | Estados de ficha: 2 (Borrador/Aprobada) vs 7 (prompt). | 7 estados con máquina de estados en servidor. | 🧭 |
| <a id="c-10"></a>C‑10 | Cliente HTTPS: confía en CA de usuario (mkcert) vs producción pública. | Variantes de compilación: *release* solo CA del sistema + pines; *debug/enterprise* admite CA propia. | 🧭 |
| <a id="c-11"></a>C‑11 | `inventario` publica credenciales demo en el README y las muestra en la pantalla de acceso web; incluye dos `.xlsx` que probablemente contienen datos reales (no verificado). | **No importar** credenciales ni datos; semilla 100 % sintética. | ⛔ [D‑16](16-decisiones-pendientes.md#d-16) |
| <a id="c-12"></a>C‑12 | El prompt cita unos términos de elTOQUE "vigentes desde agosto de 2026"; la página oficial consultada muestra **"Actualizado: 26 de febrero de 2024"**. | Cumplir la versión leída y **pedir confirmación** a elTOQUE de la versión aplicable. | ⛔ [D‑04](16-decisiones-pendientes.md#d-04) |

---

## 1.5 Qué se reutiliza, qué se reescribe, qué se descarta

| Elemento | Decisión | Motivo |
|---|---|---|
| Reglas de dominio (rendimiento por comensales/copas, estados de control, cierre/reapertura de día con motivo) | **Reutilizar como especificación** | Conocimiento de negocio validado en uso. |
| Suites de pruebas (123 + 14) | **Convertir en casos de aceptación** | Sirven de oráculo del comportamiento actual. |
| Matriz de permisos "módulo × (ver/editar/costos)" | **Generalizar** a RBAC con alcance + permiso `costs:view` | Ocultar costos en servidor es un buen patrón. |
| Cadena de auditoría HMAC | **Evolucionar** a eventos completos + bloques firmados | Añadir entidad, antes/después, motivo. |
| Esqueleto Android de `inventario` (Compose/Hilt/Room/WorkManager) | **Reutilizar la arquitectura**, reescribir pantallas y sync | Dependencias de 2023‑2024 (p. ej. Room 2.6.1, Compose BOM 2024.10.01) → actualizar. |
| `Security.kt` de IPV (Keystore AES‑GCM, biometría) | **Portar** | Útil; el pinning TOFU se sustituye. |
| Servidores Python stdlib | **Descartar** (migrar a Kotlin/Spring) | No escalan a multi‑tenant ni comparten motor con Android. |
| Web vanilla JS | **Reescribir** en Next.js/React/TS | Mantenibilidad, accesibilidad, componentes. |
| Esquemas de licencia propios (ECDSA / HMAC) | **Descartar** como mecanismo principal | Se sustituye por Keygen; el formato ECDSA sirve de plan B. |
| Estado JSON único y sync por reemplazo total | **Descartar** | Conflictos globales; incompatible con multiusuario. |
| `Double` para dinero, preguntas de seguridad, contraseñas demo | **Descartar** | Riesgo de integridad y de seguridad. |

---

## 1.6 Prioridades del prompt convertidas en decisiones

| Prioridad | Decisiones de diseño que la materializan |
|---|---|
| 1. Seguridad | Todo acceso a datos solo vía API; RBAC+ABAC en servidor; aislamiento por organización con RLS; secretos solo en servidor; licencias verificadas criptográficamente; Argon2id + MFA; Keystore + SQLCipher; revisión de cadena de suministro ([doc 11](11-estrategia-seguridad.md)). |
| 2. Trazabilidad | Auditoría completa y encadenada; versiones inmutables; instantáneas de tasa/costos; historial de estados; borrado lógico ([doc 12](12-estrategia-auditoria.md)). |
| 3. Corrección de datos | `NUMERIC`/`BigDecimal`; restricciones en BD (únicos parciales, exclusión de solapes, CHECK); motor de cálculo único compartido por servidor y Android; transiciones solo por comandos ([doc 3](03-modelo-er.md)). |
| 4. Mantenibilidad | Monolito modular con fronteras verificables; OpenAPI como contrato; ADR; CI con puertas de calidad ([doc 17](17-stack-recomendado.md)). |
| 5. Offline | Outbox idempotente + cursor de cambios + política de conflicto por entidad ([doc 9](09-flujo-android-offline-sync.md)). |
| 6. Rendimiento | Delta‑sync, gzip, índices, caché de tasas; medir antes de optimizar. |
| 7. Estética | Material 3 y componentes accesibles, sin comprometer lo anterior. |

---

## 1.7 Restricciones y supuestos de partida

1. **Conectividad en Cuba**: enlaces intermitentes y datos móviles costosos → *delta‑sync*, compresión, "solo Wi‑Fi" por defecto, reintentos con *backoff* (🧭, [doc 9](09-flujo-android-offline-sync.md)).
2. **Embargo de EE. UU.**: puede afectar a Keygen Cloud, a servicios en la nube de proveedores estadounidenses y a medios de pago. No es asesoría legal: requiere revisión ([R‑01](14-riesgos.md#r-01), [D‑05](16-decisiones-pendientes.md#d-05), [D‑06](16-decisiones-pendientes.md#d-06)).
3. **Normativa cubana**: no se codifica ninguna fórmula legal/contable; las reglas por categoría (Bebidas, Comidas, Servicios…) son **plantillas configurables, no normas oficiales** ([D‑02](16-decisiones-pendientes.md#d-02)).
4. **Equipo reducido**: se prefiere un monolito modular y una sola base de datos a microservicios.
5. **Presupuesto limitado**: autoalojable en un único servidor modesto; sin dependencias de pago por petición.
6. **Sin datos reales** en esta fase: semilla sintética.

---

## 1.8 Principios de arquitectura

1. **El servidor es la autoridad**; los clientes proponen, el servidor decide.
2. **Nada sensible en el cliente**: ni secretos de proveedores, ni acceso directo a la BD, ni banderas "premium".
3. **Inmutabilidad de lo aprobado**: lo vigente no se sobrescribe; se versiona y se reemplaza.
4. **Instantáneas, no referencias vivas**, para costos, tasas y reglas usadas en un documento.
5. **Una sola implementación del cálculo** (módulo `core:domain` en Kotlin/JVM, usado por servidor y Android).
6. **Dinero siempre decimal exacto**, con política de redondeo explícita y probada.
7. **Proveedores detrás de puertos** (`ExchangeRateProvider`, `LicenseProvider`) para poder cambiar de fuente sin tocar el dominio.
8. **Offline con conflictos explícitos**: nunca se pierde una edición en silencio.
9. **Auditar por diseño**: toda mutación genera un evento en la misma transacción.
10. **Lo desconocido se marca ⛔**: ninguna norma, fórmula ni endpoint se inventa.

---

## 1.9 Síntesis de hechos externos verificados

Detalle, URL y límites en el [anexo B](anexos/B-fuentes-y-verificaciones.md).

- **elTOQUE**: `GET /v1/trmi`, *bearer token* **por aplicación**, límites por clave API (60/min y 10/s por defecto; 429 con `Retry-After`); el swagger documenta solo `date_from`/`date_to` (intervalo < 24 h) y **no define el esquema de la respuesta 200**; los términos prohíben cambiar los datos, sublicenciar/revender y usarlos para fines distintos de mostrarlos en la aplicación, y obligan a citar a elTOQUE.
- **Keygen**: existen `expirationBasis=FROM_FIRST_ACTIVATION`, `renewalBasis=FROM_EXPIRY`, `expirationStrategy=REVOKE_ACCESS`; archivos de licencia/máquina firmados con TTL (por defecto 1 mes, mínimo 1 h); esquema `ECDSA_P256_SIGN` → archivos `…+ecdsa-p256`; **Keygen CE** es autoalojable (monoinquilino, Fair Core License) pero **sin** registros de eventos, entornos ni permisos finos (solo EE).
- **Android**: `androidx.security:security-crypto` está obsoleta.

## 1.10 Siguientes pasos

1. El propietario revisa este paquete y responde las decisiones de [doc 16](16-decisiones-pendientes.md) (y las del §37 del prompt, que **no** se responden aquí).
2. Tras validar el diseño: *spikes* S‑1…S‑7 del [roadmap](19-roadmap.md#spikes) (verificación técnica descartable, no producto).
3. Fase 2: modelo de datos y migraciones.
