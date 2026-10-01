package cu.ipvgc.domain.license

import cu.ipvgc.domain.money.InstrumentCode
import cu.ipvgc.domain.money.Money
import cu.ipvgc.domain.money.RoundingPolicy
import java.math.BigDecimal

/** Precio primario USD; CUP derivado se congela al cotizar (doc 8.4). */
object CommercePricing {
    fun freezeCup(priceUsd: BigDecimal, usdToCup: BigDecimal): Money {
        require(priceUsd >= BigDecimal.ZERO)
        require(usdToCup > BigDecimal.ZERO)
        val raw = priceUsd.multiply(usdToCup)
        return Money(raw, InstrumentCode.CUP).roundToCents(RoundingPolicy.LINE)
    }
}
