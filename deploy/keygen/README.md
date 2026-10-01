# Aprovisionamiento Keygen (Fase 7)

`policies.yaml` declara producto, políticas y *entitlements*. El CLI real contra
Cloud/CE **no se ejecuta** mientras D-05 esté ⛔.

En este corte:

```bash
gradle :core:domain:test --tests cu.ipvgc.domain.license.*
```

`PolicyCatalog.provision(FakeLicenseProvider)` es idempotente. `KEYGEN_MODE=CLOUD`
arranca `KeygenCloudDisabledProvider` y cualquier llamada falla con D-05.

Renovación: `renewalBasis = FROM_EXPIRY` (mandato). No cambiar sin D-12.
I-18: ninguna clave de licencia se persiste.
