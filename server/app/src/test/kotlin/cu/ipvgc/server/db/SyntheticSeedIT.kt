package cu.ipvgc.server.db

import cu.ipvgc.domain.guard.TestDataGuard
import cu.ipvgc.seed.SyntheticSeed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SyntheticSeedIT : PostgresIT() {
    @Test
    fun `seed loads two organizations and test rates from JSON not from code constants`() {
        admin().use { connection ->
            connection.scalarLong("SELECT count(*) FROM organizations") shouldBe 2
            connection.scalarLong("SELECT count(*) FROM exchange_rate_samples WHERE is_test") shouldBeGreaterThan 0
            connection.scalarLong("SELECT count(*) FROM exchange_rate_samples WHERE source = 'SEED_TEST'") shouldBe
                connection.scalarLong("SELECT count(*) FROM exchange_rate_samples WHERE is_test")
        }
        val rates = SyntheticSeed.loadTestRates()
        rates.instruments.keys shouldContainAll listOf("USD", "EUR", "MLC", "CAD", "MXN", "ZELLE", "CLA")
        val source = SyntheticSeed::class.java
            .getResource("/seed/exchange-rates-test.json")
        checkNotNull(source)
        rates.instruments["USD"] shouldBe "755.00"
    }

    @Test
    fun `production guard refuses the seeded test rates`() {
        admin().use { connection ->
            val n = connection.scalarLong("SELECT count(*) FROM exchange_rate_samples WHERE is_test")
            shouldThrow<IllegalStateException> {
                TestDataGuard.assertProductionHasNoTestRates("prod", n)
            }
        }
    }
}
