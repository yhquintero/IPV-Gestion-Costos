# mock-eltoque

Servidor **simulado** de `GET /v1/trmi`. La CI y las pruebas de contrato **nunca** llaman a
`tasas.eltoque.com` (doc 13.11).

Los *fixtures* de `fixtures/` alimentan `SimulatedElToqueHttp` /
`ElToqueParser` en `core:domain`.

```bash
gradle :core:domain:test --tests cu.ipvgc.domain.rates.*
```

`ELTOQUE_PROVIDER_MODE=MOCK` usa el fixture comunitario. `API` exige
`ELTOQUE_API_KEY` y sigue ⛔ D-04.
