package cu.ipvgc.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Importe decimal exacto. **Nunca** usa `Double`/`Float`.
 *
 * Almacenamiento de referencia: `NUMERIC(19,4)`. El redondeo a centavos
 * es una operación explícita ([roundToCents]), no un efecto colateral.
 */
data class Money(
    val amount: BigDecimal,
    val currency: InstrumentCode,
) : Comparable<Money> {
    init {
        require(amount >= BigDecimal.ZERO) {
            "I-11 amounts must be >= 0, got $amount ${currency.code}"
        }
        val integerDigits = amount.precision() - amount.scale()
        require(integerDigits <= RoundingPolicy.MONEY_PRECISION - RoundingPolicy.CENT_SCALE) {
            "Money integer precision $integerDigits exceeds storage"
        }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.add(other.amount), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        val result = amount.subtract(other.amount)
        require(result >= BigDecimal.ZERO) {
            "I-11 subtraction would be negative: $amount - ${other.amount} ${currency.code}"
        }
        return Money(result, currency)
    }

    fun times(quantity: BigDecimal): Money {
        require(quantity >= BigDecimal.ZERO) { "I-11 quantity must be >= 0, got $quantity" }
        return Money(amount.multiply(quantity), currency)
    }

    fun divide(divisor: BigDecimal, scale: Int, mode: RoundingMode): Money {
        require(divisor > BigDecimal.ZERO) { "division by zero or negative: $divisor" }
        return Money(amount.divide(divisor, scale, mode), currency)
    }

    fun roundToCents(mode: RoundingMode = RoundingPolicy.LINE): Money =
        Money(amount.setScale(RoundingPolicy.CENT_SCALE, mode), currency)

    fun roundToStorage(): Money =
        Money(amount.setScale(RoundingPolicy.MONEY_STORAGE_SCALE, RoundingPolicy.LINE), currency)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return currency == other.currency && amount.compareTo(other.amount) == 0
    }

    override fun hashCode(): Int = 31 * amount.stripTrailingZeros().hashCode() + currency.hashCode()

    override fun toString(): String = "${amount.toPlainString()} ${currency.code}"

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "currency mismatch: ${currency.code} vs ${other.currency.code}"
        }
    }

    companion object {
        fun zero(currency: InstrumentCode): Money = Money(BigDecimal.ZERO.setScale(RoundingPolicy.CENT_SCALE), currency)

        fun of(amount: String, currency: String): Money =
            of(parseDecimal(amount), InstrumentCode.parse(currency))

        fun of(amount: BigDecimal, currency: InstrumentCode): Money {
            require(amount.scale() >= 0) { "negative scale is not allowed" }
            return Money(amount, currency)
        }

        fun parseDecimal(raw: String): BigDecimal {
            require(raw.matches(DECIMAL)) { "invalid decimal '$raw'" }
            return BigDecimal(raw)
        }

        private val DECIMAL = Regex("^[+]?\\d+(\\.\\d+)?$")
    }
}
