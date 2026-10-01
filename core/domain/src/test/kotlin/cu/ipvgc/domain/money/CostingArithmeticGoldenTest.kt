package cu.ipvgc.domain.money

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal

class CostingArithmeticGoldenTest {
    data class GoldenFile(
        val cases: List<LineCase>,
        val totals: List<TotalCase>,
    )

    data class LineCase(
        val id: String,
        val quantity: String,
        val unit_cost: String,
        val currency: String,
        val expected_line: String,
    )

    data class TotalCase(
        val id: String,
        val currency: String,
        val lines: List<String>,
        val expected_total: String,
        val yield: String,
        val expected_unit_cost: String,
    )

    companion object {
        private val golden: GoldenFile by lazy {
            val stream = checkNotNull(
                CostingArithmeticGoldenTest::class.java.getResourceAsStream("/golden/costing-arithmetic.json"),
            )
            jacksonObjectMapper().readValue(stream)
        }

        @JvmStatic
        fun lineCases(): List<LineCase> = golden.cases
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lineCases")
    fun `golden line subtotals`(case: LineCase) {
        val got = CostingArithmetic.lineSubtotal(
            Money.parseDecimal(case.quantity),
            Money.of(case.unit_cost, case.currency),
        )
        got shouldBe Money.of(case.expected_line, case.currency)
    }

    @Test
    fun `golden totals and yield unit cost`() {
        golden.totals.forEach { case ->
            val lines = case.lines.map { Money.of(it, case.currency) }
            val total = CostingArithmetic.total(lines)
            total shouldBe Money.of(case.expected_total, case.currency)
            CostingArithmetic.unitCostFromYield(total, BigDecimal(case.yield)) shouldBe
                Money.of(case.expected_unit_cost, case.currency)
        }
    }

    @Test
    fun `total of empty list is rejected`() {
        io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            CostingArithmetic.total(emptyList())
        }
    }
}
