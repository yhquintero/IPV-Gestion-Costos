package cu.ipvgc.server.access

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AccessService(private val jdbc: JdbcTemplate) {
    fun permissionsFor(userId: UUID): Pair<Set<String>, Set<String>> {
        val roles = mutableSetOf<String>()
        val perms = mutableSetOf<String>()
        jdbc.query(
            """
            SELECT r.code AS role_code, p.permission_code
              FROM role_assignments a
              JOIN roles r ON r.id = a.role_id
              LEFT JOIN role_permissions p ON p.role_id = r.id
             WHERE a.user_id = ? AND a.deleted_at IS NULL
               AND (a.valid_to IS NULL OR a.valid_to > clock_timestamp())
            """.trimIndent(),
            { rs, _ ->
                rs.getString("role_code")?.let { roles += it }
                rs.getString("permission_code")?.let { perms += it }
            },
            userId,
        )
        return roles to perms
    }
}
