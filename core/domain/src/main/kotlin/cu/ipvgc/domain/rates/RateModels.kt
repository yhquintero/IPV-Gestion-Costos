package cu.ipvgc.domain.rates

import cu.ipvgc.domain.money.InstrumentCode
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

enum class RateSource { ELTOQUE_API, MANUAL, SEED_TEST }

enum class RateStatus { FRESH, STALE, CACHED, UNAVAILABLE, MANUAL, TEST }

enum class ProviderOutcome {
    OK,
    RATE_LIMITED,
    UNAUTHORIZED,
    UPSTREAM_ERROR,
    INVALID_PAYLOAD,
    TIMEOUT,
    PAUSED,
    SKIPPED,
}

data class RateQuote(
    val providerCode: String,
    val instrument: InstrumentCode,
    val value: BigDecimal,
)

data class FetchResult(
    val outcome: ProviderOutcome,
    val quotes: List<RateQuote> = emptyList(),
    val unknownKeys: List<String> = emptyList(),
    val sourceTsRaw: Map<String, Any?>? = null,
    val payloadHash: String? = null,
    val httpStatus: Int? = null,
    val latencyMs: Long? = null,
    val ratelimitRemaining: Int? = null,
    val retryAfterSeconds: Int? = null,
    val error: String? = null,
    val servedFromCache: Boolean = false,
)

data class CurrentRate(
    val instrument: InstrumentCode,
    val value: BigDecimal?,
    val source: RateSource,
    val status: RateStatus,
    val isTest: Boolean,
    val fetchedAt: Instant?,
    val sampleId: String? = null,
)

interface ExchangeRateProvider {
    fun fetch(): FetchResult
}

data class ElToqueHttpResponse(
    val status: Int,
    val body: String,
    val contentType: String? = "application/json",
    val headers: Map<String, String> = emptyMap(),
    val latencyMs: Long = 0,
)

interface ElToqueHttp {
    fun getTrmi(): ElToqueHttpResponse
}

object RateWindows {
    val DEFAULT_INTERVAL: Duration = Duration.ofMinutes(5)
    val DEFAULT_FRESH: Duration = Duration.ofMinutes(10)
    val MIN_INTERVAL: Duration = Duration.ofSeconds(60)
    val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
}
