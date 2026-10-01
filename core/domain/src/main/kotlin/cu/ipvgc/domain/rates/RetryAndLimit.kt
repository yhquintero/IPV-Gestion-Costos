package cu.ipvgc.domain.rates

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

object RetryPolicy {
    val CAP: Duration = Duration.ofMinutes(15)

    fun nextDelay(
        outcome: ProviderOutcome,
        retryAfterSeconds: Int?,
        attempt: Int,
    ): Duration? {
        return when (outcome) {
            ProviderOutcome.RATE_LIMITED ->
                Duration.ofSeconds((retryAfterSeconds ?: 60).toLong().coerceAtLeast(1))
            ProviderOutcome.UNAUTHORIZED, ProviderOutcome.PAUSED, ProviderOutcome.SKIPPED -> null
            ProviderOutcome.INVALID_PAYLOAD -> null
            ProviderOutcome.UPSTREAM_ERROR, ProviderOutcome.TIMEOUT -> {
                val seconds = (30L shl (attempt - 1).coerceAtLeast(0).coerceAtMost(5))
                Duration.ofSeconds(seconds).coerceAtMost(CAP)
            }
            ProviderOutcome.OK -> null
        }
    }
}

class TokenBucketLimiter(
    private val maxPerMinute: Int = 12,
    private val maxPerSecond: Int = 10,
    private val hardMaxPerMinute: Int = 60,
    private val clock: () -> Instant = { Instant.now() },
) {
    private val minute = ArrayDeque<Instant>()
    private val second = ArrayDeque<Instant>()

    fun tryAcquire(): Boolean {
        val now = clock()
        prune(minute, now, Duration.ofMinutes(1))
        prune(second, now, Duration.ofSeconds(1))
        val cap = maxPerMinute.coerceAtMost(hardMaxPerMinute)
        if (minute.size >= cap) return false
        if (second.size >= maxPerSecond) return false
        minute.addLast(now)
        second.addLast(now)
        return true
    }

    private fun prune(q: ArrayDeque<Instant>, now: Instant, window: Duration) {
        while (q.isNotEmpty() && Duration.between(q.first(), now) > window) q.removeFirst()
    }
}

object AnomalyDetector {
    fun isAnomaly(previous: BigDecimal, next: BigDecimal, thresholdPct: BigDecimal?): Boolean {
        if (thresholdPct == null) return false
        if (previous <= BigDecimal.ZERO) return false
        val pct =
            next.subtract(previous).abs()
                .multiply(BigDecimal(100))
                .divide(previous, 6, RoundingMode.HALF_UP)
        return pct > thresholdPct
    }
}

data class RateVariation(val absolute: BigDecimal, val percent: BigDecimal)

object RateVariationMath {
    fun of(current: BigDecimal, previous: BigDecimal?): RateVariation? {
        if (previous == null || previous <= BigDecimal.ZERO) return null
        val abs = current.subtract(previous)
        val pct = abs.multiply(BigDecimal(100)).divide(previous, 6, RoundingMode.HALF_UP)
        return RateVariation(abs, pct)
    }
}

object SnapshotSelector {
    enum class Pick { TAKE, BLOCK }

    fun pick(status: RateStatus, source: RateSource, ageMinutes: Long, maxStaleMinutes: Int): Pick {
        if (status == RateStatus.UNAVAILABLE) return Pick.BLOCK
        if (source == RateSource.ELTOQUE_API &&
            status != RateStatus.FRESH &&
            ageMinutes > maxStaleMinutes
        ) {
            return Pick.BLOCK
        }
        return Pick.TAKE
    }
}

object SecretRedactor {
    fun redact(text: String): String =
        text
            .replace(Regex("Bearer\\s+\\S+"), "Bearer ***")
            .replace(Regex("ELTOQUE_API_KEY[=:]\\S+"), "ELTOQUE_API_KEY=***")

    fun leaks(text: String, secret: String?): Boolean =
        !secret.isNullOrBlank() && text.contains(secret)
}
