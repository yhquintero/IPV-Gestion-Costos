package cu.ipvgc.domain.license

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class RenewalMathTest {
    private val duration = Duration.ofDays(30)
    private val now = Instant.parse("2026-04-01T00:00:00Z")

    @Test
    fun `active license extends from current expiry`() {
        val expiry = Instant.parse("2026-04-10T00:00:00Z")
        val next = RenewalMath.nextExpiry(expiry, duration, now)
        assertEquals(Instant.parse("2026-05-10T00:00:00Z"), next)
    }

    @Test
    fun `expired license still extends from expiry not now`() {
        val expiry = Instant.parse("2026-03-01T00:00:00Z")
        val next = RenewalMath.nextExpiry(expiry, duration, now)
        assertEquals(Instant.parse("2026-03-31T00:00:00Z"), next)
        assertTrue(RenewalMath.stillExpiredAfterRenewal(next, now))
    }

    @Test
    fun `revoked license is not renewed`() {
        val expiry = Instant.parse("2026-04-10T00:00:00Z")
        assertNull(RenewalMath.nextExpiry(expiry, duration, now, revoked = true))
    }

    @Test
    fun `never activated has no expiry to extend`() {
        assertNull(RenewalMath.nextExpiry(null, duration, now, neverActivated = true))
    }
}
