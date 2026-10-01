package cu.ipvgc.server.rates

import cu.ipvgc.domain.rates.RateLabels
import cu.ipvgc.domain.rates.RateSource
import cu.ipvgc.domain.rates.RateStatus
import cu.ipvgc.domain.rates.RateVariationMath
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ManualRateRequest(
    val instrument_code: String,
    val value: BigDecimal,
    val reason: String,
)

@RestController
@RequestMapping("/api/v1")
class RateController(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService,
) {
    @GetMapping("/rates/current")
    fun current(): List<Map<String, Any?>> = jdbc.query(
        """
        SELECT c.instrument_code, s.value, c.status, s.source, s.is_test, s.fetched_at, s.id AS sample_id,
               s.anomaly,
               (SELECT s2.value FROM exchange_rate_samples s2
                 WHERE s2.instrument_code = c.instrument_code AND s2.id <> s.id
                 ORDER BY s2.fetched_at DESC LIMIT 1) AS prev_value
          FROM exchange_rate_current c
          JOIN exchange_rate_samples s ON s.id = c.sample_id
         ORDER BY c.instrument_code
        """.trimIndent(),
    ) { rs, _ ->
        val source = RateSource.valueOf(rs.getString("source"))
        val status = RateStatus.valueOf(rs.getString("status"))
        val test = rs.getBoolean("is_test")
        val fetched = rs.getTimestamp("fetched_at").toInstant()
        val value = rs.getBigDecimal("value")
        val prev = rs.getBigDecimal("prev_value")
        val variation = RateVariationMath.of(value, prev)
        val age = Instant.now().epochSecond - fetched.epochSecond
        mapOf(
            "instrument" to rs.getString("instrument_code"),
            "base" to "CUP",
            "value" to value.toPlainString(),
            "label" to RateLabels.compose(source, status, test, fetched.toString()),
            "disclaimer" to RateLabels.UNOFFICIAL,
            "source" to source.name,
            "status" to status.name,
            "is_test" to test,
            "source_timestamp" to null,
            "fetched_at" to fetched,
            "age_seconds" to age,
            "sample_id" to rs.getObject("sample_id"),
            "anomaly" to rs.getBoolean("anomaly"),
            "variation" to variation?.let {
                mapOf(
                    "absolute" to it.absolute.toPlainString(),
                    "percent" to it.percent.toPlainString(),
                    "note" to "respecto a la muestra anterior almacenada",
                )
            },
        )
    }

    @GetMapping("/rates/history")
    fun history(
        @RequestParam instrument: String,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<Map<String, Any?>> =
        jdbc.query(
            """
            SELECT id, value, source, status_proxy, fetched_at, is_test, anomaly
              FROM (
                SELECT s.id, s.value, s.source, s.fetched_at, s.is_test, s.anomaly,
                       c.status AS status_proxy
                  FROM exchange_rate_samples s
                  LEFT JOIN exchange_rate_current c ON c.sample_id = s.id
                 WHERE s.instrument_code = ?
                 ORDER BY s.fetched_at DESC
                 LIMIT ?
              ) x
            """.trimIndent(),
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id"),
                    "value" to rs.getBigDecimal("value").toPlainString(),
                    "source" to rs.getString("source"),
                    "fetched_at" to rs.getTimestamp("fetched_at").toInstant(),
                    "is_test" to rs.getBoolean("is_test"),
                    "anomaly" to rs.getBoolean("anomaly"),
                )
            },
            instrument,
            limit.coerceIn(1, 200),
        )

    @GetMapping("/rates/status")
    fun providerStatus(): Map<String, Any?> {
        val user = currentUser()
        if ("PLATFORM_ADMIN" !in user.roles && "ORG_ADMIN" !in user.roles) {
            throw ApiException.forbidden("forbidden", "admin required")
        }
        val state =
            jdbc.query(
                "SELECT paused, pause_reason, last_success_at, last_outcome, last_http_status, ratelimit_remaining, consecutive_failures FROM rate_provider_state WHERE provider = 'ELTOQUE'",
            ) { rs, _ ->
                mapOf(
                    "paused" to rs.getBoolean("paused"),
                    "pause_reason" to rs.getString("pause_reason"),
                    "last_success_at" to rs.getTimestamp("last_success_at")?.toInstant(),
                    "last_outcome" to rs.getString("last_outcome"),
                    "last_http_status" to rs.getObject("last_http_status"),
                    "ratelimit_remaining" to rs.getObject("ratelimit_remaining"),
                    "consecutive_failures" to rs.getInt("consecutive_failures"),
                )
            }.firstOrNull() ?: emptyMap()
        val lastRun =
            jdbc.query(
                "SELECT outcome, http_status, started_at, finished_at, unknown_keys FROM rate_provider_runs ORDER BY started_at DESC LIMIT 1",
            ) { rs, _ ->
                mapOf(
                    "outcome" to rs.getString("outcome"),
                    "http_status" to rs.getObject("http_status"),
                    "started_at" to rs.getTimestamp("started_at")?.toInstant(),
                    "unknown_keys" to rs.getString("unknown_keys"),
                )
            }.firstOrNull()
        return mapOf("provider" to "ELTOQUE", "state" to state, "last_run" to lastRun, "d04" to "pending")
    }

    @GetMapping("/rate-snapshots/{id}")
    fun snapshot(@PathVariable id: UUID): Map<String, Any?> {
        val head =
            jdbc.query(
                "SELECT id, captured_at, status_at_capture, is_test FROM rate_snapshots WHERE id = ?",
                { rs, _ ->
                    mapOf(
                        "id" to rs.getObject("id"),
                        "captured_at" to rs.getTimestamp("captured_at")?.toInstant(),
                        "status_at_capture" to rs.getString("status_at_capture"),
                        "is_test" to rs.getBoolean("is_test"),
                    )
                },
                id,
            ).firstOrNull() ?: throw ApiException.notFound("rate_snapshot")
        val items =
            jdbc.query(
                "SELECT instrument_code, value, sample_id FROM rate_snapshot_items WHERE snapshot_id = ?",
                { rs, _ ->
                    mapOf(
                        "instrument" to rs.getString(1),
                        "value" to rs.getBigDecimal(2).toPlainString(),
                        "sample_id" to rs.getObject(3),
                    )
                },
                id,
            )
        return head + mapOf("items" to items, "disclaimer" to RateLabels.UNOFFICIAL)
    }

    @PostMapping("/rates/manual")
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
            "value" to body.value.toPlainString(),
            "status" to "MANUAL",
            "label" to RateLabels.compose(RateSource.MANUAL, RateStatus.MANUAL, false),
        )
    }
}
