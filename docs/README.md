# IPV Gestión de Costos · Paquete de análisis de arquitectura (Fase 1)

| | |
|---|---|
| **Estado** | Fase 1 entregada · Fases 2–6 en el repo · **Fase 7 licencias/comercial en curso** — [fases/07](fases/07-licencias.md) |
| **Fecha** | 2026‑10‑01 |
| **Objetivo del producto** | Unir `IPV_Fichas-Costos` e `inventario` (Cuadre Pinar) en **un solo sitio web HTTPS profesional** y **una sola app Android (Kotlin)**, con usuarios, roles, HTTPS y licenciamiento Keygen por usuario |
| **Prioridades** | Seguridad > Trazabilidad > Corrección de datos > Mantenibilidad > Offline > Rendimiento > Estética |

Este paquete cumple la **primera ejecución exigida por el §39 del prompt maestro**: el análisis de arquitectura **antes** de escribir la aplicación. Se implementará después, por fases, cuando el diseño esté validado.

---

## Resumen ejecutivo

### La propuesta (provisional)

- **Un monolito modular** en **Kotlin + Spring Boot** sobre **PostgreSQL** multi‑organización (aislamiento con RLS), un **sitio Next.js** con BFF y una **app Android** Compose *offline‑first* (Room + SQLCipher). El **motor de cálculo, reglas y evaluación de licencias (`core:domain`) es el mismo código** en servidor y Android. → [doc 2](02-diagrama-arquitectura.md), [doc 4](04-modulos.md), [doc 17](17-stack-recomendado.md)
- **Ficha de Costo versionada e inmutable** (7 estados) con **instantáneas** de costos y de tasa de cambio; **Control IPV** ligado a *versiones* de ficha, no 1:1. → [doc 6](06-flujo-ficha-ipv.md)
- **Licencias por usuario con Keygen** detrás de un puerto intercambiable (Cloud, CE o propio); archivos de máquina firmados, gracia offline de 7‑15 días, estado recalculado y nunca guardado como bandera. → [doc 7](07-flujo-licencias-keygen.md), [doc 8](08-flujo-renovacion.md)
- **Tasas de elTOQUE** solo por la API oficial y desde el backend, con caché, historial y etiquetas obligatorias. → [doc 10](10-flujo-eltoque-cache.md), [doc 13](13-estrategia-actualizacion-tasas.md)
- **Offline editable** con outbox idempotente y conflictos explícitos, desplegado por etapas y con *gates* verificables. → [doc 9](09-flujo-android-offline-sync.md), [ADR‑0001](adr/0001-reabrir-offline-y-multisucursal.md)
- **Auditoría** completa con bloques firmados y verificables. → [doc 12](12-estrategia-auditoria.md)

### Hallazgos que más importan

1. **Los repos no se pueden fusionar por copia**: `inventario` guarda todo el estado en **un único JSON** y usa **`Double` para dinero**; IPV usa SQLite sin organizaciones ni sucursales. Se rediseña el modelo y se reutiliza el conocimiento de dominio. → [doc 1](01-analisis-arquitectonico.md), [anexo A](anexos/A-analisis-de-brechas.md)
2. **"IPV" tiene dos sentidos en el código**: *Valores del IPV* (antes de la Ficha) y *Controles de IPV* (después). Puede reconciliar el orden que describes, pero **está sin confirmar** y no hay norma verificada. → [D‑01](16-decisiones-pendientes.md#d-01)
3. **Las licencias actuales son falsificables por quien aloja el servidor** (el modelo de instalación de los repos origen): `inventario` firma con **HMAC simétrico** cuyo secreto está en ese mismo servidor, y su app guarda `valid=true`. → [H‑06](01-analisis-arquitectonico.md#12-hallazgos-que-más-condicionan-el-diseño)
4. **elTOQUE**: el swagger oficial **no define la respuesta**, los términos limitan el uso (una clave por aplicación, no revender, no modificar, solo mostrar) y **no consta que la API devuelva CAD/MXN/ZELLE/CLA**. Hace falta token y confirmación escrita. → [anexo B](anexos/B-fuentes-y-verificaciones.md#b1-eltoque-api-de-tasas), [D‑04](16-decisiones-pendientes.md#d-04)
5. **Keygen**: Cuba está bajo embargo integral de EE. UU. y los términos de Keygen Cloud se rigen por ley de Texas (no leí las secciones 2‑9). **Keygen CE** es autoalojable, pero según el README de Keygen el registro de eventos, los entornos y los permisos finos son funciones de EE. Se necesita **dictamen legal** antes de contratar. → [R‑01](14-riesgos.md#r-01), [D‑05](16-decisiones-pendientes.md#d-05)
6. **Con `renewalBasis = FROM_EXPIRY` puro, renovar tarde da menos de lo pagado**: una licencia mensual renovada 45 días después de vencer **sigue vencida** tras pagar. `FROM_NOW_IF_EXPIRED` lo evita. Decisión tuya. → [doc 8](08-flujo-renovacion.md#82-escenarios-con-ejemplos), [D‑12](16-decisiones-pendientes.md#d-12)

### Lo que necesito de ti

Las decisiones que desbloquean lo demás están ordenadas en [§16.1](16-decisiones-pendientes.md#161-las-decisiones-que-condicionan-todo-lo-demás-resolver-primero): **D‑05/D‑06** (proveedor de licencias, hosting y dictamen), **D‑01/D‑02** (IPV y normativa), **D‑03/D‑04** (tasa y token de elTOQUE), **D‑08/D‑09/D‑12** (precios, derechos, renovación) y **D‑15/D‑07** (alcance de Cuadre y modelo de despliegue). Además, el [ADR‑0001](adr/0001-reabrir-offline-y-multisucursal.md) espera tu aceptación.

---

## Mapa de los 20 entregables (§39)

| § | Entregable | Documento |
|---|---|---|
| 1 | Análisis arquitectónico | [01 · Análisis arquitectónico](01-analisis-arquitectonico.md) (+ [anexo A](anexos/A-analisis-de-brechas.md)) |
| 2 | Diagrama de arquitectura (texto) | [02 · Diagrama de arquitectura](02-diagrama-arquitectura.md) |
| 3 | Modelo entidad‑relación | [03 · Modelo ER](03-modelo-er.md) |
| 4 | Módulos | [04 · Módulos](04-modulos.md) |
| 5 | Casos de uso | [05 · Casos de uso](05-casos-de-uso.md) |
| 6 | Flujo Ficha → IPV | [06 · Flujo Ficha ↔ IPV](06-flujo-ficha-ipv.md) |
| 7 | Flujo de licencias Keygen | [07 · Licencias con Keygen](07-flujo-licencias-keygen.md) |
| 8 | Flujo de renovación | [08 · Renovación](08-flujo-renovacion.md) |
| 9 | Flujo Android offline → sync | [09 · Android offline → sync](09-flujo-android-offline-sync.md) |
| 10 | Flujo backend → API elTOQUE → caché → Web/Android | [10 · elTOQUE y caché](10-flujo-eltoque-cache.md) |
| 11 | Estrategia de seguridad | [11 · Seguridad](11-estrategia-seguridad.md) |
| 12 | Estrategia de auditoría | [12 · Auditoría](12-estrategia-auditoria.md) |
| 13 | Estrategia de actualización de tasas | [13 · Actualización de tasas](13-estrategia-actualizacion-tasas.md) |
| 14 | Riesgos | [14 · Riesgos](14-riesgos.md) |
| 15 | Trade‑offs | [15 · Trade‑offs](15-trade-offs.md) (incluye la comparación de backends) |
| 16 | Decisiones pendientes | [16 · Decisiones pendientes](16-decisiones-pendientes.md) |
| 17 | Stack recomendado | [17 · Stack recomendado](17-stack-recomendado.md) |
| 18 | Estructura inicial del repositorio | [18 · Estructura del repositorio](18-estructura-repo.md) |
| 19 | Roadmap por fases | [19 · Roadmap](19-roadmap.md) |
| 20 | Lista de secretos y variables de configuración | [20 · Secretos y configuración](20-secretos-y-configuracion.md) |

**Complementos**: [ADR‑0001 (reemplaza el aplazamiento de offline/multi‑sucursal)](adr/0001-reabrir-offline-y-multisucursal.md) · [índice de ADR](adr/README.md) · [Anexo A · brechas de los repos origen](anexos/A-analisis-de-brechas.md) · [Anexo B · fuentes y verificaciones](anexos/B-fuentes-y-verificaciones.md)

---

## Leyenda y convenciones

| Marca | Significado |
|---|---|
| ✅ | Verificado en documentación oficial leída el 2026‑10‑01 |
| 🔎 | Observado en el código/documentación de los repos origen |
| 🧭 | Propuesta o decisión **provisional** |
| ⛔ | **PENDIENTE DE DEFINICIÓN** (no se inventa) |

Identificadores: `C‑xx` conflictos · `D‑xx` decisiones pendientes · `R‑xx` riesgos · `T‑xx` trade‑offs · `UC‑xx` casos de uso · `M‑xx` módulos · `S‑x` *spikes* · `G‑x` *gates* del ADR · `I‑xx` invariantes de datos.

## Alcance y límites de esta fase

- **Fase 1** no escribió código de producto. **Fase 2** añade el esqueleto Gradle, las migraciones y `core:domain` ([fases/02](fases/02-modelo-de-datos.md)). No se llamó a elTOQUE ni a Keygen, no se compiló Android y **no se importó ningún dato real ni credencial** de los repos origen.
- Los **§36 (30 criterios de aceptación) y §37 (preguntas abiertas)** del prompt maestro **no se reproducen ni se responden aquí**: la matriz de trazabilidad con el §36 y el cruce con el §37 se hacen al validar el diseño.
- Ninguna norma, fórmula contable ni endpoint de terceros se inventa: lo desconocido está marcado ⛔ y lo verificado tiene su fuente en el [anexo B](anexos/B-fuentes-y-verificaciones.md).
- Los valores de tasas de prueba (30/09/2026 12:57) son **semilla**, no constantes.
