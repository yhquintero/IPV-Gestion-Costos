package cu.ipvgc.domain.money

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {
    @Test
    fun `parses decimal strings and never uses binary floating point`() {
        val a = Money.of("0.1", "CUP")
        val b = Money.of("0.2", "CUP")
        (a + b) shouldBe Money.of("0.3", "CUP")
    }

    @Test
    fun `rejects negative amounts I-11`() {
        shouldThrow<IllegalArgumentException> { Money.of("-0.01", "CUP") }
    }

    @Test
    fun `rejects currency mismatch`() {
        shouldThrow<IllegalArgumentException> {
            Money.of("1.00", "CUP") + Money.of("1.00", "USD")
        }
    }

    @Test
    fun `equality ignores trailing zeros`() {
        Money.of("1.10", "CUP") shouldBe Money.of("1.1", "CUP")
    }

    @Test
    fun `instrument codes are uppercase 3 to 8 letters`() {
        InstrumentCode.parse("usd").code shouldBe "USD"
        InstrumentCode.parse("ZELLE").code shouldBe "ZELLE"
        shouldThrow<IllegalArgumentException> { InstrumentCode.parse("us") }
        shouldThrow<IllegalArgumentException> { InstrumentCode.parse("usd-1") }
    }

    @Test
    fun `zero factory`() {
        Money.zero(InstrumentCode("CUP")).amount.compareTo(BigDecimal.ZERO) shouldBe 0
    }
}
