package cu.ipvgc.domain.money

import java.math.BigDecimal

/**
 * Tasa de un instrumento respecto a la moneda base de la empresa (CUP).
 * I-11: el valor debe ser **estrictamente positivo**.
 */
data class Rate(
    val instrument: InstrumentCode,
    val value: BigDecimal,
) {
    init {
        require(value > BigDecimal.ZERO) { "I-11 rates must be > 0, got $value ${instrument.code}" }
        require(value.scale() <= RoundingPolicy.RATE_SCALE) {
            "rate scale ${value.scale()} exceeds ${RoundingPolicy.RATE_SCALE}"
        }
    }

    /**
     * Convierte un importe en [instrument] a la moneda base: `amount * rate`.
     * No etiqueta la fuente; eso vive en la instantánea (doc 10).
     */
    fun toBase(amount: Money, base: InstrumentCode): Money {
        require(amount.currency == instrument) {
            "amount currency ${amount.currency.code} is not ${instrument.code}"
        }
        return Money.of(amount.amount.multiply(value), base)
    }

    companion object {
        fun of(instrument: String, value: String): Rate =
            Rate(InstrumentCode.parse(instrument), Money.parseDecimal(value))
    }
}
