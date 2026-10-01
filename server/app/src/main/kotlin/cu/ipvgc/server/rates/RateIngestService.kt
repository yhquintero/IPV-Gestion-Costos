package cu.ipvgc.server.rates

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import cu.ipvgc.domain.hash.ContentHash
import cu.ipvgc.domain.rates.AnomalyDetector
import cu.ipvgc.domain.rates.CachedProvider
import cu.ipvgc.domain.rates.ExchangeRateProvider
import cu.ipvgc.domain.rates.ProviderOutcome
import cu.ipvgc.domain.rates.RateQuote
import cu.ipvgc.domain.rates.RateStatus
import cu.ipvgc.domain.rates.RateStatusMachine
import cu.ipvgc.domain.rates.RateSource
import cu.ipvgc.domain.rates.SamplePolicy
import cu.ipvgc.domain.rates.SecretRedactor
import cu.ipvgc.server.notifications.NotificationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class RateIngestService(
    private val jdbc: JdbcTemplate,
    private val provider: ExchangeRateProvider,
    private val notifications: NotificationService,
    @Value("\${ipvgc.eltoque.mode:SEED}") private val mode: String,
    @Value("\${ipvgc.eltoque.api-key:}") private val apiKey: String,
    @Value("\${ipvgc.eltoque.fresh-window-seconds:600}") private val freshWindowSeconds: Long,
) {
    private val mapper = jacksonObjectMapper()

    @Scheduled(fixedDelayString = "\${ipvgc.eltoque.refresh-interval-ms:300000}")
    fun scheduledTick() {
        if (mode.equals("SEED", ignoreCase = true)) return
        val locked =
            jdbc.queryForObject("SELECT pg_try_advisory_lock(872001)", Boolean::class.java) ?: false
        if (!locked) return
        try {
            runOnce()
        } finally {
            jdbc.queryForObject("SELECT pg_advisory_unlock(872001)", Boolean::class.java)
        }
    }

    fun runOnce(): Map<String, Any?> {
        val cached =
            CachedProvider(provider) {
                jdbc.query(
                    """
                    SELECT s.instrument_code, s.value
                      FROM exchange_rate_current c
                      JOIN exchange_rate_samples s ON s.id = c.sample_id
                     WHERE s.source = 'ELTOQUE_API'
                    """.trimIndent(),
                ) { rs, _ ->
                    RateQuote(
                        rs.getString(1),
                        cu.ipvgc.domain.money.InstrumentCode.parse(rs.getString(1)),
                        rs.getBigDecimal(2),
                    )
                }
            }
        val result = cached.fetch()
        val details = mapper.writeValueAsString(mapOf("error" to result.error, "cache" to result.servedFromCache))
        if (apiKey.isNotBlank() && SecretRedactor.leaks(details, apiKey)) {
            throw IllegalStateException("refusing to persist a payload that contains ELTOQUE_API_KEY")
        }
        jdbc.update(
            """
            INSERT INTO rate_provider_runs
                (provider, outcome, http_status, latency_ms, ratelimit_remaining, retry_after_s, details, unknown_keys, finished_at)
            VALUES ('ELTOQUE', ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, clock_timestamp())
            """.trimIndent(),
            result.outcome.name,
            result.httpStatus,
            result.latencyMs?.toInt(),
            result.ratelimitRemaining,
            result.retryAfterSeconds,
            SecretRedactor.redact(details),
            mapper.writeValueAsString(result.unknownKeys),
        )
        val pause = result.outcome == ProviderOutcome.UNAUTHORIZED
        jdbc.update(
            """
            UPDATE rate_provider_state
               SET paused = ?,
                   pause_reason = ?,
                   last_outcome = ?,
                   last_http_status = ?,
                   ratelimit_remaining = ?,
                   last_success_at = CASE WHEN ? THEN clock_timestamp() ELSE last_success_at END,
                   consecutive_failures = CASE WHEN ? THEN 0 ELSE consecutive_failures + 1 END,
                   updated_at = clock_timestamp()
             WHERE provider = 'ELTOQUE'
            """.trimIndent(),
            pause,
            if (pause) "token_rejected" else null,
            result.outcome.name,
            result.httpStatus,
            result.ratelimitRemaining,
            result.outcome == ProviderOutcome.OK && !result.servedFromCache,
            result.outcome == ProviderOutcome.OK && !result.servedFromCache,
        )
        if (pause) notifications.notifyPlatformAdmins("RATE.UNAUTHORIZED", mapOf("provider" to "ELTOQUE"))
        if (result.unknownKeys.isNotEmpty()) {
            notifications.notifyPlatformAdmins("RATE.CONTRACT_DRIFT", mapOf("keys" to result.unknownKeys))
        }
        val providerOk = result.outcome == ProviderOutcome.OK && !result.servedFromCache
        for (q in result.quotes) {
            val prev =
                jdbc.query(
                    """
                    SELECT s.value, encode(s.payload_hash,'hex')
                      FROM exchange_rate_current c
                      JOIN exchange_rate_samples s ON s.id = c.sample_id
                     WHERE c.instrument_code = ?
                    """.trimIndent(),
                    { rs, _ -> rs.getBigDecimal(1) to rs.getString(2) },
                    q.instrument.code,
                ).firstOrNull()
            val anomaly = prev?.first?.let { AnomalyDetector.isAnomaly(it, q.value, null) } == true
            val store =
                try {
                    SamplePolicy.shouldStore(q.value, prev?.first, result.payloadHash, prev?.second)
                } catch (_: IllegalArgumentException) {
                    false
                }
            if (store && providerOk) {
                val id = UUID.randomUUID()
                val hashHex = result.payloadHash ?: ContentHash.sha256Hex(q.value.toPlainString())
                jdbc.update(
                    """
                    INSERT INTO exchange_rate_samples
                        (id, instrument_code, value, source, source_ts_raw, payload_hash, is_test, anomaly)
                    VALUES (?, ?, ?, 'ELTOQUE_API', ?::jsonb, decode(?, 'hex'), false, ?)
                    """.trimIndent(),
                    id,
                    q.instrument.code,
                    q.value,
                    mapper.writeValueAsString(result.sourceTsRaw ?: emptyMap<String, Any?>()),
                    hashHex,
                    anomaly,
                )
                val status =
                    RateStatusMachine.evaluate(
                        Instant.now(),
                        RateSource.ELTOQUE_API,
                        false,
                        Instant.now(),
                        true,
                        Duration.ofSeconds(freshWindowSeconds),
                    )
                jdbc.update(
                    """
                    INSERT INTO exchange_rate_current (instrument_code, sample_id, status)
                    VALUES (?, ?, ?)
                    ON CONFLICT (instrument_code) DO UPDATE
                        SET sample_id = EXCLUDED.sample_id, status = EXCLUDED.status, updated_at = clock_timestamp()
                    """.trimIndent(),
                    q.instrument.code, id, status.name,
                )
                if (anomaly) notifications.notifyPlatformAdmins("RATE.ANOMALY", mapOf("instrument" to q.instrument.code))
            } else if (!providerOk) {
                jdbc.update(
                    """
                    UPDATE exchange_rate_current SET status = ?, updated_at = clock_timestamp()
                     WHERE instrument_code = ?
                    """.trimIndent(),
                    RateStatus.CACHED.name, q.instrument.code,
                )
            }
        }
        return mapOf("outcome" to result.outcome.name, "quotes" to result.quotes.size, "unknown" to result.unknownKeys)
    }
}
