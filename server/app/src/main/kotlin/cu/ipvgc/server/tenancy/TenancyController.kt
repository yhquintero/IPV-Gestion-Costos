package cu.ipvgc.server.tenancy

import cu.ipvgc.server.security.currentUser
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class TenancyController(private val jdbc: JdbcTemplate) {
    @GetMapping("/companies")
    fun companies(): List<Map<String, Any?>> = jdbc.query(
        "SELECT id, name, tax_id, base_currency, timezone FROM companies WHERE deleted_at IS NULL ORDER BY name",
    ) { rs, _ ->
        mapOf(
            "id" to rs.getObject("id", UUID::class.java),
            "name" to rs.getString("name"),
            "tax_id" to rs.getString("tax_id"),
            "base_currency" to rs.getString("base_currency"),
            "timezone" to rs.getString("timezone"),
        )
    }

    @GetMapping("/branches")
    fun branches(): List<Map<String, Any?>> = jdbc.query(
        "SELECT id, company_id, code, name, active FROM branches WHERE deleted_at IS NULL ORDER BY code",
    ) { rs, _ ->
        mapOf(
            "id" to rs.getObject("id", UUID::class.java),
            "company_id" to rs.getObject("company_id", UUID::class.java),
            "code" to rs.getString("code"),
            "name" to rs.getString("name"),
            "active" to rs.getBoolean("active"),
        )
    }.also { currentUser() }
}
