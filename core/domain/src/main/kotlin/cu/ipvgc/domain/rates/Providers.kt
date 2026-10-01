package cu.ipvgc.domain.rates

import cu.ipvgc.domain.money.InstrumentCode
import java.math.BigDecimal

/** Semilla inyectada; **nunca** constantes de producto (doc 13.11). */
class SeedProvider(
    private val quotes: Map<String, BigDecimal>,
) : ExchangeRateProvider {
    override fun fetch(): FetchResult {
        val mapped =
            quotes.map { (code, value) ->
                RateQuote(code, InstrumentCode.parse(code), value)
            }
        return FetchResult(ProviderOutcome.OK, quotes = mapped)
    }
}

class CachedProvider(
    private val inner: ExchangeRateProvider,
    private val lastValid: () -> List<RateQuote>,
) : ExchangeRateProvider {
    override fun fetch(): FetchResult {
        val result = inner.fetch()
        if (result.outcome == ProviderOutcome.OK && result.quotes.isNotEmpty()) return result
        val cached = lastValid()
        if (cached.isEmpty()) return result
        return result.copy(quotes = cached, servedFromCache = true)
    }
}

class ElToqueApiProvider(
    private val http: ElToqueHttp,
    private val mappings: Map<String, String> = ElToqueParser.DEFAULT_MAP,
    private val limiter: TokenBucketLimiter = TokenBucketLimiter(),
    private val paused: () -> Boolean = { false },
) : ExchangeRateProvider {
    override fun fetch(): FetchResult {
        if (paused()) return FetchResult(ProviderOutcome.PAUSED, error = "paused_unauthorized")
        if (!limiter.tryAcquire()) {
            return FetchResult(ProviderOutcome.RATE_LIMITED, error = "local_limiter")
        }
        val httpResult =
            try {
                http.getTrmi()
            } catch (_: Exception) {
                return FetchResult(ProviderOutcome.TIMEOUT, error = "network")
            }
        val remaining = httpResult.headers["x-ratelimit-remaining"]?.toIntOrNull()
        val retryAfter = httpResult.headers["retry-after"]?.toIntOrNull()
        return when (httpResult.status) {
            200 ->
                ElToqueParser.parse(httpResult.body, httpResult.contentType, mappings).copy(
                    httpStatus = 200,
                    latencyMs = httpResult.latencyMs,
                    ratelimitRemaining = remaining,
                )
            429 ->
                FetchResult(
                    ProviderOutcome.RATE_LIMITED,
                    httpStatus = 429,
                    latencyMs = httpResult.latencyMs,
                    ratelimitRemaining = remaining,
                    retryAfterSeconds = retryAfter,
                )
            401, 422 ->
                FetchResult(
                    ProviderOutcome.UNAUTHORIZED,
                    httpStatus = httpResult.status,
                    latencyMs = httpResult.latencyMs,
                    error = "token_rejected",
                )
            400 ->
                FetchResult(
                    ProviderOutcome.INVALID_PAYLOAD,
                    httpStatus = 400,
                    error = "bad_request_interval",
                )
            else ->
                FetchResult(
                    ProviderOutcome.UPSTREAM_ERROR,
                    httpStatus = httpResult.status,
                    latencyMs = httpResult.latencyMs,
                )
        }
    }
}

/** Live HTTPS está bloqueado sin clave (D-04). */
class ElToqueLiveDisabledHttp : ElToqueHttp {
    override fun getTrmi(): ElToqueHttpResponse =
        throw IllegalStateException("ELTOQUE live API blocked until D-04 (token + terms)")
}

class SimulatedElToqueHttp(
    var status: Int = 200,
    var body: String = COMMUNITY_OK,
    var contentType: String? = "application/json",
    var headers: Map<String, String> = mapOf("x-ratelimit-remaining" to "59"),
    var throwTimeout: Boolean = false,
) : ElToqueHttp {
    override fun getTrmi(): ElToqueHttpResponse {
        if (throwTimeout) throw RuntimeException("timeout")
        return ElToqueHttpResponse(status, body, contentType, headers, latencyMs = 12)
    }

    companion object {
        /** Muestra comunitaria 2025-10-04 (anexo B). No es semilla de producto. */
        const val COMMUNITY_OK: String =
            """{"tasas":{"USD":442.0,"ECU":500.0,"MLC":210.0,"USDT_TRC20":440.0,"BTC":1.0,"BNB":1.0,"TRX":1.0},"date":"2025-10-04","hour":10,"minutes":34,"seconds":15}"""
        const val HTML_CHALLENGE: String = "<html><title>Just a moment...</title></html>"
        const val TRUNCATED: String = """{"tasas":{"USD":442.0"""
        const val NEGATIVE: String = """{"tasas":{"USD":-1,"ECU":500.0,"MLC":210.0}}"""
        const val ZERO: String = """{"tasas":{"USD":0,"ECU":500.0,"MLC":210.0}}"""
        const val RATE_LIMIT_BODY: String = """{"error":"rate limit","limit":60}"""
    }
}
