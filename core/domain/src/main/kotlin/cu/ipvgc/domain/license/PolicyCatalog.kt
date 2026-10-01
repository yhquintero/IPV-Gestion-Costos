package cu.ipvgc.domain.license

import java.time.Duration

object PolicyCatalog {
    val IPV_TRIAL_7D =
        PolicySpec("IPV-TRIAL-7D", Duration.ofDays(7), maxMachines = 1)
    val IPV_MENSUAL = PolicySpec("IPV-MENSUAL", Duration.ofDays(30))
    val IPV_TRIMESTRAL = PolicySpec("IPV-TRIMESTRAL", Duration.ofDays(90))
    val IPV_SEMESTRAL = PolicySpec("IPV-SEMESTRAL", Duration.ofDays(180))
    val IPV_ANUAL = PolicySpec("IPV-ANUAL", Duration.ofDays(365))
    val IPV_BIENAL = PolicySpec("IPV-BIENAL", Duration.ofDays(730))
    val IPV_TRIENAL = PolicySpec("IPV-TRIENAL", Duration.ofDays(1095))

    val ALL: List<PolicySpec> =
        listOf(IPV_TRIAL_7D, IPV_MENSUAL, IPV_TRIMESTRAL, IPV_SEMESTRAL, IPV_ANUAL, IPV_BIENAL, IPV_TRIENAL)

    fun provision(provider: LicenseProvider): List<String> = ALL.map { provider.upsertPolicy(it) }
}
