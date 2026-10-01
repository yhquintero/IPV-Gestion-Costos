# Restauración (E-5)

**RPO/RTO acordados: ⛔ D-22.** Este corte no midió un simulacro.

1. Restaurar dump en BD **de prueba** (`deploy/backup/restore.sh`).
2. `GET /api/v1/audit/verify` debe pasar.
3. Incrementar `sync_server_state.epoch` → clientes `RESYNC_REQUIRED`, outbox PENDING intacta.
4. Medir minutos hasta API sana y primer sync de un dispositivo de prueba = RTO observado.
5. Registrar fecha, operador, dump usado, resultado. No marcar G-4 hasta que el propietario acepte umbrales.
