# 5. Casos de uso

> Entregable §39‑5 · [Índice](README.md) · Prioridad MVP: **M** = imprescindible, **S** = deseable, **C** = posterior (🧭 propuesta).

## 5.1 Actores

| Actor | Descripción | Rol sugerido (catálogo final ⛔ [D‑18](16-decisiones-pendientes.md#d-18)) |
|---|---|---|
| Administrador de plataforma | Personal que opera el producto (comercial y técnico). | `PLATFORM_ADMIN` (MFA obligatorio) |
| Administrador de organización | Responsable del cliente; gestiona empresas, usuarios y licencias. | `ORG_ADMIN` |
| Administrador de empresa | Gestiona catálogo, reglas y configuración de una empresa. | `COMPANY_ADMIN` |
| Costeador | Elabora fichas y valores IPV. | `COSTEADOR` |
| Revisor | Valida cálculo y coherencia de las fichas. | `REVISOR` |
| Aprobador | Aprueba y activa fichas; anula. | `APROBADOR` |
| Controlador IPV | Ejecuta controles periódicos. | `CONTROLADOR_IPV` |
| Almacenero | Registra movimientos y conteos. | `ALMACENERO` |
| Económico | Consulta costos, tasas, reportes y cierres. | `ECONOMICO` |
| Auditor | Solo lectura, incluida la auditoría. | `AUDITOR` |
| Lector | Consulta sin ver costos. | `LECTOR` |
| Sistema | Trabajos programados (tasas, vencimientos, activaciones). | — |
| Proveedores externos | elTOQUE (tasas), Keygen (licencias, *webhooks*). | — |

## 5.2 Catálogo de casos de uso

| Id | Caso de uso | Actor primario | Módulos | Offline | MVP |
|---|---|---|---|---|---|
| UC‑01 | Registrar organización y empresa | Admin de plataforma | tenancy, commerce | No | M |
| UC‑02 | Crear sucursales | Admin de organización | tenancy | No | M |
| UC‑03 | Crear usuarios y asignar roles con alcance | Admin de organización | identity, access | No | M |
| UC‑04 | Definir roles personalizados | Admin de organización | access | No | S |
| UC‑05 | **Iniciar sesión** (con MFA) | Cualquiera | identity, licensing | Parcial¹ | M |
| UC‑06 | Recuperar acceso | Cualquiera | identity | No | M |
| UC‑07 | Gestionar sesiones y dispositivos | Cualquiera | identity, licensing | No | S |
| UC‑08 | Gestionar categorías, unidades y proveedores | Admin de empresa | catalog | No | M |
| UC‑09 | Gestionar insumos y productos/servicios | Costeador | catalog | No | M |
| UC‑10 | Registrar **Valor IPV** y consultar historial | Costeador | ipv-values | No | M |
| UC‑11 | Registrar movimientos y conteos de inventario | Almacenero | inventory, sync | **Sí** | S |
| UC‑12 | **Crear borrador de ficha** desde valores IPV | Costeador | costing | Sí (etapa 6c) | M |
| UC‑13 | Editar borrador (líneas, subfichas, rendimiento) | Costeador | costing | Sí (etapa 6c) | M |
| UC‑14 | Pre‑validar con reglas | Costeador | rules | Sí (lectura) | M |
| UC‑15 | Enviar a revisión | Costeador | costing, workflow | No | M |
| UC‑16 | Revisar: validar o devolver | Revisor | costing, workflow | No | M |
| UC‑17 | Aprobar | Aprobador | costing, workflow | No | M |
| UC‑18 | **Activar** (poner VIGENTE) y reemplazar la vigente | Aprobador | costing | No | M |
| UC‑19 | **Anular** una ficha | Aprobador | costing, ipv-control | No | M |
| UC‑20 | Crear nueva versión desde una existente | Costeador | costing | No | M |
| UC‑21 | **Registrar un Control IPV** | Controlador IPV | ipv-control, sync | **Sí** | M |
| UC‑22 | Validar/cerrar un Control IPV y analizar diferencias | Controlador IPV | ipv-control | No | M |
| UC‑23 | Configurar reglas (conjuntos y versiones) | Admin de empresa | rules | No | S |
| UC‑24 | Ver tasas de referencia y su estado | Cualquiera | rates | Sí (última conocida) | M |
| UC‑25 | Registrar tasa manual (con motivo y aprobación) | Económico | rates | No | S |
| UC‑26 | **Sincronizar** y resolver conflictos | Cualquiera (Android) | sync | — | M |
| UC‑27 | **Activar licencia** en un dispositivo / acceso web | Usuario licenciado | licensing | Parcial² | M |
| UC‑28 | Ver estado de licencia; desactivar un dispositivo | Usuario / Admin | licensing | Sí (estado local) | M |
| UC‑29 | **Renovar licencia** (registrar pago) | Admin de plataforma | commerce, licensing | No | M |
| UC‑30 | Suspender, reactivar o revocar licencia | Admin de plataforma | licensing | No | S |
| UC‑31 | Administrar precios, contratos, pagos y recibos | Admin de plataforma | commerce | No | S |
| UC‑32 | Consultar reportes y tablero; exportar | Económico | reporting | Parcial | S |
| UC‑33 | Consultar auditoría y verificar integridad | Auditor | audit | No | M |
| UC‑34 | Recibir notificaciones | Cualquiera | notifications | Sí (bandeja) | S |
| UC‑35 | Respaldar y restaurar (operación) | Operaciones | plataforma | — | M |
| UC‑36 | Cerrar/reabrir período con motivo | Económico | cuadre | No | C |
| UC‑37 | Convertir una prueba (trial) en licencia de pago | Admin de plataforma | commerce, licensing | No | S |

¹ Con licencia y archivo de máquina vigentes, un usuario ya autenticado puede **desbloquear** la app sin red (biometría/PIN); el primer inicio de sesión y el cambio de contraseña requieren conexión. ² La primera activación requiere conexión.

## 5.3 Casos de uso críticos

### UC‑05 · Iniciar sesión con MFA

- **Precondición**: usuario activo; licencia asignada (o rol de plataforma).
- **Flujo**: (1) correo + contraseña → (2) si hay MFA, código TOTP o de recuperación → (3) el servidor comprueba estado de licencia y derechos del cliente (`WEB_ACCESS` o `ANDROID_ACCESS`) → (4) emite tokens cortos (web: cookie `HttpOnly` vía BFF; Android: *access* + *refresh* rotatorio) → (5) auditoría `LOGIN_SUCCESS`.
- **Alternativos**: credenciales erróneas (respuesta uniforme + límite de tasa + bloqueo progresivo); MFA fallido; **licencia vencida/suspendida** → `403 LICENSE_*` con el estado a mostrar; dispositivo nuevo sobre el límite → ofrecer desactivar otro.
- **Reglas**: sin "preguntas de seguridad"; las contraseñas no se muestran ni se registran.

### UC‑12/15/17/18 · De borrador a vigente

- **Precondición**: existen Valores IPV vigentes para los insumos; usuario con `costing:create` y `costs:view`.
- **Flujo**: borrador → pre‑validar (UC‑14) → enviar (UC‑15: reglas bloqueantes, **congelar instantánea de tasas**, calcular, `content_hash`) → validar (UC‑16) → aprobar (UC‑17) → activar (UC‑18: la anterior pasa a REEMPLAZADA en la misma transacción).
- **Alternativos**: reglas con errores bloquean el envío; el revisor **devuelve** con comentarios (vuelve a BORRADOR); `content_hash` distinto al validar/aprobar → rechazo y alerta de integridad; activación con fecha futura → queda APROBADA hasta esa fecha.
- **Postcondición**: una sola versión VIGENTE; todas las transiciones con actor, hora, motivo y antes/después en auditoría. Ver [doc 6](06-flujo-ficha-ipv.md).

### UC‑19 · Anular una ficha

- **Precondición**: permiso `costing:annul`; estado EN_REVISIÓN, VALIDADA, APROBADA o VIGENTE.
- **Flujo**: el aprobador indica un **motivo obligatorio** → estado ANULADA (irreversible) → se bloquean **nuevas** líneas de control sobre esa versión y se notifica a los controles abiertos que la usan.
- **Reglas**: no se borra nada; si era la VIGENTE queda el producto **sin ficha vigente** hasta activar otra (aviso destacado); la versión REEMPLAZADA anterior **no** se reactiva sola (se crea una nueva versión a partir de ella).

### UC‑21 · Registrar un Control IPV sin conexión

- **Precondición**: el dispositivo tiene sincronizadas las fichas vigentes de su alcance y la licencia offline vigente.
- **Flujo**: crear control local (referencia `LOCAL‑xxxx`) → capturar líneas contra versiones de ficha → guardar en la **outbox** → al reconectar, el servidor valida cada mutación (permisos y licencia vigentes **ahora**; versión no anulada y vigente para el período) → asigna el **número definitivo** → `APPLIED`, `CONFLICT` o `REJECTED` con motivo.
- **Alternativos**: versión anulada entretanto → línea `REJECTED: FICHA_ANULADA` (se conserva en el dispositivo para revisión, **nunca se pierde en silencio**); permisos revocados → todo `REJECTED` y se retira lo que salió de alcance.
- **Regla visible**: el usuario siempre ve la edad de los datos y los cambios pendientes.

### UC‑27 · Activar licencia en un dispositivo Android

- **Precondición**: usuario autenticado con licencia asignada y por debajo del máximo de dispositivos.
- **Flujo**: la app genera su identificador de instalación (aleatorio, protegido por Keystore) → `POST /licenses/activate` → el servidor registra el dispositivo en el proveedor (Keygen *machine*) → devuelve el **archivo de máquina firmado** (TTL = periodo de gracia offline) → la app **verifica la firma** con la clave pública incrustada y evalúa el estado.
- **Alternativos**: límite de dispositivos alcanzado → listar y permitir desactivar uno; proveedor no disponible → se reintenta, la activación **no** se simula localmente.
- **Reglas**: la app no guarda una bandera "premium"; el estado se **recalcula** cada vez desde el archivo firmado y un reloj fiable ([doc 7](07-flujo-licencias-keygen.md)).

### UC‑29 · Renovar licencia

- **Flujo**: el administrador registra el pago (con `price_usd`, equivalente en CUP, tasa usada y fecha) → el sistema solicita la renovación al proveedor con clave de idempotencia → actualiza `expires_at` → notifica al usuario → el dispositivo recibe el archivo nuevo en su próxima sincronización.
- **Reglas**: la base de renovación y el caso de renovación tardía están en [doc 8](08-flujo-renovacion.md) (⛔ [D‑12](16-decisiones-pendientes.md#d-12)).

### UC‑24 · Consultar tasas con la API de elTOQUE caída

- **Flujo**: el servidor ya no obtiene datos nuevos → sirve el **último valor válido** con `FUENTE: elTOQUE · ESTADO: DATOS EN CACHÉ` y la hora de esa lectura → la UI muestra "**Última actualización disponible**" y "**Tasa de referencia, no oficial**".
- **Reglas**: **nunca** se inventa, interpola ni se cambia la fuente en silencio; si pasa un umbral de vejez, las instantáneas nuevas para fichas exigen aviso o tasa manual con motivo ([doc 13](13-estrategia-actualizacion-tasas.md)).
