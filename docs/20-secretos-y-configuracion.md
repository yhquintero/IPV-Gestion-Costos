# 20. Secretos y variables de configuración

> Entregable §39‑20 · [Índice](README.md) · Relacionados: [seguridad](11-estrategia-seguridad.md#117-protección-de-datos) · [tasas](13-estrategia-actualizacion-tasas.md) · [licencias](07-flujo-licencias-keygen.md). Los valores de ejemplo son **marcadores**, nunca valores reales.

## 20.1 Reglas

1. **Ningún secreto en el repositorio**, en imágenes, en clientes (Android/JS) ni en registros. `.gitignore` excluye `.env*` salvo `.env.example`.
2. Toda variable secreta admite la forma **`<NOMBRE>_FILE`** (ruta a un archivo montado, p. ej. *Docker secrets*); si existen ambas, gana `_FILE`.
3. **Una clave por propósito** (JWT, firma de auditoría, cifrado de campo, copias); versionadas (`kid`/`key_version`) para rotar sin romper el pasado.
4. **Android solo recibe valores públicos** (URL, pines, identificadores y claves *públicas*); nunca tokens de proveedores.
5. El arranque **falla** si falta un secreto obligatorio o si hay configuración peligrosa para el perfil (p. ej. semillas de tasas de prueba en producción).
6. Inventario de secretos con responsable, fecha de creación y de rotación (⛔ [D‑22](16-decisiones-pendientes.md#d-22)).

## 20.2 Servidor (API)

**S** = secreto. **Req.** = obligatoria en producción.

### Aplicación y red

| Variable | S | Ejemplo / formato | Notas |
|---|---|---|---|
| `APP_ENV` | — | `dev` · `test` · `staging` · `prod` | Activa guardas por perfil |
| `APP_BASE_URL` | — | `https://app.ejemplo.tld` | URL pública del panel |
| `API_PUBLIC_URL` | — | `https://app.ejemplo.tld/api` | Para enlaces y *webhooks* |
| `ALLOWED_ORIGINS` | — | lista separada por comas | Solo para clientes `API_ACCESS`; el panel es mismo origen |
| `TRUSTED_PROXY_CIDRS` | — | `10.0.0.0/8` | De qué proxies se acepta `X‑Forwarded‑For` |
| `DEFAULT_TIMEZONE` | — | `America/Havana` | Fecha de negocio por defecto |
| `LOG_LEVEL` | — | `INFO` | Sin PII ni secretos |

### Base de datos

| Variable | S | Ejemplo | Notas |
|---|---|---|---|
| `DB_URL` | — | `jdbc:postgresql://db:5432/ipvgc` | TLS si no es red local |
| `DB_APP_USER` / `DB_APP_PASSWORD` | **S** | `app_rw` / `…` | Sin DDL ni `BYPASSRLS` |
| `DB_MIGRATOR_USER` / `DB_MIGRATOR_PASSWORD` | **S** | `migrator` / `…` | Solo durante migraciones |
| `DB_AUDIT_WRITER_USER` / `DB_AUDIT_WRITER_PASSWORD` | **S** | `audit_writer` / `…` | Solo `INSERT` en auditoría |
| `DB_REPORTING_USER` / `DB_REPORTING_PASSWORD` | **S** | `reporting_ro` / `…` | Solo lectura |

### Identidad y sesiones

| Variable | S | Ejemplo / formato | Notas |
|---|---|---|---|
| `JWT_SIGNING_KEY_PEM` (o `_FILE`) | **S** | PEM EdDSA/ES256 | **Req.**; privada |
| `JWT_KEY_ID` | — | `2026-10-a` | Rotación |
| `JWT_PREVIOUS_PUBLIC_KEYS` | — | lista `kid:PEM` | Verificar tokens vigentes tras rotar |
| `JWT_ACCESS_TTL_SECONDS` | — | `900` | 10‑15 min |
| `REFRESH_TOKEN_TTL_DAYS` | — | `30` | ⛔ valor |
| `REFRESH_TOKEN_HMAC_PEPPER` | **S** | 32+ bytes aleatorios | Para guardar *hash* de *refresh tokens* |
| `PASSWORD_PEPPER` | **S** (opcional) | 32+ bytes | Si se usa |
| `ARGON2_MEMORY_KIB` · `ARGON2_ITERATIONS` · `ARGON2_PARALLELISM` | — | según OWASP vigente | [doc 11](11-estrategia-seguridad.md#114-identidad-y-autenticación) |
| `FIELD_ENCRYPTION_KEYS` (o `_FILE`) | **S** | JSON `{kid: clave}` | Semillas TOTP y campos sensibles |
| `FIELD_ENCRYPTION_ACTIVE_KEY_ID` | — | `fe-1` | |
| `LOGIN_MAX_ATTEMPTS` · `LOGIN_LOCK_MINUTES` | — | `5` · `15` | Bloqueo progresivo |
| `SESSION_MAX_CONCURRENT` | — | `3` | ⛔ [D‑10](16-decisiones-pendientes.md#d-10) |
| `RATE_LIMIT_*` | — | por ruta | Login, sync, API |

### Auditoría

| Variable | S | Ejemplo | Notas |
|---|---|---|---|
| `AUDIT_SIGNING_KEY_PEM` (o `_FILE`) | **S** | PEM Ed25519/ECDSA | **Req.**; la pública se exporta a verificadores |
| `AUDIT_KEY_VERSION` | — | `1` | |
| `AUDIT_BLOCK_MAX_EVENTS` · `AUDIT_BLOCK_MAX_SECONDS` | — | `500` · `60` | Sellado |
| `AUDIT_ANCHOR_TARGET` | — | ruta/URL de almacenamiento externo | Anclaje diario |
| `AUDIT_ANCHOR_CREDENTIALS` | **S** | según destino | |
| `AUDIT_IP_MODE` | — | `FULL` · `TRUNCATED` · `HASHED` · `NONE` | ⛔ [D‑17](16-decisiones-pendientes.md#d-17) |
| `AUDIT_IP_HASH_SALT` | **S** | aleatorio | Si `HASHED` |

### elTOQUE (solo servidor)

| Variable | S | Ejemplo | Notas |
|---|---|---|---|
| **`ELTOQUE_API_KEY`** | **S** | token *bearer* | **Nunca** en Android/JS/repo/logs; una por aplicación |
| `ELTOQUE_API_URL` | — | `https://tasas.eltoque.com` | Lista blanca de destino |
| `ELTOQUE_PROVIDER_MODE` | — | `API` · `CACHED` · `MANUAL` | `SEED` solo en dev/test |
| `ELTOQUE_REFRESH_INTERVAL_SECONDS` | — | `300` | Mínimo 60 |
| `ELTOQUE_REFRESH_JITTER_PCT` | — | `10` | |
| `ELTOQUE_TIMEOUT_MS` | — | `10000` | |
| `ELTOQUE_FRESH_WINDOW_SECONDS` | — | `600` | Estados de la tasa |
| `ELTOQUE_MAX_REQ_PER_MINUTE` | — | `12` | Limitador propio; tope duro 60 |
| `ELTOQUE_ANOMALY_THRESHOLD_PCT` | — | ⛔ | Umbral de aviso |
| `RATES_SEED_ENABLED` | — | `false` en producción | El arranque falla si es `true` en `prod` |
| `RATES_DEFAULT_MAX_STALE_MINUTES` | — | ⛔ [D‑03](16-decisiones-pendientes.md#d-03) | Por empresa en `rate_policies` |

### Licencias (Keygen)

| Variable | S | Ejemplo | Notas |
|---|---|---|---|
| `KEYGEN_MODE` | — | `CLOUD` · `CE` · `INHOUSE` · `FAKE` | ⛔ [D‑05](16-decisiones-pendientes.md#d-05) |
| `KEYGEN_API_URL` | — | `https://api.keygen.sh` o URL de CE | Lista blanca |
| `KEYGEN_ACCOUNT_ID` · `KEYGEN_PRODUCT_ID` | — | UUID | |
| **`KEYGEN_PRODUCT_TOKEN`** (o `_FILE`) | **S** | token de producto | **Solo servidor** |
| `KEYGEN_ENVIRONMENT` | — | opcional | Solo si la edición lo soporta |
| `KEYGEN_VERIFY_KEY` | — (pública) | clave pública de la cuenta | **También se incrusta en las apps**; crítica para integridad |
| `KEYGEN_LICENSE_SCHEME` | — | `ECDSA_P256_SIGN` · `ED25519_SIGN` | Resultado de S‑1 |
| `KEYGEN_WEBHOOK_TOLERANCE_SECONDS` | — | `300` | Verificación de firma con la clave pública |
| `LICENSE_OFFLINE_GRACE_DAYS` | — | `14` | 7‑15; TTL del archivo de máquina |
| `LICENSE_EXPIRING_THRESHOLD_DAYS` | — | `7` | POR VENCER |
| `LICENSE_DEFAULT_MAX_DEVICES` | — | `2` | Configurable |
| `LICENSE_ACTIVATION_DEADLINE_DAYS` | — | ⛔ [D‑11](16-decisiones-pendientes.md#d-11) | |
| `LICENSE_CLOCK_TOLERANCE_SECONDS` | — | ⛔ | Reloj fiable |
| `LICENSE_REFRESH_AT_REMAINING_PCT` | — | `50` | Cuándo refrescar el archivo |

> **Keygen CE autoalojado** (si se elige): requiere sus propias variables (secretos de Rails, claves de cifrado, claves de firma de la cuenta, cadenas de conexión a su BD y Redis). Sus nombres exactos se toman de **su guía de autoalojamiento** y se verifican en S‑1; son **secretos críticos** (las claves de firma de la cuenta equivalen a la raíz de confianza de las licencias).

### Comercial, correo, copias y observabilidad

| Variable | S | Ejemplo | Notas |
|---|---|---|---|
| `COMMERCE_QUOTE_VALIDITY_DAYS` | — | ⛔ [D‑13](16-decisiones-pendientes.md#d-13) | |
| `COMMERCE_RECEIPT_PREFIX` | — | `REC` | Numeración definitiva en servidor |
| `SMTP_HOST` · `SMTP_PORT` · `SMTP_TLS_MODE` | — | | ⛔ [D‑20](16-decisiones-pendientes.md#d-20) |
| `SMTP_USER` · `SMTP_PASSWORD` | **S** | | |
| `MAIL_FROM_ADDRESS` · `MAIL_FROM_NAME` | — | | |
| `BACKUP_REPO_URL` | — | | pgBackRest/WAL‑G/restic |
| `BACKUP_ENCRYPTION_PASSPHRASE` (o `BACKUP_AGE_RECIPIENT`) | **S** | | **Perderla = perder las copias** ([R‑13](14-riesgos.md#r-13)) |
| `BACKUP_S3_ENDPOINT` · `BACKUP_S3_BUCKET` | — | | |
| `BACKUP_S3_ACCESS_KEY` · `BACKUP_S3_SECRET_KEY` | **S** | | Permiso de **solo escritura** si es posible |
| `BACKUP_RETENTION_*` · `WAL_ARCHIVE_*` | — | | ⛔ [D‑22](16-decisiones-pendientes.md#d-22) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | — | | Opcional |
| `ERROR_TRACKING_DSN` | **S** | | Opcional |
| `METRICS_BASIC_AUTH_USER` / `METRICS_BASIC_AUTH_PASSWORD` | **S** | | Protege `/metrics` |

## 20.3 Web (Next.js)

| Variable | S | Notas |
|---|---|---|
| `API_INTERNAL_URL` | — | Solo servidor (BFF → API por red interna) |
| `BFF_COOKIE_ENCRYPTION_KEY` | **S** | Cifra/firma la cookie de sesión del BFF; rotación versionada |
| `SESSION_COOKIE_NAME` | — | Con prefijo `__Host-` |
| `NEXT_PUBLIC_APP_NAME` · `NEXT_PUBLIC_SUPPORT_EMAIL` | — | **Todo `NEXT_PUBLIC_*` es público**: nunca secretos |
| `CSP_REPORT_URI` | — | Opcional |

## 20.4 Android (tiempo de compilación) — **sin secretos**

| Valor | Público | Notas |
|---|---|---|
| `API_BASE_URL` / `ipv.apiBaseUrl` | Sí | `https://…` (Gradle `BuildConfig`; default inválido) |
| `TLS_PINS` | Sí | SPKI SHA‑256 (≥ 2, con respaldo) + fecha de caducidad del conjunto |
| `KEYGEN_ACCOUNT_ID` · `KEYGEN_VERIFY_KEY` | Sí | **Constantes en el código** (recomendación de Keygen ✅); la compilación *release* **falla si están vacías** (práctica heredada de IPV) |
| `LICENSE_SCHEME` | Sí | Debe coincidir con el `alg` esperado |
| Firma de la app: `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | **Secretos de CI** | Nunca en el repositorio ni en `local.properties` versionado |

## 20.5 CI/CD e infraestructura

| Variable | S | Notas |
|---|---|---|
| `GITHUB_TOKEN` (automático) | — | Permisos mínimos por flujo |
| `GHCR_TOKEN` | **S** | Publicar imágenes |
| `DEPLOY_SSH_KEY` · `DEPLOY_HOST` | **S** · — | Despliegue |
| `COSIGN_KEY` (o firma sin clave) | **S** | Firma de imágenes |
| `RENOVATE_TOKEN` | **S** | Actualización de dependencias |
| `PLAY_SERVICE_ACCOUNT_JSON` | **S** | Solo si se publica en Play ([D‑19](16-decisiones-pendientes.md#d-19)) |
| `DOMAIN` · `ACME_EMAIL` · `ACME_CA` | — | Caddy/TLS |

## 20.5b Sitio web (BFF · Fase 4)

El navegador **no** recibe tokens JWT. El BFF guarda `ipv_access` / `ipv_refresh` HttpOnly y un `ipv_csrf` legible para el patrón de doble envío.

| Variable | S | Ejemplo | Notas |
|---|---|---|---|
| `API_URL` | — | `http://127.0.0.1:8080` | Vacío = almacén de demostración. **Obligatoria** si `APP_ENV=prod` |
| `MOCK_API` | — | `1` | Fuerza el almacén de demostración (nunca en prod) |
| `COOKIE_SECURE` | — | `true` | Cookies `Secure`; en prod se activa también por `APP_ENV` |
| `APP_BASE_URL` | — | `https://app.ejemplo.tld` | Enlaces absolutos (prueba, correos) |

## 20.6 Gestión

| Tema | Práctica 🧭 |
|---|---|
| Almacenamiento | Variables del entorno de despliegue o archivos montados con permisos 0400; opcionalmente SOPS+age o Vault |
| Desarrollo local | `.env.example` con **marcadores** (`CAMBIAR`) y generadores de claves de prueba; nunca claves reales |
| Rotación | Claves de JWT, auditoría y campo con `kid`/`key_version`; tokens de proveedores al menor sospecha; procedimiento escrito |
| Custodia | Copia de claves críticas fuera del servidor (sobre cerrado o gestor con doble control) |
| Redacción en logs | Nunca registrar: `Authorization`, cookies, contraseñas, *refresh tokens*, semillas TOTP, claves de licencia, `ELTOQUE_API_KEY`, `KEYGEN_PRODUCT_TOKEN`, cuerpos con PII |
| Detección | gitleaks en pre‑commit y CI; análisis del APK y del *bundle* web en busca de secretos |

## 20.7 Plantilla `.env.example` (marcadores)

```dotenv
APP_ENV=dev
APP_BASE_URL=https://localhost
DB_URL=jdbc:postgresql://localhost:5432/ipvgc
DB_APP_USER=app_rw
DB_APP_PASSWORD=CAMBIAR
JWT_SIGNING_KEY_PEM_FILE=./secrets/jwt-dev.pem      # clave de PRUEBA generada localmente
AUDIT_SIGNING_KEY_PEM_FILE=./secrets/audit-dev.pem
ELTOQUE_PROVIDER_MODE=SEED                          # solo dev/test
RATES_SEED_ENABLED=true                             # el arranque falla en prod
ELTOQUE_API_KEY=                                    # vacío: nunca se versiona
KEYGEN_MODE=FAKE
LICENSE_OFFLINE_GRACE_DAYS=14
LICENSE_DEFAULT_MAX_DEVICES=2
```
