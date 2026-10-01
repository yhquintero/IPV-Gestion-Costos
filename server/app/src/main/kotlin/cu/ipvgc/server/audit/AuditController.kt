package cu.ipvgc.server.audit

import cu.ipvgc.server.security.currentUser
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/audit")
class AuditController(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {
    @GetMapping
    fun list(): List<Map<String, Any?>> {
        currentUser().require("ADVANCED_AUDIT")
        return jdbc.query(
            """
            SELECT id, seq, occurred_at, action, entity_type, entity_id, result, reason
              FROM audit_events ORDER BY seq DESC LIMIT 200
            """.trimIndent(),
        ) { rs, _ ->
            mapOf(
                "id" to rs.getObject("id", UUID::class.java),
                "seq" to rs.getLong("seq"),
                "occurred_at" to rs.getTimestamp("occurred_at").toInstant(),
                "action" to rs.getString("action"),
                "entity_type" to rs.getString("entity_type"),
                "entity_id" to rs.getObject("entity_id"),
                "result" to rs.getString("result"),
                "reason" to rs.getString("reason"),
            )
        }
    }

    @GetMapping("/verify")
    fun verify(): Map<String, Any?> {
        currentUser().require("ADVANCED_AUDIT")
        return audit.verify(currentUser().organizationId)
    }
}
