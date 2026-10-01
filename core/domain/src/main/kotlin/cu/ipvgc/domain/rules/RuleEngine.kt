package cu.ipvgc.domain.rules

import cu.ipvgc.domain.money.InstrumentCode
import java.math.BigDecimal
import java.time.LocalDate

enum class RuleSeverity { ERROR, WARNING, INFO }

data class RuleFinding(
    val id: String,
    val severity: RuleSeverity,
    val message: String,
)

data class RuleLine(
    val lineNo: Int,
    val lineType: String,
    val quantity: BigDecimal,
    val ipvValueId: String?,
    val ipvValidFrom: LocalDate?,
    val ipvValidTo: LocalDate?,
    val unitCurrency: String?,
    val subVersionAnnulled: Boolean = false,
)

data class RuleSheet(
    val yieldQty: BigDecimal?,
    val asOf: LocalDate,
    val allowedCurrencies: Set<String>,
    val linesRequireIpvValue: Boolean,
    val lines: List<RuleLine>,
)

/**
 * Plantillas estructurales v1 (doc 6.6). No son normas legales (D-02).
 */
object StructuralRuleEngine {
    fun evaluate(sheet: RuleSheet): List<RuleFinding> {
        val findings = mutableListOf<RuleFinding>()
        val yield = sheet.yieldQty
        if (yield == null || yield <= BigDecimal.ZERO) {
            findings += RuleFinding("R-YIELD", RuleSeverity.ERROR, "yield must be > 0")
        }
        if (sheet.lines.isEmpty()) {
            findings += RuleFinding("R-LINES", RuleSeverity.ERROR, "cost sheet has no lines")
        }
        sheet.lines.forEach { line ->
            if (line.quantity <= BigDecimal.ZERO) {
                findings += RuleFinding("R-QTY-${line.lineNo}", RuleSeverity.ERROR, "quantity must be > 0")
            }
            if (sheet.linesRequireIpvValue && line.lineType == "MATERIAL" && line.ipvValueId == null) {
                findings += RuleFinding("R-IPV-${line.lineNo}", RuleSeverity.ERROR, "material line requires a current IPV value")
            }
            if (line.lineType == "MATERIAL" && line.ipvValueId != null) {
                val from = line.ipvValidFrom
                val to = line.ipvValidTo
                if (from != null && from.isAfter(sheet.asOf) || (to != null && !to.isAfter(sheet.asOf))) {
                    findings += RuleFinding("R-IPV-VALID-${line.lineNo}", RuleSeverity.ERROR, "IPV value is not valid at ${sheet.asOf}")
                }
            }
            if (line.unitCurrency != null && InstrumentCode.parse(line.unitCurrency).code !in sheet.allowedCurrencies) {
                findings += RuleFinding("R-CCY-${line.lineNo}", RuleSeverity.ERROR, "currency ${line.unitCurrency} is not allowed")
            }
            if (line.subVersionAnnulled) {
                findings += RuleFinding("R-SUB-${line.lineNo}", RuleSeverity.ERROR, "sub-sheet is annulled")
            }
        }
        return findings
    }

    fun blocking(findings: List<RuleFinding>): List<RuleFinding> =
        findings.filter { it.severity == RuleSeverity.ERROR }
}
