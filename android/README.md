# Android · IPV Gestión de Costos

Build **independiente** (no forma parte del Gradle raíz). Incorpora `core:domain` con `includeBuild("../core")`.

Ver [docs/fases/05-android.md](../docs/fases/05-android.md).

```bash
# SDK en local.properties (gitignored)
gradle testDebugUnitTest assembleDebug
./scripts/assert-no-secrets.sh
```

Fase 5 = en línea + caché de lectura. Offline editable = Fase 6.
