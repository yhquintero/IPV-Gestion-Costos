# Fase 5 · Android (en línea + caché de lectura)

| | |
|---|---|
| **Estado** | En curso — multimódulo, Room+SQLCipher, evaluador de licencia |
| **Fecha** | 2026‑10‑01 |
| **Gate** | Pruebas unitarias; migración Room v1; MASVS L1 parcial; APK sin secretos; *build* reproducible |

## Entregables

| Pieza | Ubicación |
|---|---|
| App multimódulo Compose/Hilt | `android/` (build **aparte**, `includeBuild("../core")`) |
| `core:domain` (evaluador de licencia + vectores dorados) | `core/domain/.../license/`, `golden/license-states.json` |
| Red OkHttp/Retrofit, pines, `Idempotency-Key` | `android/core/network` |
| Room + SQLCipher, repositorios de **lectura** | `android/core/data` |
| Keystore/Tink, biometría, `TokenStore` | `android/core/security` |
| Features: auth, home, catalog, ipv, costing, control, inventory, rates, license, sync, settings | `android/feature/*` |
| WorkManager pull (Wi‑Fi) | `ReadPullWorker` — **sin outbox** |
| MASVS L1 | [05-masvs-l1.md](05-masvs-l1.md) |
| CI | `.github/workflows/ci-android.yml` |

## Qué hace / qué no

- **Sí**: login + MFA TOTP; catálogo, valores IPV, fichas, controles y tasas **en línea** con caché Room; evaluador de 7 estados; biometría para desbloqueo.
- **No (Fase 6)**: outbox, conflictos, conteos/movimientos offline, borradores offline.
- **No (Fase 7)**: proveedor Keygen, archivos de máquina reales (aquí hay vectores dorados del evaluador).
- **No (Fase 8)**: elTOQUE real.

## Cómo verificar

```bash
# Evaluador (JVM, mismo core que el servidor)
gradle :core:domain:test --tests cu.ipvgc.domain.license.*

cd android
./scripts/assert-no-secrets.sh
# JDK 17+ y Android SDK:
gradle testDebugUnitTest assembleDebug
gradle :core:data:connectedDebugAndroidTest   # migración Room v1
```

`applicationId` `cu.ipvgc.android`. URL pública: propiedad `ipv.apiBaseUrl` (default `https://api.invalid.example`). **Ningún** token de elTOQUE/Keygen entra en el APK.

## Versiones (inicio de fase)

AGP **8.13.2** · Kotlin **2.1.20** · Compose BOM **2026.09.00** · Room **2.7.2** · minSdk **26** · compile/targetSdk **36**.
