package cu.ipvgc.domain.rules

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class StructuralRuleEngineTest {
    @Test
    fun `blocks missing yield and empty lines`() {
        val findings = StructuralRuleEngine.evaluate(
            RuleSheet(
                yieldQty = null,
                asOf = LocalDate.parse("2026-10-01"),
                allowedCurrencies = setOf("CUP"),
                linesRequireIpvValue = true,
                lines = emptyList(),
            ),
        )
        StructuralRuleEngine.blocking(findings).size shouldBe 2
    }

    @Test
    fun `accepts a valid material line`() {
        val findings = StructuralRuleEngine.evaluate(
            RuleSheet(
                yieldQty = BigDecimal.ONE,
                asOf = LocalDate.parse("2026-06-01"),
                allowedCurrencies = setOf("CUP"),
                linesRequireIpvValue = true,
                lines = listOf(
                    RuleLine(
                        lineNo = 1,
                        lineType = "MATERIAL",
                        quantity = BigDecimal("2"),
                        ipvValueId = "x",
                        ipvValidFrom = LocalDate.parse("2026-01-01"),
                        ipvValidTo = null,
                        unitCurrency = "CUP",
                    ),
                ),
            ),
        )
        StructuralRuleEngine.blocking(findings).shouldBeEmpty()
    }
}
