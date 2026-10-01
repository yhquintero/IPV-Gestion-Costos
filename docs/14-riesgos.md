# 14. Riesgos

> Entregable §39‑14 · [Índice](README.md) · Escala: probabilidad **P** e impacto **I** = Baja (B), Media (M), Alta (A). El "dueño" de cada riesgo es ⛔ ([D‑22](16-decisiones-pendientes.md#d-22)).

## 14.1 Registro de riesgos

| Id | Riesgo | P | I | Mitigación | Señal temprana |
|---|---|---|---|---|---|
| <a id="r-01"></a>**R‑01** | **Embargo y disponibilidad de proveedores**: Cuba está bajo embargo integral de EE. UU. (fuentes secundarias, [anexo B](anexos/B-fuentes-y-verificaciones.md#b3-sanciones--disponibilidad-contexto-no-asesoría-legal)); los términos de Keygen Cloud se rigen por ley de Texas, piden datos de la empresa y de sus beneficiarios finales y permiten cancelar una cuenta preliminar en cualquier momento (1.1.1); también pueden afectar a nube, repositorios y medios de pago. No es asesoría legal. | M‑A | A | Dictamen legal **antes** de contratar; puerto `LicenseProvider` con **Keygen CE autoalojado** y proveedor propio como alternativas; el cliente nunca depende de Keygen en tiempo real (archivos offline); hosting y pagos elegidos tras el dictamen. | Cuenta en revisión/rechazada; bloqueo de acceso; cambio de términos. |
| <a id="r-02"></a>**R‑02** | **Términos de elTOQUE**: licencia revocable, no transferible ni sublicenciable; una clave por aplicación; no revender datos; uso solo para que el usuario los vea en la aplicación; no modificar; pueden cobrar o suspender; esquema de respuesta **no documentado** y zona horaria desconocida; CAD/MXN/ZELLE/CLA posiblemente no disponibles. | A | A | Confirmación escrita ([D‑04](16-decisiones-pendientes.md#d-04)); proveedor intercambiable; tasa manual etiquetada como alternativa; analizador estricto con alerta de *drift*; sin raspado. | Respuesta 401/403; cambio de esquema; correo de elTOQUE. |
| <a id="r-03"></a>**R‑03** | **Normativa cubana desconocida**: fichas o IPV no conformes con la norma real (campos, firmas, vigencias, fórmulas). | M | A | Nada legal codificado; reglas configurables y plantillas **no normativas**; revisión por el contador/jurista del cliente; avisos en la UI ([D‑01](16-decisiones-pendientes.md#d-01), [D‑02](16-decisiones-pendientes.md#d-02)). | Observaciones de auditores o clientes. |
| <a id="r-04"></a>**R‑04** | **Sin evidencia de mercado** para offline/multi‑sucursal (G‑0 del ADR). | M | M | Despliegue escalonado, métricas de uso en piloto, revisión del ADR tras 6a. | Poco uso offline medido; solicitudes de otras funciones. |
| <a id="r-05"></a>**R‑05** | **Integridad de datos en sincronización** (duplicados, pérdidas, conflictos mal resueltos). | M | A | Libro mayor inmutable, mutaciones idempotentes, conflictos explícitos, suite E‑1…E‑5 como *gate*, caos. | Conflictos o rechazos inesperados; discrepancias de saldos. |
| <a id="r-06"></a>**R‑06** | **Evasión de licencia en el cliente** (APK modificado, reloj, copia del archivo). | M | M | Firma asimétrica, reloj fiable, huella por dispositivo, aplicación autoritativa en servidor, lo valioso en el servidor; riesgo residual aceptado en uso offline ([doc 7](07-flujo-licencias-keygen.md#78-lo-que-no-puede-impedirse-realismo)). | Activaciones anómalas; dispositivos duplicados. |
| <a id="r-07"></a>**R‑07** | **Errores monetarios** (redondeo, `Double`, divergencia entre servidor y Android). | M | A | `NUMERIC`/`BigDecimal`, motor único `core:domain`, vectores dorados, pruebas de propiedades, política de redondeo escrita ([D‑25](16-decisiones-pendientes.md#d-25)). | Diferencias de centavos entre pantallas o exportaciones. |
| <a id="r-08"></a>**R‑08** | **Alcance excesivo** (dos productos + licencias + sync + tasas + comercial). | A | M‑A | Fases con *gates*, MVP acotado, módulos opcionales (Cuadre posterior), monolito modular. | Retrasos de fase; deuda acumulada. |
| <a id="r-09"></a>**R‑09** | **Dependencia de una persona** y calidad variable del código generado con asistentes. | M | M‑A | ADR, documentación, pruebas y CI obligatorios, revisión de código, estándares y *linters*. | Cobertura bajando; PR sin revisión. |
| <a id="r-10"></a>**R‑10** | **Fuga entre organizaciones** (multi‑tenant). | B‑M | A | RLS forzado, FK compuestas, autorización por objeto, pruebas de fuga en CI, revisión externa. | Fallo de las pruebas de aislamiento. |
| <a id="r-11"></a>**R‑11** | **Conectividad y costo de datos** (timeouts, abandono de la app). | A | M | Delta‑sync, gzip, solo Wi‑Fi por defecto, reintentos pacientes, modo ahorro, pruebas con enlaces lentos. | Altas tasas de `STUCK`; quejas por consumo. |
| <a id="r-12"></a>**R‑12**| **Tasa mostrada como oficial** o uso fuera de los términos. | M | A | Etiquetas obligatorias, política por empresa, pruebas de UI/exportaciones que verifican el texto, atribución. | Documentos sin etiqueta; consultas de clientes. |
| <a id="r-13"></a>**R‑13** | **Pérdida de claves** (copias, cifrado de campo, auditoría). | B | A | Custodia y *escrow*, procedimiento de rotación, simulacros de restauración, claves separadas por propósito. | Fallo de restauración en simulacro. |
| <a id="r-14"></a>**R‑14** | **Obsolescencia** (dependencias de 2023‑2024 en los repos origen; `security-crypto` obsoleta). | A | M | Actualizar a versiones estables vigentes al iniciar cada fase; Renovate; sin bibliotecas obsoletas. | Alertas de dependencias; avisos de *deprecation*. |
| <a id="r-15"></a>**R‑15** | **Carga operativa/costo** de Keygen CE (Rails + PostgreSQL + Redis) o de Keygen Cloud. | M | M | Spike S‑1 para medir; decisión [D‑05](16-decisiones-pendientes.md#d-05); plan B propio. | Consumo de memoria; coste de suscripción. |
| <a id="r-16"></a>**R‑16** | **Distribución del APK** (reempaquetado, canal no oficial, Play no disponible). | M | M | Firma protegida, manifiesto de actualización firmado, verificación de integridad blanda, guía de instalación ([D‑19](16-decisiones-pendientes.md#d-19)). | APK no oficiales circulando. |
| <a id="r-17"></a>**R‑17** | **Zona horaria y cambios de horario**; desfase del sello de elTOQUE. | M | M | UTC en servidor, fecha de negocio por zona de la empresa, `fetched_at` autoritativo, pruebas con `America/Havana`. | Fechas de negocio desplazadas un día. |
| <a id="r-18"></a>**R‑18** | **Ambigüedad del término IPV** y del orden de documentos → rediseño tardío. | M | M | Objetos separados (`ipv_values`/`ipv_controls`), política configurable, ADR‑0006 ([D‑01](16-decisiones-pendientes.md#d-01)). | Cambios de vocabulario en revisiones. |
| <a id="r-19"></a>**R‑19** | **Rendimiento de RLS y auditoría** con volumen. | B | M | Índices por `organization_id`, sellado asíncrono, particionado, pruebas de carga. | Latencias crecientes en consultas. |
| <a id="r-20"></a>**R‑20** | **Licencias de terceros** (Fair Core License de Keygen CE, SQLCipher, jOOQ) y licencia propietaria del código. | B‑M | M | Inventario de licencias en el SBOM; revisión de compatibilidad ([D‑24](16-decisiones-pendientes.md#d-24)). | Hallazgos del escáner de licencias. |

## 14.2 Mapa de calor

| | **Impacto M** | **Impacto A** |
|---|---|---|
| **P alta** | R‑08 · R‑11 · R‑14 | R‑02 |
| **P media** | R‑04 · R‑06 · R‑09 · R‑15 · R‑16 · R‑17 · R‑18 | R‑01 · R‑03 · R‑05 · R‑07 · R‑12 |
| **P baja** | R‑19 · R‑20 | R‑10 · R‑13 |

**Riesgos que condicionan el arranque de la Fase 2**: R‑01 (dictamen legal sobre proveedores), R‑02 (confirmación de elTOQUE), R‑03/R‑18 (vocabulario y normativa) y R‑08 (acotar el MVP).
