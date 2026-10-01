package cu.ipvgc.domain.license

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class LicenseEvaluatorTest {
    private val t0 = Instant.parse("2026-10-01T12:00:00Z")

    @Test
    fun `blocksAccess is true for terminal states`() {
        LicenseStatus.VALID.blocksAccess shouldBe false
        LicenseStatus.EXPIRING.blocksAccess shouldBe false
        LicenseStatus.OFFLINE_GRACE.blocksAccess shouldBe false
        LicenseStatus.EXPIRED.blocksAccess shouldBe true
        LicenseStatus.REVOKED.blocksAccess shouldBe true
        LicenseStatus.DISCONNECTED.blocksAccess shouldBe true
    }

    @Test
    fun `reliable now prefers server time plus elapsed realtime`() {
        val clock =
            ClockSnapshot(
                wallClock = Instant.parse("2026-10-01T00:00:00Z"),
                lastServerTime = Instant.parse("2026-10-01T12:00:00Z"),
                elapsedRealtimeMillis = 5_000,
                elapsedRealtimeAtServer = 0,
            )
        LicenseEvaluator.reliableNow(clock) shouldBe Instant.parse("2026-10-01T12:00:05Z")
    }

    @Test
    fun `clock rewind beyond tolerance is inconsistent`() {
        val clock =
            ClockSnapshot(
                wallClock = Instant.parse("2026-01-01T00:00:00Z"),
                lastSeenWall = t0,
            )
        LicenseEvaluator.isClockInconsistent(clock, issuedAt = Instant.parse("2026-01-01T00:00:00Z")) shouldBe true
    }
}
