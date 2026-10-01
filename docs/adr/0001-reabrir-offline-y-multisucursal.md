# ADR‑0001 · Reabrir la edición sin conexión y la multi‑sucursal en el producto unificado

| Campo | Valor |
|---|---|
| **Estado** | **Propuesto** — pendiente de aceptación del propietario del producto |
| **Fecha** | 2026‑10‑01 |
| **Reemplaza (*supersedes*)** | `IPV_Fichas-Costos` → `docs/decision-sin-multisucursal-offline.md` (2026‑09‑29, "aceptada provisionalmente"). Ese archivo vive en otro repositorio y **no se modifica**; se sugiere añadirle allí una nota "Reemplazada por ADR‑0001 de `IPV-Gestion-Costos`". |
| **Decide** | Propietario del producto (Ing. Yosvany Hernández Quintero) |
| **Relacionados** | [Doc 9 · Android offline→sync](../09-flujo-android-offline-sync.md) · [Doc 3 · Modelo ER](../03-modelo-er.md) · [Doc 11 · Seguridad](../11-estrategia-seguridad.md) · [Doc 19 · Roadmap](../19-roadmap.md) |

## 1. Contexto

El documento del 2026‑09‑29 **pospuso** la sincronización editable y la multi‑sucursal porque *"no se aportaron entrevistas, pilotos ni incidentes que prueben que sean hoy bloqueos prioritarios"* y porque ambas funciones *"cambian el modelo de seguridad y datos, no son una función aislada"*. Dejó explícitas cinco **condiciones para reabrir** y seis **supuestos de diseño**.

El prompt maestro del producto unificado (IPV + Cuadre Pinar) **exige ambas capacidades**: multi‑empresa/multi‑sucursal con PostgreSQL compartido y una app Android *offline‑first* con sincronización. Es un **mandato explícito del propietario del producto**, no evidencia de mercado. Esa diferencia se registra honestamente en la condición G‑0 más abajo.

Además, el repositorio `inventario` ya contiene un mecanismo de sincronización (estado JSON completo + versión global) que **no** satisface los supuestos de diseño previos: un conflicto entre dos usuarios invalida *todo* el estado ([anexo A](../anexos/A-analisis-de-brechas.md), fila 36).

## 2. Decisión

1. **Se reabre**: la edición offline en Android y la multi‑organización/empresa/sucursal forman parte del producto unificado.
2. **Se condiciona** su habilitación en producción a los *gates* G‑1…G‑3 (y G‑4 para pasar de piloto a disponibilidad general). Hasta entonces la sincronización de **escritura** no se activa para clientes reales.
3. **Despliegue escalonado** (conserva el supuesto 6 del documento previo): primero un dispositivo por usuario, datos sintéticos y un tipo de dato no crítico, sin eliminación ni aprobación; luego se amplía ([§5](#5-despliegue-escalonado)).
4. **Se conservan** los seis supuestos de diseño del documento anterior como **requisitos verificables** ([§4](#4-los-seis-supuestos-previos-como-requisitos)).

## 3. Condiciones para reabrir y gates verificables

| Gate | Condición original (2026‑09‑29) | Estado hoy | Criterio de aprobación |
|---|---|---|---|
| **G‑0** | "Varias organizaciones independientes reportan un caso reciente repetido que bloquea activación/retención." | **No cumplida** (sin evidencia aportada). Se decide por mandato del propietario. | Se registra como riesgo ([R‑04](../14-riesgos.md#r-04)) y se **mide en el piloto**: porcentaje de usuarios que trabajan sin conexión, mutaciones pendientes, conflictos por semana. |
| **G‑1** | "Se define si el producto será instalación local, alojamiento gestionado o ambos." | Abierta ([D‑07](../16-decisiones-pendientes.md#d-07)). Diseño provisional: SaaS gestionado multi‑organización, con la misma base de código desplegable por cliente. | Decisión escrita del propietario y reflejada en [doc 2](../02-diagrama-arquitectura.md) y [doc 20](../20-secretos-y-configuracion.md). |
| **G‑2** | "Hay diseño de aislamiento, matriz de amenazas, política de conflicto y recuperación revisados." | Diseñado en este paquete; **pendiente de revisión**. | (a) Aislamiento: `organization_id` en toda tabla de negocio + RLS forzado + claves compuestas ([doc 3](../03-modelo-er.md#34-invariantes-y-su-aplicación)); (b) matriz de amenazas ([doc 11](../11-estrategia-seguridad.md#113-modelo-de-amenazas)); (c) política de conflicto por entidad ([doc 9](../09-flujo-android-offline-sync.md#94-política-de-conflictos-por-entidad)); (d) runbook de recuperación probado en [doc 17](../17-stack-recomendado.md). Revisión por el propietario **y** por un revisor técnico independiente ([D‑22](../16-decisiones-pendientes.md#d-22)). |
| **G‑3** | "Existe prueba que simula desconexión, cambios concurrentes, permiso revocado, repetición de solicitudes y restauración." | **No existe** (no hay código). | Suite automatizada en CI con los cinco escenarios de la tabla siguiente, **100 % verde** en cada cambio que toque sincronización. |
| **G‑4** | "Costo de soporte/operación y criterio de salida del piloto son aceptables." | Abierta ([D‑22](../16-decisiones-pendientes.md#d-22), [D‑30](../16-decisiones-pendientes.md#d-30)). | Presupuesto mensual de infraestructura y soporte acordado + criterios de salida del piloto escritos **antes** de iniciarlo. |

### Escenarios mínimos de G‑3

| Id | Escenario | Resultado esperado |
|---|---|---|
| E‑1 | **Desconexión**: el dispositivo acumula N mutaciones sin red y reconecta. | Todas se aplican **exactamente una vez**, en orden; el estado local converge con el servidor. |
| E‑2 | **Cambios concurrentes**: dos dispositivos editan la misma entidad con la misma versión base. | Uno se aplica; el otro queda `CONFLICT` visible; **no se pierde ninguna edición**; ambas versiones son recuperables. |
| E‑3 | **Permiso revocado**: se revoca el rol (o vence la licencia) con el dispositivo sin conexión. | Al sincronizar, el servidor **rechaza** las mutaciones con motivo; el cliente no las trata como válidas y retira los datos fuera de alcance según política. |
| E‑4 | **Reintentos idempotentes**: el mismo lote se envía 5 veces (o se corta la respuesta). | Efecto único; respuestas idénticas por `mutation_id`. |
| E‑5 | **Restauración**: se restaura una copia de seguridad del servidor (el cursor retrocede). | Los clientes detectan `RESYNC_REQUIRED`, conservan sus mutaciones pendientes y reconcilian sin duplicar ni perder datos. |

## 4. Los seis supuestos previos como requisitos

| # | Supuesto del documento 2026‑09‑29 | Elemento de diseño | Verificación |
|---|---|---|---|
| 1 | La instalación conserva un `organization_id` único. | `organization_id NOT NULL` en toda tabla de negocio desde la primera migración; RLS con `FORCE ROW LEVEL SECURITY`. | Pruebas de fuga entre organizaciones (G‑2). |
| 2 | Movimientos de inventario y aprobaciones **no se fusionan en silencio**; se conservan autor, hora, versión base y estado de conflicto revisable. | Inventario = **libro mayor inmutable** (solo se añaden movimientos, sin fusión); aprobaciones = comandos de servidor **solo en línea**; el resto usa `base_version` y estado `CONFLICT` con ambas versiones. | E‑2. |
| 3 | Una respuesta del servidor prevalece solo tras verificar **permisos vigentes**; no se encola como válida una acción ya revocada. | El servidor re‑evalúa permisos y licencia **al procesar** cada mutación, no al encolarla; las rechazadas pasan a `REJECTED` con código de motivo. | E‑3. |
| 4 | Los clientes sin conexión muestran **la edad de los datos** y los cambios pendientes. | Banda permanente: "Datos al `<fecha/hora>` · *N* cambios pendientes"; contador y centro de conflictos. | Prueba de UI Android. |
| 5 | Cada operación sincronizada tiene **identificador idempotente, orden explícito, reintento con límite y bitácora de resolución**. | `mutation_id` (UUID), `seq` monótono por dispositivo, *backoff* con tope y *dead‑letter*; resultado persistido en `sync_mutations` y auditado. | E‑1, E‑4. |
| 6 | La primera prueba se limita a **un tipo de dato no crítico, un dispositivo por usuario y datos ficticios**; sin eliminación ni aprobación. | Despliegue escalonado de [§5](#5-despliegue-escalonado). | Criterios de salida de cada etapa. |

## 5. Despliegue escalonado

| Etapa | Alcance offline editable | Restricciones | Salida a la siguiente etapa |
|---|---|---|---|
| **6a** | Conteos de inventario (dato no crítico, solo se añaden). | 1 dispositivo por usuario (`maxMachines=1` en la política de piloto), datos sintéticos, sin eliminar ni aprobar. | E‑1…E‑5 verdes + sin pérdida de datos en 2 semanas de uso interno. |
| **6b** | Líneas de Control IPV y movimientos de inventario. | Hasta 2 dispositivos por usuario; aún sin aprobaciones offline. | Tasa de conflictos y rechazos dentro del umbral acordado ([D‑30](../16-decisiones-pendientes.md#d-30)). |
| **6c** | Borradores de Ficha de Costo (edición). | Los cambios de estado (enviar, validar, aprobar, vigencia, anular) **siguen siendo solo en línea**. | Revisión G‑4 y decisión del propietario. |

## 6. Alternativas consideradas

| Alternativa | Veredicto |
|---|---|
| **A.** Mantener el aplazamiento. | Rechazada: contradice el requisito expreso del propietario. |
| **B.** Offline *solo lectura* (caché) y escritura más adelante. | **Incluida como parte del escalonado** (la app es útil sin red desde la Fase 5; la escritura llega en 6a–6c). |
| **C.** Offline completo desde el primer día. | Rechazada: viola el supuesto 6 y el riesgo de integridad (R‑05). |
| **D.** Reabrir con *gates* y despliegue escalonado. | **Elegida.** |

## 7. Consecuencias

**Positivas**: cumple el requisito de producto; el aislamiento por organización se diseña desde el inicio (más barato que retro‑ajustarlo); el comportamiento offline queda definido y probado, no improvisado.

**Negativas / costes**: mayor complejidad (outbox, conflictos, tombstones, RLS) y de pruebas; el cálculo debe ser idéntico en servidor y Android (módulo `core:domain` compartido); aumenta la superficie de ataque (datos locales cifrados, licencia offline); sin evidencia de mercado (G‑0) el esfuerzo podría no rendir lo esperado.

**Neutras**: el documento del 2026‑09‑29 queda como histórico; sus conclusiones sobre *falta de evidencia* siguen siendo ciertas y por eso se mide en el piloto.

## 8. Cómo se sabrá que este ADR se cumple

- G‑1…G‑3 aprobados y registrados en el repositorio (enlace a la ejecución de CI de la suite E‑1…E‑5).
- Ningún cliente real escribe offline antes de que G‑2 y G‑3 estén en verde.
- Revisión de este ADR al cierre de la etapa 6a y de nuevo tras el piloto (G‑0/G‑4).
