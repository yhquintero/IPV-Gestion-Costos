package cu.ipvgc.domain.rates

/**
 * Textos obligatorios (doc 10.6). Nunca «tasa oficial» ni patrocinio.
 */
object RateLabels {
    const val REFERENCE = "Tasa de referencia de elTOQUE"
    const val UNOFFICIAL = "Tasa de referencia, no oficial"
    const val SOURCE = "Fuente: elTOQUE"
    const val LAST_AVAILABLE = "Última actualización disponible"
    const val CACHED = "FUENTE: elTOQUE · ESTADO: DATOS EN CACHÉ"
    const val TEST = "DATOS DE PRUEBA"
    const val MANUAL = "Tasa manual (no es de elTOQUE)"
    const val UNAVAILABLE = "Tasa no disponible"
    const val API_MISSING = "No disponible vía API"

    fun compose(
        source: RateSource,
        status: RateStatus,
        isTest: Boolean,
        fetchedAt: String? = null,
    ): String {
        if (status == RateStatus.UNAVAILABLE) return UNAVAILABLE
        val parts = mutableListOf<String>()
        when (source) {
            RateSource.ELTOQUE_API -> {
                parts += REFERENCE
                parts += UNOFFICIAL
                parts += SOURCE
                if (status == RateStatus.CACHED) parts += CACHED
                if (status == RateStatus.STALE || status == RateStatus.CACHED) {
                    parts += LAST_AVAILABLE + (fetchedAt?.let { " · $it" } ?: "")
                }
            }
            RateSource.MANUAL -> {
                parts += MANUAL
                parts += UNOFFICIAL
            }
            RateSource.SEED_TEST -> {
                parts += TEST
                parts += UNOFFICIAL
            }
        }
        if (isTest && TEST !in parts) parts += TEST
        return parts.joinToString(" · ")
    }

    fun containsForbiddenOfficialClaim(text: String): Boolean {
        val n = text.lowercase()
        return n.contains("tasa oficial de cuba") ||
            n.contains("tasa oficial") ||
            n.contains("official cuban rate")
    }
}
