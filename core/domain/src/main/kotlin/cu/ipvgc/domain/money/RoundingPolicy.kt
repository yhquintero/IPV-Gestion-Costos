package cu.ipvgc.domain.money

import java.math.RoundingMode

/**
 * Política de redondeo y escalas (ADR-0007 / D-25).
 *
 * Línea base de IPV: redondeo **por línea** a centavos con `HALF_UP`,
 * el total es la suma de líneas ya redondeadas.
 */
object RoundingPolicy {
    const val CENT_SCALE: Int = 2
    const val MONEY_STORAGE_SCALE: Int = 4
    const val QUANTITY_SCALE: Int = 6
    const val RATE_SCALE: Int = 6
    const val PERCENT_SCALE: Int = 6
    const val MONEY_PRECISION: Int = 19

    val LINE: RoundingMode = RoundingMode.HALF_UP
    val YIELD: RoundingMode = RoundingMode.HALF_UP
}
