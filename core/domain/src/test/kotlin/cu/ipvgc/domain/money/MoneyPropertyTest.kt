package cu.ipvgc.domain.money

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.random.Random

class MoneyPropertyTest {
    private val cup = InstrumentCode("CUP")
    private val rng = Random(20261001)

    private fun samples(): List<BigDecimal> {
        val fixed = listOf("0", "0.01", "0.10", "1.00", "1.005", "12.50", "99.99", "100000.12")
            .map { Money.parseDecimal(it) }
        val random = List(40) { BigDecimal(rng.nextInt(0, 1_000_000)).movePointLeft(2) }
        return fixed + random
    }

    @Test
    fun `addition is commutative and has zero as identity`() {
        val values = samples()
        for (a in values) {
            val ma = Money.of(a, cup)
            (ma + Money.zero(cup)) shouldBe ma
            for (b in values.take(12)) {
                val mb = Money.of(b, cup)
                (ma + mb) shouldBe (mb + ma)
            }
        }
    }

    @Test
    fun `line rounding is idempotent at cent scale`() {
        samples().forEach { unit ->
            val line = CostingArithmetic.lineSubtotal(BigDecimal.ONE, Money.of(unit, cup))
            line.roundToCents() shouldBe line
            line.amount.scale() shouldBe RoundingPolicy.CENT_SCALE
        }
    }

    @Test
    fun `sum of rounded lines equals total`() {
        val values = samples()
        repeat(30) {
            val lines = List(3) {
                CostingArithmetic.lineSubtotal(BigDecimal.ONE, Money.of(values.random(rng), cup))
            }
            CostingArithmetic.total(lines) shouldBe lines.reduce { acc, m -> acc + m }
        }
    }
}
