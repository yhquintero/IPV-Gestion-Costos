# Generador de semilla sintética

Datos **100 % sintéticos** (D‑16). No importa credenciales ni datos de los repos origen.

- Tasas de prueba: `src/main/resources/seed/exchange-rates-test.json` (doc 13.11). **No** van como constantes en Kotlin.
- Grafo de organizaciones, catálogo, fichas y controles: `src/main/resources/seed/synthetic.sql`.

```bash
gradle :tools:seed:run --args='jdbc:postgresql://localhost:5432/ipvgc ipvgc secret'
```

El perfil de producción se niega a arrancar si `exchange_rate_samples.is_test = true`.
