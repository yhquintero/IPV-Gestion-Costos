package cu.ipvgc.domain.rates

import java.math.BigDecimal

/** I-12: solo se guarda muestra si cambió el valor o el sello de fuente. Nunca ≤ 0. */
object SamplePolicy {
    fun shouldStore(
        incoming: BigDecimal,
        previous: BigDecimal?,
        incomingHash: String?,
        previousHash: String?,
    ): Boolean {
        require(incoming > BigDecimal.ZERO) { "rate value must be > 0" }
        if (previous == null) return true
        if (incoming.compareTo(previous) != 0) return true
        if (incomingHash != null && previousHash != null && incomingHash != previousHash) return true
        return false
    }
}
