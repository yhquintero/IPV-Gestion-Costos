package cu.ipvgc.server.notifications

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import cu.ipvgc.server.security.currentUser
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Service
class NotificationService(private val jdbc: JdbcTemplate) {
    private val mapper = jacksonObjectMapper()

    fun notifyPlatformAdmins(type: String, payload: Map<String, Any?>) {
        jdbc.update(
            """
            INSERT INTO notifications (organization_id, user_id, type, payload)
            SELECT a.organization_id, a.user_id, ?, ?::jsonb
              FROM role_assignments a
              JOIN roles r ON r.id = a.role_id
             WHERE r.code = 'PLATFORM_ADMIN' AND a.deleted_at IS NULL
            """.trimIndent(),
            type,
            mapper.writeValueAsString(payload),
        )
    }

    fun notifyRole(roleCode: String, type: String, entityId: UUID) {
        val org = currentUser().organizationId
        jdbc.update(
            """
            INSERT INTO notifications (organization_id, user_id, type, payload)
            SELECT a.organization_id, a.user_id, ?, jsonb_build_object('entity_id', ?::text)
              FROM role_assignments a
              JOIN roles r ON r.id = a.role_id
             WHERE a.organization_id = ?
               AND r.code = ?
               AND a.deleted_at IS NULL
            """.trimIndent(),
            type, entityId, org, roleCode,
        )
    }

    fun mine(): List<Map<String, Any?>> {
        val u = currentUser()
        return jdbc.query(
            """
            SELECT id, type, payload, read_at, created_at
              FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 100
            """.trimIndent(),
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id"),
                    "type" to rs.getString("type"),
                    "payload" to rs.getString("payload"),
                    "read_at" to rs.getTimestamp("read_at")?.toInstant(),
                    "created_at" to rs.getTimestamp("created_at").toInstant(),
                )
            },
            u.userId,
        )
    }
}

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val notifications: NotificationService) {
    @GetMapping
    fun list(): List<Map<String, Any?>> = notifications.mine()
}
