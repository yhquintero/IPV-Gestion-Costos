package cu.ipvgc.server

import cu.ipvgc.domain.guard.TestDataGuard
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@SpringBootApplication
class IpvGestionCostosApplication

fun main(args: Array<String>) {
    runApplication<IpvGestionCostosApplication>(*args)
}

@Component
class ProductionTestDataGuard(
    private val jdbc: JdbcTemplate,
    @Value("\${ipvgc.env:dev}") private val env: String,
) : InitializingBean {
    override fun afterPropertiesSet() {
        val count = jdbc.queryForObject(
            "SELECT count(*) FROM exchange_rate_samples WHERE is_test = true",
            Long::class.java,
        ) ?: 0
        TestDataGuard.assertProductionHasNoTestRates(env, count)
    }
}
