package cu.ipvgc.server.ipvvalues

import cu.ipvgc.server.audit.AuditService
import cu.ipvgc.server.security.currentUser
import cu.ipvgc.server.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class IpvValueBody(
    val company_id: UUID,
    val raw_material_id: UUID? = null,
    val product_id: UUID? = null,
    val currency: String,
    val unit_price: BigDecimal,
    val valid_from: LocalDate,
    val valid_to: LocalDate? = null,
    val source_ref: String? = null,
    val branch_id: UUID? = null,
)

@RestController
@RequestMapping("/api/v1/ipv-values")
class IpvValueController(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {
    @GetMapping
    fun list(): List<Map<String, Any?>> {
        currentUser().require("costs:view")
        return jdbc.query(
            """
            SELECT id, company_id, raw_material_id, product_id, currency, unit_price, valid_from, valid_to, source_ref
              FROM ipv_values WHERE deleted_at IS NULL ORDER BY valid_from DESC
            """.trimIndent(),
        ) { rs, _ ->
            mapOf(
                "id" to rs.getObject("id"),
                "company_id" to rs.getObject("company_id"),
                "raw_material_id" to rs.getObject("raw_material_id"),
                "product_id" to rs.getObject("product_id"),
                "currency" to rs.getString("currency"),
                "unit_price" to rs.getBigDecimal("unit_price"),
                "valid_from" to rs.getDate("valid_from").toLocalDate(),
                "valid_to" to rs.getDate("valid_to")?.toLocalDate(),
                "source_ref" to rs.getString("source_ref"),
            )
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: IpvValueBody): Map<String, Any?> {
        currentUser().require("catalog:edit")
        if ((body.raw_material_id == null) == (body.product_id == null)) {
            throw ApiException.badRequest("xor_subject", "exactly one of raw_material_id or product_id")
        }
        val id = UUID.randomUUID()
        try {
            jdbc.update(
                """
                INSERT INTO ipv_values (
                    id, organization_id, company_id, branch_id, raw_material_id, product_id,
                    currency, unit_price, valid_from, valid_to, source_ref, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id, currentUser().organizationId, body.company_id, body.branch_id,
                body.raw_material_id, body.product_id, body.currency, body.unit_price,
                body.valid_from, body.valid_to, body.source_ref, currentUser().userId,
            )
        } catch (ex: Exception) {
            throw ApiException.conflict("ipv_overlap", "IPV value overlaps an existing validity window")
        }
        audit.record("IPV_VALUE.CREATE", "ipv_values", id, after = body)
        return mapOf("id" to id)
    }
}
