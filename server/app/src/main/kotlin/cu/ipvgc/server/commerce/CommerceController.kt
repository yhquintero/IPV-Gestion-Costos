package cu.ipvgc.server.commerce

import cu.ipvgc.domain.license.CommercePricing
import cu.ipvgc.server.audit.AuditService
import cu.ipvgc.server.licensing.LicenseController
import cu.ipvgc.server.security.currentUser
import cu.ipvgc.server.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ContractBody(val number: String, val type: String = "LICENSE")
data class ContractItemBody(val price_item_id: UUID)
data class PaymentBody(
    val contract_id: UUID,
    val amount: BigDecimal,
    val currency: String = "USD",
    val method: String = "TRANSFER",
)
data class ConfirmBody(val license_id: UUID? = null)

@RestController
@RequestMapping("/api/v1")
class CommerceController(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
    private val licenses: LicenseController,
) {
    @GetMapping("/price-catalog")
    fun catalog(): List<Map<String, Any?>> =
        jdbc.query(
            """
            SELECT id, policy_code, kind, duration_days, price_usd, active, valid_from, valid_to
              FROM price_catalog_items
             WHERE active
             ORDER BY duration_days NULLS LAST, policy_code
            """.trimIndent(),
        ) { rs, _ ->
            mapOf(
                "id" to rs.getObject("id"),
                "policy_code" to rs.getString("policy_code"),
                "kind" to rs.getString("kind"),
                "duration_days" to rs.getObject("duration_days"),
                "price_usd" to rs.getBigDecimal("price_usd"),
                "valid_from" to rs.getDate("valid_from")?.toLocalDate(),
                "valid_to" to rs.getDate("valid_to")?.toLocalDate(),
            )
        }

    @GetMapping("/contracts")
    fun contracts(): List<Map<String, Any?>> {
        val user = currentUser()
        requireAdmin(user.roles)
        return jdbc.query(
            """
            SELECT id, number, type, status, total_usd, created_at
              FROM contracts
             WHERE organization_id = ? AND deleted_at IS NULL
             ORDER BY created_at DESC
            """.trimIndent(),
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id"),
                    "number" to rs.getString("number"),
                    "type" to rs.getString("type"),
                    "status" to rs.getString("status"),
                    "total_usd" to rs.getBigDecimal("total_usd"),
                    "created_at" to rs.getTimestamp("created_at").toInstant(),
                )
            },
            user.organizationId,
        )
    }

    @PostMapping("/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createContract(@RequestBody body: ContractBody): Map<String, Any?> {
        val user = currentUser()
        requireAdmin(user.roles)
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO contracts (id, organization_id, number, type, status, total_usd)
            VALUES (?, ?, ?, ?, 'DRAFT', 0)
            """.trimIndent(),
            id, user.organizationId, body.number, body.type,
        )
        audit.record("CONTRACT.CREATED", "contracts", id, after = body)
        return mapOf("id" to id, "number" to body.number, "status" to "DRAFT")
    }

    @PostMapping("/contracts/{id}/items")
    @Transactional
    fun addItem(
        @PathVariable id: UUID,
        @RequestBody body: ContractItemBody,
    ): Map<String, Any?> {
        val user = currentUser()
        requireAdmin(user.roles)
        val price =
            jdbc.query(
                "SELECT policy_code, price_usd FROM price_catalog_items WHERE id = ? AND active",
                { rs, _ -> rs.getString(1) to rs.getBigDecimal(2) },
                body.price_item_id,
            ).firstOrNull() ?: throw ApiException.notFound("price_catalog_item")
        val usdRate =
            jdbc.query(
                """
                SELECT s.value, s.id
                  FROM exchange_rate_current c
                  JOIN exchange_rate_samples s ON s.id = c.sample_id
                 WHERE c.instrument_code = 'USD'
                """.trimIndent(),
            ) { rs, _ -> rs.getBigDecimal(1) to (rs.getObject(2) as UUID) }
                .firstOrNull() ?: throw ApiException.unprocessable("rate_unavailable", "USD rate missing")
        val cup = CommercePricing.freezeCup(price.second, usdRate.first)
        val itemId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO contract_items
                (id, organization_id, contract_id, price_item_id, price_usd,
                 cup_reference_value, exchange_rate_used, pricing_date, rate_sample_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            itemId, user.organizationId, id, body.price_item_id, price.second,
            cup.amount, usdRate.first, LocalDate.now(), usdRate.second,
        )
        jdbc.update(
            "UPDATE contracts SET total_usd = total_usd + ?, version = version + 1, updated_at = clock_timestamp() WHERE id = ?",
            price.second, id,
        )
        return mapOf(
            "id" to itemId,
            "policy_code" to price.first,
            "price_usd" to price.second,
            "cup_reference_value" to cup.amount,
            "exchange_rate_used" to usdRate.first,
            "pricing_date" to LocalDate.now(),
            "rate_sample_id" to usdRate.second,
        )
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun pay(@RequestBody body: PaymentBody): Map<String, Any?> {
        val user = currentUser()
        requireAdmin(user.roles)
        if (body.amount < BigDecimal.ZERO) throw ApiException.badRequest("invalid_amount", "I-11")
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO payments (id, organization_id, contract_id, amount, currency, method, status)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
            """.trimIndent(),
            id, user.organizationId, body.contract_id, body.amount, body.currency, body.method,
        )
        return mapOf("id" to id, "status" to "PENDING")
    }

    @PostMapping("/payments/{id}/confirm")
    @Transactional
    fun confirm(
        @PathVariable id: UUID,
        @RequestBody(required = false) body: ConfirmBody?,
        @RequestHeader("Idempotency-Key", required = false) idem: String?,
    ): Map<String, Any?> {
        val user = currentUser()
        requireAdmin(user.roles)
        val updated =
            jdbc.update(
                """
                UPDATE payments
                   SET status = 'CONFIRMED', paid_at = clock_timestamp()
                 WHERE id = ? AND organization_id = ? AND status = 'PENDING'
                """.trimIndent(),
                id, user.organizationId,
            )
        if (updated == 0) {
            val status =
                jdbc.query(
                    "SELECT status FROM payments WHERE id = ? AND organization_id = ?",
                    { rs, _ -> rs.getString(1) },
                    id, user.organizationId,
                ).firstOrNull() ?: throw ApiException.notFound("payment")
            if (status != "CONFIRMED") throw ApiException.conflict("payment_state", status)
        }
        val receiptNo = "REC-${id.toString().take(8)}"
        val receiptId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO receipts (id, organization_id, payment_id, number)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (organization_id, number) DO NOTHING
            """.trimIndent(),
            receiptId, user.organizationId, id, receiptNo,
        )
        val renewal =
            if (body?.license_id != null) {
                licenses.applyRenewal(user, body.license_id, idem ?: "pay-$id")
            } else {
                null
            }
        audit.record("PAYMENT.CONFIRMED", "payments", id, after = mapOf("receipt" to receiptNo))
        return mapOf(
            "payment_id" to id,
            "status" to "CONFIRMED",
            "receipt_number" to receiptNo,
            "renewal" to renewal,
        )
    }

    @GetMapping("/receipts")
    fun receipts(): List<Map<String, Any?>> {
        val user = currentUser()
        requireAdmin(user.roles)
        return jdbc.query(
            """
            SELECT r.id, r.number, r.payment_id, p.amount, p.currency
              FROM receipts r
              JOIN payments p ON p.id = r.payment_id
             WHERE r.organization_id = ?
             ORDER BY r.number
            """.trimIndent(),
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id"),
                    "number" to rs.getString("number"),
                    "payment_id" to rs.getObject("payment_id"),
                    "amount" to rs.getBigDecimal("amount"),
                    "currency" to rs.getString("currency"),
                )
            },
            user.organizationId,
        )
    }

    private fun requireAdmin(roles: Set<String>) {
        if ("PLATFORM_ADMIN" !in roles && "ORG_ADMIN" !in roles) {
            throw ApiException.forbidden("forbidden", "platform or org admin required")
        }
    }
}
