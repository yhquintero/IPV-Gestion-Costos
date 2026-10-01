# IPV Gestión de Costos

Producto unificado que reúne [`IPV_Fichas-Costos`](https://github.com/yhquintero/IPV_Fichas-Costos) y [`inventario`](https://github.com/yhquintero/inventario) (Cuadre Pinar) en:

- **un sitio web HTTPS profesional** (panel de administración + sitio público), y
- **una app Android nativa en Kotlin** con trabajo sin conexión,

con usuarios, roles y permisos, auditoría completa y **licenciamiento por usuario con Keygen**. Pensado para operar en Cuba (conectividad limitada, tasas de referencia de elTOQUE).

## Estado

**Fase 9 de 10 — endurecimiento (en curso).** Fases 2–8 están en el repo. Pentest, ZAP, k6 y restauración **no se ejecutan aquí**.

➡️ **Diseño:** [`docs/README.md`](docs/README.md) · **Fase 8:** [`docs/fases/08-eltoque.md`](docs/fases/08-eltoque.md) · **Fase 9:** [`docs/fases/09-endurecimiento.md`](docs/fases/09-endurecimiento.md)

```bash
# JDK 21 + Docker (Testcontainers)
gradle test
gradle :core:domain:test --tests cu.ipvgc.domain.security.* --tests cu.ipvgc.domain.sync.*

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
