package cu.ipvgc.domain.guard

/**
 * El perfil de producción se niega a arrancar si hay muestras de tasa de prueba
 * (doc 13.11). La UI las rotula "DATOS DE PRUEBA" en cualquier entorno.
 */
object TestDataGuard {
    fun assertProductionHasNoTestRates(environment: String, testSampleCount: Long) {
        val prod = environment.equals("prod", ignoreCase = true) ||
            environment.equals("production", ignoreCase = true)
        if (prod && testSampleCount > 0) {
            throw IllegalStateException(
                "Production refuses to start: found $testSampleCount SEED_TEST/is_test rate sample(s)",
            )
        }
    }
}
