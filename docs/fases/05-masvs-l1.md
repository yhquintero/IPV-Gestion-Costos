# Fase 5 · MASVS L1 (parcial)

Lista de control **MASVS 2.0** nivel L1 aplicada al esqueleto Android. No es certificación. Fase 9 **no la cierra**: falta instrumentación en dispositivo y pentest móvil.

| Control | Estado | Evidencia |
|---|---|---|
| STORAGE: datos sensibles cifrados en reposo | Parcial | Room + SQLCipher; clave 256 bit envuelta (Keystore/Tink). `allowBackup=false` |
| STORAGE: sin backups de la BD | Sí | `allowBackup=false`, `data_extraction_rules` excluyen db/prefs |
| CRYPTO: sin criptografía propia | Sí | Tink + SQLCipher + JCA |
| CRYPTO: no `EncryptedSharedPreferences` | Sí | [doc 17](../17-stack-recomendado.md) |
| AUTH: tokens cortos, refresh opaco | Parcial | `TokenStore`; rotación en API Fase 3 |
| AUTH: biometría para desbloqueo local | Parcial | `BiometricGate` + `USE_BIOMETRIC`; UI en Ajustes |
| NETWORK: TLS, sin cleartext en release | Sí | `usesCleartextTraffic=false`; pines en `PublicConfig` |
| NETWORK: no confiar CA de usuario en release | Sí | `network_security_config` solo `system` |
| PLATFORM: mínimo privilegio | Sí | INTERNET + USE_BIOMETRIC |
| CODE: sin secretos en el APK | Sí | `BuildConfig` solo URL pública; `scripts/assert-no-secrets.sh` |
| CODE: ofuscación release | Sí | R8 `isMinifyEnabled` |
| RESILIENCE: estado de licencia recalculado | Sí | `LicenseEvaluator` (no bandera premium) |
| PRIVACY: identificador de instalación, no IMEI | Documentado | [doc 7](../07-flujo-licencias-keygen.md) · Fase 7 |

Instrumentación y análisis del APK (strings, `gitleaks`) se ejecutan en CI con SDK.
