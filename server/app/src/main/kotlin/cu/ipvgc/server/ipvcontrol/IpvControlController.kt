package cu.ipvgc.server.ipvcontrol

import cu.ipvgc.domain.ipvcontrol.IpvControlMode
import cu.ipvgc.server.audit.AuditService
import cu.ipvgc.server.security.currentUser
import cu.ipvgc.server.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CreateControlRequest(
    val company_id: UUID,
    val branch_id: UUID,
    val mode: String = IpvControlMode.CONSISTENCIA.name,
    val period_start: LocalDate,
    val period_end: LocalDate,
)

data class ControlLineRequest(
    val product_id: UUID,
    val cost_sheet_version_id: UUID,
    val expected_qty: BigDecimal,
    val expected_unit_cost: BigDecimal,
    val observed_qty: BigDecimal? = null,
    val observed_unit_cost: BigDecimal? = null,
    val observation_source: String? = "MANUAL",
)

data class CloseControlRequest(val with_differences: Boolean = false)

@RestController
@RequestMapping("/api/v1/ipv-controls")
class IpvControlController(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {
    @GetMapping
    fun list(): List<Map<String, Any?>> {
        currentUser().require("ipvcontrol:capture")
        return jdbc.query(
            "SELECT id, control_no, mode, period_start, period_end, status FROM ipv_controls WHERE deleted_at IS NULL ORDER BY created_at DESC",
        ) { rs, _ ->
            mapOf(
                "id" to rs.getObject("id"),
                "control_no" to rs.getString("control_no"),
                "mode" to rs.getString("mode"),
                "period_start" to rs.getDate("period_start").toLocalDate(),
                "period_end" to rs.getDate("period_end").toLocalDate(),
                "status" to rs.getString("status"),
            )
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun create(@RequestBody body: CreateControlRequest): Map<String, Any?> {
        currentUser().require("ipvcontrol:capture")
        val year = body.period_start.year
        val n = jdbc.queryForObject(
            "SELECT app.next_document_number(?, ?, 'IPV', ?)",
            Long::class.java,
            currentUser().organizationId, body.company_id, year,
        )!!
        val controlNo = "IPV-%d-%04d".format(year, n)
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO ipv_controls (
                id, organization_id, company_id, branch_id, control_no, mode,
                period_start, period_end, status, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?)
            """.trimIndent(),
            id, currentUser().organizationId, body.company_id, body.branch_id,
            controlNo, body.mode, body.period_start, body.period_end, currentUser().userId,
        )
        audit.record("IPV_CONTROL.CREATE", "ipv_controls", id)
        return mapOf("id" to id, "control_no" to controlNo, "status" to "PENDIENTE")
    }

    @PostMapping("/{id}/lines")
    @Transactional
    fun addLine(@PathVariable id: UUID, @RequestBody body: ControlLineRequest): Map<String, Any?> {
        currentUser().require("ipvcontrol:capture")
        val lineId = UUID.randomUUID()
        val expectedValue = body.expected_qty * body.expected_unit_cost
        val observedValue = if (body.observed_qty != null && body.observed_unit_cost != null) {
            body.observed_qty * body.observed_unit_cost
        } else {
            null
        }
        val variance = observedValue?.minus(expectedValue)
        jdbc.update(
            """
            INSERT INTO ipv_control_lines (
                id, organization_id, control_id, product_id, cost_sheet_version_id,
                expected_qty, expected_unit_cost, observed_qty, observed_unit_cost,
                observation_source, variance_value
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            lineId, currentUser().organizationId, id, body.product_id, body.cost_sheet_version_id,
            body.expected_qty, body.expected_unit_cost, body.observed_qty, body.observed_unit_cost,
            body.observation_source, variance,
        )
        jdbc.update("UPDATE ipv_controls SET status = 'EN_PROCESO' WHERE id = ? AND status = 'PENDIENTE'", id)
        audit.record("IPV_CONTROL.LINE.ADD", "ipv_control_lines", lineId)
        return mapOf("id" to lineId)
    }

    @PostMapping("/{id}/close")
    @Transactional
    fun close(@PathVariable id: UUID, @RequestBody(required = false) body: CloseControlRequest?): Map<String, Any?> {
        currentUser().require("ipvcontrol:close")
        val diffs = body?.with_differences == true || hasDifferences(id)
        val status = if (diffs) "CON_DIFERENCIAS" else "VALIDADO"
        val updated = jdbc.update(
            """
            UPDATE ipv_controls SET status = ?, closed_at = clock_timestamp()
             WHERE id = ? AND status = 'EN_PROCESO'
            """.trimIndent(),
            status, id,
        )
        if (updated != 1) throw ApiException.conflict("illegal_transition", "control is not EN_PROCESO")
        audit.record("IPV_CONTROL.CLOSE", "ipv_controls", id)
        return mapOf("id" to id, "status" to status)
    }

    private fun hasDifferences(id: UUID): Boolean {
        val n = jdbc.queryForObject(
            "SELECT count(*) FROM ipv_control_lines WHERE control_id = ? AND COALESCE(variance_value, 0) <> 0",
            Long::class.java,
            id,
        ) ?: 0
        return n > 0
    }
}
