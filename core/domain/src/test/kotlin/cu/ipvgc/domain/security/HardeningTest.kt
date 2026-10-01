package cu.ipvgc.domain.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class HardeningTest {
    @Test
    fun `login throttle blocks after window fills`() {
        val t0 = Instant.parse("2026-10-01T12:00:00Z")
        val clock = mutableListOf(t0)
        val throttle = LoginThrottle(maxPerWindow = 3, window = Duration.ofMinutes(15)) { clock.last() }
        repeat(3) { assertTrue(throttle.allow("10.0.0.1")) }
        assertFalse(throttle.allow("10.0.0.1"))
        assertTrue(throttle.allow("10.0.0.2"))
        clock += t0.plus(Duration.ofMinutes(16))
        assertTrue(throttle.allow("10.0.0.1"))
    }

    @Test
    fun `outbound allowlist rejects user-controlled hosts`() {
        assertTrue(OutboundAllowlist.elToqueHost("tasas.eltoque.com"))
        assertFalse(OutboundAllowlist.elToqueHost("evil.example"))
        assertFalse(OutboundAllowlist.elToqueHost("tasas.eltoque.com.evil"))
        assertTrue(OutboundAllowlist.keygenHost("api.keygen.sh"))
        assertFalse(OutboundAllowlist.keygenHost("api.keygen.sh.evil"))
    }

    @Test
    fun `api headers do not claim CORS star or stack traces`() {
        assertTrue(ApiSecurityHeaders.ALL["X-Frame-Options"] == "DENY")
        assertTrue(ApiSecurityHeaders.ALL["Cache-Control"] == "no-store")
        assertFalse(ApiSecurityHeaders.ALL.values.any { it.contains("*") })
    }
}
