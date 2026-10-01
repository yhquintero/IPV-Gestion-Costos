# Compose del piloto

```bash
cp ../../.env.example .env   # completar contraseñas
# Por defecto APP_ENV=dev: prod rechaza SEED de tasas (ElToqueModeGuard).
docker compose --env-file .env up --build
```

Solo Caddy publica 80/443. `KEYGEN_MODE=FAKE`. `ELTOQUE_PROVIDER_MODE` en prod **no** puede ser SEED (`ElToqueModeGuard`).

Roles `app_rw`/`migrator` separados: este compose de piloto usa un solo usuario hasta el runbook de endurecimiento de BD.

Keygen CE **no** está en el archivo (D-05).
