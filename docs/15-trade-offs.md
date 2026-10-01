# 15. Trade‑offs

> Entregable §39‑15 · [Índice](README.md) · Cada fila registra **qué se gana y qué se paga**. Todo es 🧭 provisional salvo indicación.

## 15.1 Resumen de decisiones y sus costes

| Id | Decisión (elegida provisional) | Alternativas | A favor | Coste / en contra | Cuándo revisar |
|---|---|---|---|---|---|
| <a id="t-01"></a>T‑01 | **Monolito modular** | Microservicios | Transacciones únicas (ficha + auditoría + sync); operación simple; equipo pequeño | Fronteras que hay que **hacer cumplir** con pruebas; escalado conjunto | Si un módulo exige escalar o desplegarse aparte |
| <a id="t-02"></a>T‑02 | **Kotlin + Spring Boot** (motor `core:domain` compartido con Android) | NestJS, Ktor, FastAPI, Supabase, Firebase (§15.2) | Una sola implementación del cálculo/reglas/licencia; Spring Security y ecosistema maduros; `BigDecimal` nativo | Mayor huella de memoria y arranque más lento; curva de Spring; menos gente que TypeScript | Tras el spike S‑5 ([D‑31](16-decisiones-pendientes.md#d-31)) |
| <a id="t-03"></a>T‑03 | **Next.js con BFF** (sitio público SSG + panel) | SPA estática (Vite) + sitio aparte | Tokens fuera de JS; páginas públicas rápidas con poco ancho de banda; un solo proyecto | Un proceso Node más que operar; más superficie (SSR) | Si el BFF no aporta frente a una SPA con cookie |
| <a id="t-04"></a>T‑04 | **SQLCipher** en Room | Cifrado de disco del SO (FBE) | Protege la BD en dispositivos con *root* o extraídos | +tamaño de APK y algo de CPU; gestión de claves | Medir rendimiento en gama baja (S‑4) |
| <a id="t-05"></a>T‑05 | **TTL de archivo de máquina = gracia offline (7‑15 días)** | TTL corto (horas) o nulo | TTL largo tolera semanas sin red | TTL largo retrasa suspensión/revocación en dispositivos sin conexión; TTL nulo es irrevocable ✅ | Según datos de uso real y riesgo comercial |
| <a id="t-06"></a>T‑06 | **Conflictos explícitos** (ambas copias visibles) | Última escritura gana | Nunca se pierde una edición | Más UX y más código | Si los conflictos resultan raros |
| <a id="t-07"></a>T‑07 | **Aprobaciones solo en línea** | Aprobar offline | Efecto legal siempre con permisos/licencia/reglas vigentes | Un aprobador sin red no puede aprobar | Si el piloto lo exige |
| <a id="t-08"></a>T‑08 | **RLS forzado** + filtros en servicio | Solo filtros en la aplicación | Un fallo del servicio no filtra datos entre organizaciones | Complejidad de pruebas; pequeña penalización de rendimiento | Si el rendimiento lo exige |
| <a id="t-09"></a>T‑09 | **Auditoría con bloques firmados** (Merkle + firma + anclaje) | Cadena HMAC lineal (la de IPV) | Verificable por terceros; sin bloqueo global; rotación de claves | Más piezas (sellador, verificador, anclaje) | Spike S‑7 |
| <a id="t-10"></a>T‑10 | **Puerto `LicenseProvider`** (Keygen Cloud / CE / propio) | Atarse a Keygen Cloud | Plan B ante embargo, coste o caída | Abstracción extra; dos formatos que mantener si se usa el propio | [D‑05](16-decisiones-pendientes.md#d-05) |
| <a id="t-11"></a>T‑11 | **SaaS central** (multi‑organización) | Instalación por cliente | Una clave de elTOQUE, un Keygen, operación uniforme | Dependencia de conectividad; residencia de datos ⛔ | [D‑06](16-decisiones-pendientes.md#d-06)/[D‑07](16-decisiones-pendientes.md#d-07) |
| <a id="t-12"></a>T‑12 | **Sync con WorkManager** (sin *push*) | Notificaciones push (FCM) | Sin dependencia de servicios de Google; menos consumo de datos | Latencia de minutos | Si FCM es viable ([D‑20](16-decisiones-pendientes.md#d-20)) |
| <a id="t-13"></a>T‑13 | **Solo Wi‑Fi por defecto** para descargas grandes | Siempre datos móviles | Ahorra datos costosos | Actualizaciones diferidas | Según encuestas de uso |
| <a id="t-14"></a>T‑14 | **Pines SPKI con respaldo y caducidad** | Sin pines / pin TOFU | Mitiga CA comprometida o MITM | Riesgo de bloqueo por rotación mal gestionada | Cada rotación de certificado |
| <a id="t-15"></a>T‑15 | **"Asiento web" único** por licencia | Una máquina por navegador | Estable (los navegadores cambian); arranca `FROM_FIRST_ACTIVATION` | Limita sesiones solo con lógica propia | Spike S‑1 |
| <a id="t-16"></a>T‑16 | **DSL JSON cerrado** para reglas | Scripting/expresiones libres | Determinista, seguro, idéntico en Android | Menos flexible | Si aparecen reglas no expresables |
| <a id="t-17"></a>T‑17 | **`NUMERIC(19,4)` / `BigDecimal`** | Enteros en unidades menores | Legibilidad; tasas con muchos decimales | Disciplina de redondeo | [D‑25](16-decisiones-pendientes.md#d-25) |
| <a id="t-18"></a>T‑18 | **Guardar muestras de tasa solo si cambian** + registro de ejecuciones | Guardar cada lectura | Menos filas; historial limpio | Pierde "cuántas veces se confirmó el mismo valor" (queda en `rate_provider_runs`) | Si se necesita cada lectura |

## 15.2 Comparación de opciones de backend

Criterios y pesos alineados con el orden de prioridad del prompt (suma 100). Puntuación 1‑5; **son juicios razonados, no mediciones**.

| Criterio | Peso | Supabase | Firebase | NestJS + PG | Ktor + PG | **Spring Boot (Kotlin) + PG** | FastAPI + PG |
|---|---|---|---|---|---|---|---|
| Seguridad | 25 | 3 | 3 | 4 | 3 | **5** | 4 |
| Corrección de datos | 20 | 4 | 2 | 3 | 5 | **5** | 3 |
| Trazabilidad/auditoría | 10 | 3 | 2 | 4 | 4 | **5** | 4 |
| Mantenibilidad | 15 | 3 | 3 | 4 | 3 | **4** | 4 |
| Offline/sync (incl. motor compartido con Android) | 10 | 2 | 4 | 3 | 4 | **4** | 3 |
| Encaje con Cuba (costo, independencia de terceros, huella) | 15 | 1 | 1 | 4 | 5 | **4** | 5 |
| Rendimiento/huella | 5 | 4 | 4 | 4 | 5 | **3** | 3 |
| **Total ponderado (0‑100)** | | 57.0 | 51.0 | 74.0 | 80.0 | **90.0** | 76.0 |

**Sensibilidad** (calculada): con **pesos iguales** → Spring 85.7, Ktor 82.9, FastAPI/NestJS 74.3; con **"Encaje con Cuba" duplicado** → Spring 88.2, Ktor 83.6, FastAPI 79.1; **sin la ventaja del motor compartido** (Corrección y Offline más bajos en Kotlin) → Spring 84.0, FastAPI 76.0, Ktor/NestJS 74.0. El orden de cabeza **no cambia**.

**Lectura por opción**

| Opción | Motivo principal |
|---|---|
| **Spring Boot (Kotlin) + PostgreSQL** ✅ provisional | Seguridad y trazabilidad maduras; motor `core:domain` compartido con Android (la divergencia de cálculo es el riesgo #1 de corrección de datos); autoalojable sin costo por petición. Coste: más memoria. |
| Ktor + PostgreSQL | Más ligero y también comparte Kotlin, pero hay que **ensamblar a mano** autenticación, validación, OpenAPI y migraciones (más superficie para errores de seguridad). Buen plan B si la memoria es crítica. |
| FastAPI + PostgreSQL | Reutiliza conocimiento y pruebas de IPV (Python); pero **duplica el motor** frente a Android y no mejora la seguridad respecto a Spring. |
| NestJS + PostgreSQL | Comparte TypeScript con la web; duplica el motor frente a Android; manejo de decimales exige disciplina extra. |
| Supabase | PostgreSQL + RLS atractivos, pero **dependencia de un SaaS estadounidense** (embargo/latencia), lógica de dominio compleja fuera de RLS y sin sync offline para Android. |
| Firebase | Buen sync offline, pero **modelo no relacional** (sin FK ni restricciones), costos por lectura/escritura y dependencia de Google: mal ajuste a datos contables con integridad fuerte. |

> La decisión es **provisional**: se confirma tras un *spike* de una semana (S‑5) que mida memoria, arranque y esfuerzo real ([D‑31](16-decisiones-pendientes.md#d-31), futuro ADR‑0002).

## 15.3 Web: Next.js frente a alternativas

| Criterio | **Next.js (App Router, TS)** | SPA estática (Vite + React) | SvelteKit |
|---|---|---|---|
| Páginas públicas rápidas con poco ancho de banda | **SSG** | Requiere sitio aparte | SSG |
| Tokens fuera de JavaScript (BFF) | **Nativo** | Cookie del API; más fricción | Posible |
| Ecosistema y contratación | **Muy amplio** | Muy amplio | Menor |
| Operación | Proceso Node | **Solo archivos estáticos** | Proceso Node |
| Veredicto | ✅ elegido | Alternativa si el BFF no se usa | No elegido |

## 15.4 Android: decisiones menores

| Tema | Elegido | Motivo |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Ya usado en `inventario`; estándar actual |
| DI | Hilt | Ya usado; integración con WorkManager/ViewModel |
| Red | Retrofit + OkHttp + kotlinx.serialization | Pedido en el prompt; pines y gzip en OkHttp |
| Persistencia | Room + SQLCipher | Pedido en el prompt; cifrado en reposo |
| Preferencias seguras | **Keystore + DataStore (o Tink)** | `EncryptedSharedPreferences` está obsoleta ✅ |
