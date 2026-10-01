package cu.ipvgc.domain.money

import java.math.BigDecimal

/**
 * Aritmética de fichas de costo (doc 6.10 / ADR-0007).
 *
 * ```
 * line_subtotal = round_cent(quantity × unit_cost)   # HALF_UP, per line
 * total         = Σ line_subtotal
 * unit_cost     = round_cent(total / yield)
 * ```
 */
object CostingArithmetic {
    fun lineSubtotal(quantity: BigDecimal, unitCost: Money): Money {
        require(quantity >= BigDecimal.ZERO) { "I-11 quantity must be >= 0, got $quantity" }
        return unitCost.times(quantity).roundToCents(RoundingPolicy.LINE)
    }

    fun total(lines: Collection<Money>): Money {
        require(lines.isNotEmpty()) { "cannot total an empty line list" }
        val currency = lines.first().currency
        return lines.fold(Money.zero(currency)) { acc, line -> acc + line }
    }

    fun totalOrZero(lines: Collection<Money>, currency: InstrumentCode): Money =
        if (lines.isEmpty()) Money.zero(currency) else total(lines)

    fun unitCostFromYield(total: Money, yieldQty: BigDecimal): Money {
        require(yieldQty > BigDecimal.ZERO) { "yield must be > 0, got $yieldQty" }
        return total.divide(yieldQty, RoundingPolicy.CENT_SCALE, RoundingPolicy.YIELD)
    }
}
