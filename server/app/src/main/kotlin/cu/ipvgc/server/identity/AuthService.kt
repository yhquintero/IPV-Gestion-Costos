package cu.ipvgc.server.identity

import cu.ipvgc.domain.crypto.Totp
import cu.ipvgc.server.access.AccessService
import cu.ipvgc.server.audit.AuditService
import cu.ipvgc.server.security.JwtService
import cu.ipvgc.server.web.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.PreparedStatement
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String = "Bearer",
    val expires_in: Long,
    val user: Map<String, Any?>,
)

data class LoginRequest(val email: String, val password: String, val organization_id: UUID? = null)
data class RefreshRequest(val refresh_token: String)
data class MfaRequest(val mfa_token: String, val code: String)

@Service
class AuthService(
    private val jdbc: JdbcTemplate,
    private val encoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val accessService: AccessService,
    private val audit: AuditService,
    tm: PlatformTransactionManager,
    @Value("\${ipvgc.refresh.pepper:dev-refresh-pepper}") private val pepper: String,
) {
    private val tx = TransactionTemplate(tm)
    private val rng = SecureRandom()

    fun login(req: LoginRequest): Any {
        val email = req.email.trim().lowercase()
        val loginMapper =
            RowMapper { rs, _ ->
                LoginRow(
                    id = rs.getObject("id", UUID::class.java),
                    organizationId = rs.getObject("organization_id", UUID::class.java),
                    passwordHash = rs.getString("password_hash"),
                    status = rs.getString("status"),
                    mfa = rs.getBoolean("mfa_enabled"),
                    displayName = rs.getString("display_name"),
                    lockedUntil = rs.getTimestamp("locked_until")?.toInstant(),
                )
            }
        val rows: List<LoginRow> = jdbc.query(
            "SELECT * FROM app.lookup_user_for_login(?)",
            arrayOf<Any>(email),
            loginMapper,
        )
        val row = when {
            req.organization_id != null -> rows.firstOrNull { it.organizationId == req.organization_id }
            rows.size == 1 -> rows.first()
            rows.isEmpty() -> null
            else -> throw ApiException.badRequest("ambiguous_organization", "organization_id is required")
        }
        if (row == null || !encoder.matches(req.password, row.passwordHash)) {
            row?.let { failed ->
                call("SELECT app.register_login_failure(?)", failed.id)
                tx.execute { _ ->
                    withOrg(failed.organizationId)
                    audit.record(
                        action = "AUTH.LOGIN.FAILED",
                        result = "DENIED",
                        organizationId = failed.organizationId,
                    )
                }
            }
            throw ApiException.unauthorized()
        }
        if (row.status != "ACTIVE" || (row.lockedUntil != null && row.lockedUntil.isAfter(Instant.now()))) {
            throw ApiException.forbidden("account_locked", "account is locked")
        }
        if (row.mfa) {
            val mfaToken = jwtService.issueAccess(row.id, row.organizationId, email)
            return mapOf("mfa_required" to true, "mfa_token" to mfaToken)
        }
        return issueSession(row.id, row.organizationId, email, row.displayName)
    }

    fun completeMfa(req: MfaRequest): TokenResponse {
        val claims = jwtService.parse(req.mfa_token)
        return tx.execute {
            withOrg(claims.organizationId)
            val secret = jdbc.query(
                "SELECT secret_enc FROM mfa_factors WHERE user_id = ? AND type = 'TOTP' AND deleted_at IS NULL LIMIT 1",
                { rs, _ -> rs.getBytes(1) },
                claims.userId,
            ).firstOrNull() ?: throw ApiException.unauthorized()
            if (!Totp.matches(secret, req.code, Instant.now().epochSecond)) {
                throw ApiException.unauthorized()
            }
            val display = jdbc.queryForObject(
                "SELECT display_name FROM users WHERE id = ?",
                String::class.java,
                claims.userId,
            ) ?: ""
            issueSession(claims.userId, claims.organizationId, claims.email, display)
        }!!
    }

    fun refresh(req: RefreshRequest): TokenResponse {
        val hash = hashRefresh(req.refresh_token)
        return tx.execute {
            val row = jdbc.query(
                """
                SELECT s.id, s.user_id, s.organization_id, s.revoked_at, s.expires_at, u.email, u.display_name
                  FROM sessions s JOIN users u ON u.id = s.user_id
                 WHERE s.refresh_hash = ?
                """.trimIndent(),
                { rs, _ ->
                    SessionRow(
                        id = rs.getObject("id", UUID::class.java),
                        userId = rs.getObject("user_id", UUID::class.java),
                        organizationId = rs.getObject("organization_id", UUID::class.java),
                        revoked = rs.getTimestamp("revoked_at") != null,
                        expires = rs.getTimestamp("expires_at").toInstant(),
                        email = rs.getString("email"),
                        displayName = rs.getString("display_name"),
                    )
                },
                hash,
            ).firstOrNull() ?: throw ApiException.unauthorized()
            withOrg(row.organizationId)
            if (row.revoked || row.expires.isBefore(Instant.now())) {
                jdbc.update("UPDATE sessions SET revoked_at = clock_timestamp() WHERE family_id IN (SELECT family_id FROM sessions WHERE id = ?)", row.id)
                throw ApiException.unauthorized()
            }
            jdbc.update("UPDATE sessions SET revoked_at = clock_timestamp() WHERE id = ?", row.id)
            issueSession(row.userId, row.organizationId, row.email, row.displayName)
        }!!
    }

    fun logout(refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) return
        val hash = hashRefresh(refreshToken)
        jdbc.update("UPDATE sessions SET revoked_at = clock_timestamp() WHERE refresh_hash = ?", hash)
    }

    private fun issueSession(userId: UUID, orgId: UUID, email: String, displayName: String): TokenResponse {
        return tx.execute {
            withOrg(orgId)
            call("SELECT app.register_login_success(?)", userId)
            val refresh = randomToken()
            val family = UUID.randomUUID()
            jdbc.update(
                """
                INSERT INTO sessions (organization_id, user_id, refresh_hash, family_id, expires_at)
                VALUES (?, ?, ?, ?, clock_timestamp() + interval '30 days')
                """.trimIndent(),
                orgId, userId, hashRefresh(refresh), family,
            )
            val (roles, perms) = accessService.permissionsFor(userId)
            audit.record(
                action = "AUTH.LOGIN.SUCCESS",
                entityType = "users",
                entityId = userId,
                organizationId = orgId,
                user = cu.ipvgc.server.access.CurrentUser(userId, orgId, email, displayName, roles, perms),
            )
            TokenResponse(
                access_token = jwtService.issueAccess(userId, orgId, email),
                refresh_token = refresh,
                expires_in = jwtService.accessTtlSeconds,
                user = mapOf(
                    "id" to userId,
                    "organization_id" to orgId,
                    "email" to email,
                    "display_name" to displayName,
                    "roles" to roles,
                    "permissions" to perms,
                ),
            )
        }!!
    }

    private fun call(sql: String, id: UUID) {
        jdbc.execute(sql) { ps: PreparedStatement ->
            ps.setObject(1, id)
            ps.execute()
        }
    }

    private fun withOrg(orgId: UUID) {
        jdbc.execute("SELECT set_config('app.organization_id', ?, true)") { ps: PreparedStatement ->
            ps.setString(1, orgId.toString())
            ps.execute()
        }
    }

    private fun hashRefresh(token: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return HexFormat.of().formatHex(md.digest((pepper + token).toByteArray()))
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        rng.nextBytes(bytes)
        return HexFormat.of().formatHex(bytes)
    }

    private data class LoginRow(
        val id: UUID,
        val organizationId: UUID,
        val passwordHash: String,
        val status: String,
        val mfa: Boolean,
        val displayName: String,
        val lockedUntil: Instant?,
    )

    private data class SessionRow(
        val id: UUID,
        val userId: UUID,
        val organizationId: UUID,
        val revoked: Boolean,
        val expires: Instant,
        val email: String,
        val displayName: String,
    )
}
