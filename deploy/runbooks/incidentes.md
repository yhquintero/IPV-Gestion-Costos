# Incidente

1. **No borrar logs.** Conservar `request_id`.
2. Clasificar: secreto · cuenta · integridad · disponibilidad · fuga.
3. Secretos: rotar (`rotacion-secretos.md`), revocar sesiones (`POST /auth/logout` familia), gitleaks.
4. Cuenta: bloquear usuario, forzar MFA, auditar `AUTH.LOGIN.*`.
5. Auditoría: `GET /api/v1/audit/verify`. Si falla, congelar escrituras y restaurar.
6. Fuga entre orgs: desactivar API, conservar dump forense, avisar ⛔ marco legal D-02.
7. Post-mortem en 72 h. Dueño ⛔ D-22.
