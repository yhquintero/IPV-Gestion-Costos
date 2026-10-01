package cu.ipvgc.server.rates

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ElToqueModeGuard(
    @Value("\${ipvgc.env:dev}") private val env: String,
    @Value("\${ipvgc.eltoque.mode:SEED}") private val mode: String,
) : InitializingBean {
    override fun afterPropertiesSet() {
        val prod = env.equals("prod", true) || env.equals("production", true)
        if (prod && mode.uppercase() in setOf("SEED", "MOCK")) {
            throw IllegalStateException("ELTOQUE_PROVIDER_MODE=$mode is forbidden in production (doc 13.11)")
        }
    }
}
