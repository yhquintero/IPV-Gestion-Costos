# Rotación de secretos

| Secreto | Acción |
|---|---|
| JWT signing PEM | Nuevo `kid`; dejar el anterior verificar hasta TTL del access |
| `KEYGEN_WEBHOOK_SECRET` | Dual-accept un intervalo; actualizar endpoint |
| `ELTOQUE_API_KEY` | Una clave por aplicación; pedir rotación a elTOQUE |
| `DB_*_PASSWORD` | `ALTER ROLE … PASSWORD`; recargar API |
| Refresh pepper | Invalida todos los refresh; usuarios re-login |

Nunca commitear PEM. Preferir `*_FILE` montado 0400.
