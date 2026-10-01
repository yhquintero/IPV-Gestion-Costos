package cu.ipvgc.domain.license

object Entitlements {
    const val IPV_BASIC = "IPV_BASIC"
    const val IPV_ADVANCED = "IPV_ADVANCED"
    const val COST_SHEETS = "COST_SHEETS"
    const val REPORTS = "REPORTS"
    const val MULTI_COMPANY = "MULTI_COMPANY"
    const val MULTI_BRANCH = "MULTI_BRANCH"
    const val ANDROID_ACCESS = "ANDROID_ACCESS"
    const val WEB_ACCESS = "WEB_ACCESS"
    const val API_ACCESS = "API_ACCESS"
    const val ADVANCED_AUDIT = "ADVANCED_AUDIT"
    const val DATA_EXPORT = "DATA_EXPORT"

    val POLICY_BASE: Set<String> =
        setOf(IPV_BASIC, COST_SHEETS, REPORTS, ANDROID_ACCESS, WEB_ACCESS)
}

class EntitlementChecker(private val granted: Set<String>) {
    fun has(code: String): Boolean = code in granted

    fun require(code: String) {
        require(has(code)) { "missing entitlement $code" }
    }
}
