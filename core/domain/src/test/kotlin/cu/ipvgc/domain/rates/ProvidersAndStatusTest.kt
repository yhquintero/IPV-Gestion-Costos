package cu.ipvgc.domain.rates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class ProvidersAndStatusTest {
    private val t0 = Instant.parse("2026-10-01T12:00:00Z")

    @Test
    fun `fresh stale cached unavailable`() {
        assertEquals(
            RateStatus.FRESH,
            RateStatusMachine.evaluate(t0, RateSource.ELTOQUE_API, false, t0.minusSeconds(60), true),
        )
        assertEquals(
            RateStatus.STALE,
            RateStatusMachine.evaluate(t0, RateSource.ELTOQUE_API, false, t0.minusSeconds(11 * 60), true),
        )
        assertEquals(
            RateStatus.CACHED,
            RateStatusMachine.evaluate(t0, RateSource.ELTOQUE_API, false, t0.minusSeconds(60), false),
        )
        assertEquals(
            RateStatus.UNAVAILABLE,
            RateStatusMachine.evaluate(t0, RateSource.ELTOQUE_API, false, null, false),
        )
        assertEquals(RateStatus.TEST, RateStatusMachine.evaluate(t0, RateSource.SEED_TEST, true, t0, true))
        assertEquals(RateStatus.MANUAL, RateStatusMachine.evaluate(t0, RateSource.MANUAL, false, t0, true))
    }

    @Test
    fun `cached provider serves last valid without mutating`() {
        val http = SimulatedElToqueHttp(status = 503, body = "no")
        val inner = ElToqueApiProvider(http)
        val last = ElToqueParser.parse(SimulatedElToqueHttp.COMMUNITY_OK).quotes
        val cached = CachedProvider(inner) { last }
        val r = cached.fetch()
        assertTrue(r.servedFromCache)
        assertEquals(last, r.quotes)
        assertEquals(BigDecimal("442.0"), r.quotes.first { it.instrument.code == "USD" }.value)
    }

    @Test
    fun `429 401 timeout and no live call without D-04`() {
        val limited = ElToqueApiProvider(SimulatedElToqueHttp(status = 429, body = SimulatedElToqueHttp.RATE_LIMIT_BODY, headers = mapOf("retry-after" to "30")))
        val lim = limited.fetch()
        assertEquals(ProviderOutcome.RATE_LIMITED, lim.outcome)
        assertEquals(30, lim.retryAfterSeconds)
        assertEquals(Duration.ofSeconds(30), RetryPolicy.nextDelay(lim.outcome, lim.retryAfterSeconds, 1))

        val unauth = ElToqueApiProvider(SimulatedElToqueHttp(status = 401, body = "{}")).fetch()
        assertEquals(ProviderOutcome.UNAUTHORIZED, unauth.outcome)
        assertNull(RetryPolicy.nextDelay(unauth.outcome, null, 1))

        val timeout = ElToqueApiProvider(SimulatedElToqueHttp(throwTimeout = true)).fetch()
        assertEquals(ProviderOutcome.TIMEOUT, timeout.outcome)

        assertThrows(IllegalStateException::class.java) { ElToqueLiveDisabledHttp().getTrmi() }
    }

    @Test
    fun `local limiter never exceeds hard cap`() {
        val bucket = TokenBucketLimiter(maxPerMinute = 12, maxPerSecond = 12, hardMaxPerMinute = 60) { t0 }
        repeat(12) { assertTrue(bucket.tryAcquire()) }
        assertFalse(bucket.tryAcquire())
    }

    @Test
    fun `labels never claim official rate`() {
        val text = RateLabels.compose(RateSource.ELTOQUE_API, RateStatus.CACHED, false, "2026-10-01T12:00:00Z")
        assertTrue(text.contains(RateLabels.CACHED))
        assertTrue(text.contains(RateLabels.UNOFFICIAL))
        assertFalse(RateLabels.containsForbiddenOfficialClaim(text))
        assertTrue(RateLabels.containsForbiddenOfficialClaim("tasa oficial de Cuba"))
    }

    @Test
    fun `sample policy skips identical values`() {
        assertTrue(SamplePolicy.shouldStore(BigDecimal("755.00"), null, "a", null))
        assertFalse(SamplePolicy.shouldStore(BigDecimal("755.00"), BigDecimal("755.00"), "a", "a"))
        assertTrue(SamplePolicy.shouldStore(BigDecimal("760.00"), BigDecimal("755.00"), "b", "a"))
        assertThrows(IllegalArgumentException::class.java) {
            SamplePolicy.shouldStore(BigDecimal.ZERO, null, null, null)
        }
    }

    @Test
    fun `snapshot selector blocks stale eltoque beyond max`() {
        assertEquals(
            SnapshotSelector.Pick.BLOCK,
            SnapshotSelector.pick(RateStatus.CACHED, RateSource.ELTOQUE_API, 2000, 1440),
        )
        assertEquals(
            SnapshotSelector.Pick.TAKE,
            SnapshotSelector.pick(RateStatus.FRESH, RateSource.ELTOQUE_API, 1, 1440),
        )
        assertEquals(
            SnapshotSelector.Pick.TAKE,
            SnapshotSelector.pick(RateStatus.TEST, RateSource.SEED_TEST, 0, 1440),
        )
    }

    @Test
    fun `redactor hides bearer token`() {
        val secret = "super-secret-token"
        val leaked = "Authorization: Bearer $secret"
        assertFalse(SecretRedactor.redact(leaked).contains(secret))
        assertTrue(SecretRedactor.leaks(leaked, secret))
        assertFalse(SecretRedactor.leaks(SecretRedactor.redact(leaked), secret))
    }
}
