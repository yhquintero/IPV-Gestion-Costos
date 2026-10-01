package cu.ipvgc.domain.license

import cu.ipvgc.domain.money.InstrumentCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CommercePricingTest {
    @Test
    fun `CUP freeze uses HALF_UP to cents`() {
        val cup = CommercePricing.freezeCup(BigDecimal("25.00"), BigDecimal("320.333"))
        assertEquals(InstrumentCode.CUP, cup.currency)
        assertEquals(BigDecimal("8008.33"), cup.amount)
    }
}
