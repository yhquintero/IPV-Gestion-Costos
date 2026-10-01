package cu.ipvgc.domain.money

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RateTest {
    @Test
    fun `rejects non-positive rates I-11`() {
        shouldThrow<IllegalArgumentException> { Rate.of("USD", "0") }
        shouldThrow<IllegalArgumentException> { Rate.of("USD", "-1") }
    }

    @Test
    fun `converts to base currency without inventing a source label`() {
        val usd = Rate.of("USD", "755.00")
        val cup = usd.toBase(Money.of("2.00", "USD"), InstrumentCode("CUP"))
        cup shouldBe Money.of("1510.00", "CUP")
    }
}
