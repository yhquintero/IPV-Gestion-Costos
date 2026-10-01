# IPV Gestión de Costos

Producto unificado que reúne [`IPV_Fichas-Costos`](https://github.com/yhquintero/IPV_Fichas-Costos) y [`inventario`](https://github.com/yhquintero/inventario) (Cuadre Pinar) en:

- **un sitio web HTTPS profesional** (panel de administración + sitio público), y
- **una app Android nativa en Kotlin** con trabajo sin conexión,

con usuarios, roles y permisos, auditoría completa y **licenciamiento por usuario con Keygen**. Pensado para operar en Cuba (conectividad limitada, tasas de referencia de elTOQUE).

## Estado

**Fase 5 de 10 — Android (en curso).** Fases 2–4 están en el repo; esta fase añade la app multimódulo (en línea + caché de lectura) y el evaluador de licencia en `core:domain`.

➡️ **Diseño:** [`docs/README.md`](docs/README.md) · **Fase 4:** [`docs/fases/04-web.md`](docs/fases/04-web.md) · **Fase 5:** [`docs/fases/05-android.md`](docs/fases/05-android.md)

```bash
# JDK 21 + Docker (Testcontainers) — Fases 2–3 (+ evaluador de licencia)
gradle test

# Web (Fase 4)
cd web && npm ci && npm run test:e2e

# Android (Fase 5) — SDK
cd android && gradle testDebugUnitTest assembleDebug
```

| Documento | Contenido |
|---|---|
| [Índice y resumen ejecutivo](docs/README.md) | Visión, hallazgos clave y mapa de los 20 entregables |
| [Decisiones pendientes](docs/16-decisiones-pendientes.md) | Lo que hay que definir antes de implementar |
| [ADR‑0001](docs/adr/0001-reabrir-offline-y-multisucursal.md) | Reabre offline y multi‑sucursal, con *gates* verificables |
| [Roadmap](docs/19-roadmap.md) | Las 10 fases y los *spikes* previos |

## Principios

Prioridad: **seguridad > trazabilidad > corrección de datos > mantenibilidad > offline > rendimiento > estética**. Sin secretos en el repositorio ni en los clientes; sin normas ni fórmulas legales inventadas (todo lo desconocido está marcado **PENDIENTE DE DEFINICIÓN**); las tasas de elTOQUE se consumen solo por su API oficial desde el servidor.

## Licencia del código

Pendiente de decisión ([D‑24](docs/16-decisiones-pendientes.md#d-24)). Los repositorios de origen son "todos los derechos reservados".
