package cu.ipvgc.server.rates

import cu.ipvgc.domain.rates.ElToqueApiProvider
import cu.ipvgc.domain.rates.ElToqueHttp
import cu.ipvgc.domain.rates.ElToqueLiveDisabledHttp
import cu.ipvgc.domain.rates.ExchangeRateProvider
import cu.ipvgc.domain.rates.SimulatedElToqueHttp
import cu.ipvgc.domain.rates.TokenBucketLimiter
import cu.ipvgc.domain.security.OutboundAllowlist
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Configuration
@EnableScheduling
class RateConfig {
    @Bean
    fun elToqueHttp(
        @Value("\${ipvgc.eltoque.mode:SEED}") mode: String,
        @Value("\${ipvgc.eltoque.api-url:https://tasas.eltoque.com}") apiUrl: String,
        @Value("\${ipvgc.eltoque.api-key:}") apiKey: String,
        @Value("\${ipvgc.eltoque.timeout-ms:10000}") timeoutMs: Long,
    ): ElToqueHttp {
        return when (mode.uppercase()) {
            "MOCK" -> SimulatedElToqueHttp()
            "API" ->
                if (apiKey.isBlank()) {
                    ElToqueLiveDisabledHttp()
                } else {
                    LiveElToqueHttp(apiUrl, apiKey, timeoutMs)
                }
            else -> ElToqueLiveDisabledHttp()
        }
    }

    @Bean
    fun exchangeRateProvider(
        http: ElToqueHttp,
        jdbc: JdbcTemplate,
    ): ExchangeRateProvider {
        val paused = {
            jdbc.query(
                "SELECT paused FROM rate_provider_state WHERE provider = 'ELTOQUE'",
                { rs, _ -> rs.getBoolean(1) },
            ).firstOrNull() == true
        }
        return ElToqueApiProvider(http, limiter = TokenBucketLimiter(), paused = paused)
    }
}

class LiveElToqueHttp(
    baseUrl: String,
    private val apiKey: String,
    timeoutMs: Long,
) : ElToqueHttp {
    private val endpoint: URI
    private val client: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build()
    private val timeout: Duration = Duration.ofMillis(timeoutMs)

    init {
        val root = URI(baseUrl)
        val host = root.host ?: ""
        require(OutboundAllowlist.elToqueHost(host)) {
            "ELTOQUE_API_URL host '$host' is not allow-listed"
        }
        endpoint = root.resolve("/v1/trmi")
    }

    override fun getTrmi(): cu.ipvgc.domain.rates.ElToqueHttpResponse {
        val started = System.nanoTime()
        val req =
            HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .GET()
                .build()
        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        val latency = (System.nanoTime() - started) / 1_000_000
        val headers =
            buildMap {
                res.headers().firstValue("x-ratelimit-remaining").ifPresent { put("x-ratelimit-remaining", it) }
                res.headers().firstValue("retry-after").ifPresent { put("retry-after", it) }
            }
        return cu.ipvgc.domain.rates.ElToqueHttpResponse(
            status = res.statusCode(),
            body = res.body() ?: "",
            contentType = res.headers().firstValue("content-type").orElse("application/json"),
            headers = headers,
            latencyMs = latency,
        )
    }
}
