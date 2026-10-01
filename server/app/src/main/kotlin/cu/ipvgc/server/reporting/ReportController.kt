package cu.ipvgc.server.reporting

import cu.ipvgc.server.security.currentUser
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reports")
class ReportController(private val jdbc: JdbcTemplate) {
    @GetMapping("/cost-sheets")
    fun costSheets(): List<Map<String, Any?>> {
        currentUser().require("costs:view")
        return jdbc.query(
            """
            SELECT s.code, p.name AS product, v.version_no, v.status, v.total_cost, v.unit_cost, v.valid_from
              FROM cost_sheets s
              JOIN products p ON p.id = s.product_id
              JOIN cost_sheet_versions v ON v.cost_sheet_id = s.id AND v.deleted_at IS NULL
             WHERE s.deleted_at IS NULL
             ORDER BY s.code, v.version_no
            """.trimIndent(),
        ) { rs, _ ->
            mapOf(
                "code" to rs.getString("code"),
                "product" to rs.getString("product"),
                "version_no" to rs.getInt("version_no"),
                "status" to rs.getString("status"),
                "total_cost" to rs.getBigDecimal("total_cost"),
                "unit_cost" to rs.getBigDecimal("unit_cost"),
                "valid_from" to rs.getDate("valid_from")?.toLocalDate(),
            )
        }
    }
}
