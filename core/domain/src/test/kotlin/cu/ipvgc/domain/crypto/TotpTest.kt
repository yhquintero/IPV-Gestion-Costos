package cu.ipvgc.domain.crypto

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TotpTest {
    @Test
    fun `same window matches and far window does not`() {
        val secret = "12345678901234567890".toByteArray()
        val at = 1_000_000_000L
        val code = Totp.generate(secret, at)
        Totp.matches(secret, code, at) shouldBe true
        Totp.matches(secret, code, at + 120) shouldBe false
    }
}
