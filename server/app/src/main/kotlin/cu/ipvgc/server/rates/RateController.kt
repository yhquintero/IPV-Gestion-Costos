package cu.ipvgc.server.rates

import cu.ipvgc.server.audit.AuditService
import cu.ipvgc.server.security.currentUser
import cu.ipvgc.server.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

data class ManualRateRequest(
    val instrument_code: String,
    val value: BigDecimal,
    val reason: String,
)

@RestController
@RequestMapping("/api/v1/rates")
class RateController(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {
    @GetMapping("/current")
    fun current(): List<Map<String, Any?>> = jdbc.query(
        """
        SELECT c.instrument_code, s.value, c.status, s.source, s.is_test, s.fetched_at, s.id AS sample_id
          FROM exchange_rate_current c
          JOIN exchange_rate_samples s ON s.id = c.sample_id
         ORDER BY c.instrument_code
        """.trimIndent(),
    ) { rs, _ ->
        mapOf(
            "instrument" to rs.getString("instrument_code"),
            "value" to rs.getBigDecimal("value"),
            "status" to rs.getString("status"),
            "source" to rs.getString("source"),
            "is_test" to rs.getBoolean("is_test"),
            "fetched_at" to rs.getTimestamp("fetched_at").toInstant(),
            "sample_id" to rs.getObject("sample_id"),
            "label" to label(rs.getString("source"), rs.getString("status"), rs.getBoolean("is_test")),
        )
    }

    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun manual(@RequestBody body: ManualRateRequest): Map<String, Any?> {
        currentUser().require("rates:manual")
        if (body.reason.isBlank()) throw ApiException.badRequest("reason_required", "reason is required for a manual rate")
        if (body.value <= BigDecimal.ZERO) throw ApiException.badRequest("invalid_rate", "rate must be > 0")
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO exchange_rate_samples (id, instrument_code, value, source, is_test)
            VALUES (?, ?, ?, 'MANUAL', false)
            """.trimIndent(),
            id, body.instrument_code, body.value,
        )
        jdbc.update(
            """
            INSERT INTO exchange_rate_current (instrument_code, sample_id, status)
            VALUES (?, ?, 'MANUAL')
            ON CONFLICT (instrument_code) DO UPDATE
                SET sample_id = EXCLUDED.sample_id, status = 'MANUAL', updated_at = clock_timestamp()
            """.trimIndent(),
            body.instrument_code, id,
        )
        audit.record("RATE.MANUAL.SET", "exchange_rate_samples", id, reason = body.reason, after = body)
        return mapOf(
            "id" to id,
            "instrument" to body.instrument_code,
            "value" to body.value,
            "status" to "MANUAL",
            "label" to "Tasa de referencia, no oficial · FUENTE: MANUAL",
        )
    }

    private fun label(source: String, status: String, test: Boolean): String {
        val base = when (source) {
            "ELTOQUE_API" -> "Tasa de referencia de elTOQUE · Tasa de referencia, no oficial"
            "MANUAL" -> "Tasa de referencia, no oficial · FUENTE: MANUAL"
            else -> "DATOS DE PRUEBA · Tasa de referencia, no oficial"
        }
        val stale = if (status == "CACHED" || status == "STALE") " · ESTADO: DATOS EN CACHÉ" else ""
        val t = if (test) " · DATOS DE PRUEBA" else ""
        return base + stale + t
    }
}
