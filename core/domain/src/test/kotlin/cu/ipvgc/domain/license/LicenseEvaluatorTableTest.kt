package cu.ipvgc.domain.license

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class LicenseEvaluatorTableTest {
    @Test
    fun `table order matches doc 7_6`() {
        val ids = LicenseEvaluator.EVALUATION_TABLE.map { it.id }
        assertEquals(
            listOf(
                "no-file-revoked",
                "no-file-suspended",
                "no-file-device-limit",
                "no-file-not-activated",
                "no-file-disconnected",
                "crypto-invalid",
                "revoked",
                "suspended",
                "device-limit",
                "not-activated",
                "expired",
                "clock-or-file",
                "offline-grace",
                "expiring",
            ),
            ids,
        )
    }

    @Test
    fun `online web seat with valid provider is VALID not DISCONNECTED`() {
        val input =
            LicenseEvaluationInput(
                hasFile = false,
                providerCode = "VALID",
                serverReachable = true,
                remainingDays = 40,
                clock = ClockSnapshot(Instant.parse("2026-10-01T12:00:00Z")),
                onlineServerAuthority = true,
            )
        assertEquals(LicenseStatus.VALID, LicenseEvaluator.evaluate(input))
    }
}
