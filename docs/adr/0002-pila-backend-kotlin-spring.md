# ADR‑0002 · Pila del backend: Kotlin + Spring Boot

| Campo | Valor |
|---|---|
| **Estado** | **Aceptado (provisional al iniciar Fase 2)** — el propietario puede reemplazarlo |
| **Fecha** | 2026‑10‑01 |
| **Decide** | Defaults del análisis ([D‑31](../16-decisiones-pendientes.md#d-31)); spike S‑5 sigue siendo válido para confirmar |
| **Relacionados** | [Doc 15](../15-trade-offs.md) · [Doc 17](../17-stack-recomendado.md) · [Doc 18](../18-estructura-repo.md) |

## Contexto

Hay que fijar la pila del servidor para el esqueleto Gradle de la Fase 2. Las alternativas (Ktor, FastAPI, NestJS) duplicarían el motor de dominio o no compartirían bytecode con Android.

## Decisión

1. Backend **Kotlin + Spring Boot** (monolito modular) sobre **JDK 21 LTS**.
2. Persistencia **jOOQ + Flyway** (jOOQ se genera en Fase 3; esta fase deja las migraciones).
3. `core:domain` es una biblioteca JVM **sin framework**, compartida con Android vía `includeBuild` cuando exista el build Android.

## Alternativas

- Ktor: menos ensamblaje de seguridad/observabilidad listo.
- FastAPI / NestJS: segundo motor de dinero/reglas, divergencia inevitable.

## Consecuencias

- El servidor se construye sin Android SDK.
- Versiones fijadas en `gradle/libs.versions.toml` al iniciar la fase.

## Verificación

`./gradlew test` compila `core:domain` y `server:app` sobre JDK 21.
