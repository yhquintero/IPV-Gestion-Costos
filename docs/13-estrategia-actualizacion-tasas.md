# 13. Estrategia de actualización de tasas

> Entregable §39‑13 · [Índice](README.md) · Arquitectura y contrato con elTOQUE: [doc 10](10-flujo-eltoque-cache.md). Los valores numéricos marcados 🧭 son **parámetros iniciales configurables**, no constantes del sistema.

## 13.1 Planificación

| Parámetro | Valor inicial 🧭 | Variable |
|---|---|---|
| Intervalo de refresco | **5 min** (mínimo permitido: 60 s) | `ELTOQUE_REFRESH_INTERVAL_SECONDS` |
| Variación aleatoria (*jitter*) | ± 10 % del intervalo | `ELTOQUE_REFRESH_JITTER_PCT` |
| Tiempo de espera por petición | 10 s | `ELTOQUE_TIMEOUT_MS` |
| Exclusión mutua | Un solo líder por ciclo (bloqueo asesor de PostgreSQL o ShedLock) para que varias instancias del API **no** consulten a la vez | — |

**Presupuesto de cuota**: a 5 min son **12 peticiones/hora** frente al límite de **3 600/hora** (60/min) de la clave ✅, es decir ~0,3 %. Los reintentos usan un **limitador propio** que nunca supera 60/min ni 10/s, ni siquiera tras una caída.

## 13.2 Qué se pide

- Siempre `GET /v1/trmi` **sin fechas** (la API devuelve la mediana de las **últimas 24 h** ✅).
- **Sin *backfill* masivo** del histórico: el historial propio empieza cuando arranca el sistema. Una consulta puntual con `date_from`/`date_to` (intervalo < 24 h ✅) solo la lanza un administrador para reconstruir un hueco concreto, respetando cuota (⛔ confirmar con elTOQUE que es aceptable).

## 13.3 Validación de la respuesta

1. HTTP 200 y JSON bien formado; `Content-Type` esperado.
2. Estructura: existe `tasas`; cada valor es **numérico y > 0**; no hay `NaN`/infinito.
3. **Mapeo** de códigos del proveedor a instrumentos (`ECU → EUR`); los códigos desconocidos se **registran** (alerta de *drift*) y no se usan.
4. Marca temporal: se guarda **tal como llega** (`source_ts_raw`); `source_ts_utc` queda nulo hasta confirmar la zona horaria ([doc 10](10-flujo-eltoque-cache.md#103-contrato-con-eltoque-lo-verificado-y-lo-que-no)).
5. `payload_hash` (SHA‑256 del cuerpo) para detectar respuestas repetidas.
6. **Anomalía**: variación respecto a la muestra anterior mayor que un umbral configurable (⛔ valor inicial a fijar con datos reales; el prompt solo exige no inventar) → se marca `anomaly`, se alerta a administradores y se muestra aviso; **no se descarta** (el mercado puede moverse con fuerza) ni se "corrige".

Una respuesta que falla 1‑3 **no se almacena como muestra** y cuenta como `INVALID_PAYLOAD`; el sistema sigue sirviendo la última muestra válida (estado `CACHED`).

## 13.4 Almacenamiento

- **`rate_provider_runs`**: **cada** llamada (resultado, HTTP, latencia, cuota restante, `retry_after`). Retención corta (p. ej. 90 días, ⛔).
- **`exchange_rate_samples`**: solo cuando **cambia** el valor o la marca de la fuente (evita miles de filas idénticas); **inmutables** ([I‑12](03-modelo-er.md#34-invariantes-y-su-aplicación)). Volumen de referencia: 7 instrumentos × 288 lecturas/día ≈ 2 016 filas/día como **máximo** → trivial para PostgreSQL.
- **`exchange_rate_current`**: puntero transaccional a la muestra vigente por instrumento y su estado.
- **Historial**: se conserva (consulta paginada); la poda, si existe, es política ⛔ [D‑17](16-decisiones-pendientes.md#d-17).

## 13.5 Reintentos y *backoff*

| Respuesta | Acción |
|---|---|
| **429** | Respetar `Retry-After` ✅; no reintentar antes; registrar `RATE_LIMITED`. |
| **5xx**, *timeout*, red | *Backoff* exponencial con *jitter* (30 s → 60 s → 2 min … tope 15 min); estado `CACHED` mientras tanto. |
| **401 / 422** (token inválido) | **No reintentar**; alerta crítica de configuración; el planificador queda pausado hasta corregirlo. |
| **400** (intervalo ≥ 24 h) | Error de programación: alerta y no reintentar. |
| Desafío de Cloudflare / HTML en lugar de JSON | **Tratar como fallo** (`INVALID_PAYLOAD`); **no se intenta superar el desafío**. |
| N fallos seguidos | **Cortacircuitos** abierto; sondeo de recuperación a intervalos crecientes. |

## 13.6 Estados y umbrales

Estados y textos: [doc 10 §10.5](10-flujo-eltoque-cache.md#105-estados-de-la-tasa). Umbrales 🧭:

| Umbral | Valor inicial | Efecto |
|---|---|---|
| Ventana de frescura | 2 × intervalo (10 min) | Pasado esto: `STALE` / "Última actualización disponible". |
| Vejez máxima para **instantáneas nuevas** (`max_stale_minutes`, por empresa) | ⛔ [D‑03](16-decisiones-pendientes.md#d-03) | Superada: no se congela una instantánea desde elTOQUE; se exige tasa manual con motivo o se bloquea el envío. |
| Alerta de operaciones | Sin éxito en 3 × intervalo | Notificación a plataforma. |

## 13.7 Variación

La subida/bajada que se muestra se calcula **con nuestro historial**: `variación = valor_actual − valor_de_la_muestra_anterior_almacenada` (absoluta y porcentual), etiquetada "respecto a la muestra anterior almacenada". La API de elTOQUE **no** entrega variación ✅, y la del sitio web puede diferir.

## 13.8 Instantáneas

| Cuándo | Contenido | Garantía |
|---|---|---|
| Enviar ficha a revisión | Valor, fuente, estado y hora de los instrumentos que usa la ficha | Inmutable; `content_hash` único y deduplicado |
| Crear ítem de contrato/pago | Tasa USD usada para el equivalente en CUP | Se guardan `exchange_rate_used`, `pricing_date` y `rate_sample_id` |
| Cuadre diario *(posterior)* | Tasa del día | Igual |

**Cadena de selección** (`rate_policies`): `ELTOQUE_API` fresca → `CACHED` solo si su edad ≤ `max_stale_minutes` → `MANUAL` (con motivo y, si la política lo exige, aprobación) → **bloquear**. Nunca se mezcla una fuente con otra sin dejar el rastro en la instantánea.

## 13.9 Tasa manual

- Permiso `rates:manual`; **motivo obligatorio**; aprobación opcional por política; vigencia y fecha.
- Se etiqueta **MANUAL**, **nunca** como elTOQUE.
- Auditoría completa (`RATE.MANUAL.SET`) y alerta a administradores.

## 13.10 Observabilidad

| Métrica / alerta | Para qué |
|---|---|
| `rates_last_success_age_seconds` | Detectar fuente estancada |
| `rates_provider_runs_total{outcome}` | Ver 429, 401, errores |
| `rates_ratelimit_remaining` | Vigilar cuota (cabeceras `X-RateLimit-*` ✅) |
| `rates_latency_ms` | Salud del enlace |
| Alertas: sin éxito > 3 × intervalo · 401/422 · racha de 429 · *drift* de contrato · anomalía de valor | Operación |

Pantalla **/plataforma/tasas**: estado del proveedor, última ejecución, cuota, anomalías y muestras recientes.

## 13.11 Semillas y datos de prueba

Valores aportados por el usuario (30/09/2026 12:57; **no verificados**; solo para desarrollo/pruebas):

| USD | EUR | MLC | CAD | MXN | ZELLE | CLA |
|---|---|---|---|---|---|---|
| 755.00 | 850.00 | 492.76 | 487.62 | 52.13 | 724.60 | 680.37 |

- Se cargan como `source = SEED_TEST`, `is_test = true`, con `fetched_at` real de la carga y **nunca** como constantes en el código.
- El perfil de **producción se niega a arrancar** si encuentra muestras de prueba y la UI las rotula **"DATOS DE PRUEBA"** en cualquier entorno.
- Las pruebas usan un **servidor simulado** del proveedor con *fixtures* saneados: **la integración continua nunca llama a la API real**.

## 13.12 Cumplimiento de términos

Lista de verificación (revisada en cada versión): una clave por aplicación · clave solo en el servidor · atribución visible · valores sin modificar · sin raspado ni extracción masiva · sin reventa ni API pública de tasas · sin lenguaje de "oficial" ni patrocinio · consulta escrita a elTOQUE (`desarrollo@eltoque.com`) sobre almacenamiento de historial, instantáneas en documentos y retransmisión a clientes licenciados ([D‑04](16-decisiones-pendientes.md#d-04)).

## 13.13 Pruebas

| Prueba | Objetivo |
|---|---|
| Contrato con *fixtures* grabados | El analizador acepta lo esperado y rechaza lo inválido |
| Propiedades | Nunca se almacenan valores ≤ 0; el valor guardado = el recibido (sin redondeos) |
| Inyección de fallos | Timeouts, 429, 401, JSON truncado, HTML de desafío |
| Viaje en el tiempo | Transiciones `FRESH → STALE → CACHED → UNAVAILABLE` con reloj controlado |
| Zona horaria | Fecha de negocio en `America/Havana` y cambios de horario |
| Seguridad | La clave no aparece en logs, respuestas ni artefactos de Android/JS |

## 13.14 Pendientes

[D‑03](16-decisiones-pendientes.md#d-03) (qué tasa vale en documentos oficiales) · [D‑04](16-decisiones-pendientes.md#d-04) (token, términos, monedas, zona horaria) · [D‑32](16-decisiones-pendientes.md#d-32) (no implementar *scraping*).
