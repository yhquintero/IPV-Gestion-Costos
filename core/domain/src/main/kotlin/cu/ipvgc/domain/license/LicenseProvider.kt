package cu.ipvgc.domain.license

import java.time.Duration
import java.time.Instant

data class CreateLicenseRequest(
    val policyCode: String,
    val organizationId: String,
    val userId: String,
    val contractItemId: String?,
    val entitlements: Set<String> = Entitlements.POLICY_BASE,
    val maxMachines: Int = 2,
    val idempotencyKey: String,
)

data class ProviderLicense(
    val id: String,
    val policyCode: String,
    val status: String,
    val expiresAt: Instant?,
    val entitlements: Set<String>,
    val maxMachines: Int,
)

data class ProviderMachine(
    val id: String,
    val licenseId: String,
    val fingerprint: String,
    val platform: String,
)

data class MachineFile(
    val pem: String,
    val alg: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val fingerprint: String,
    val licenseId: String,
)

data class Validation(
    val code: String,
    val expiry: Instant?,
    val entitlements: Set<String>,
)

data class PolicySpec(
    val code: String,
    val duration: Duration,
    val expirationBasis: String = "FROM_FIRST_ACTIVATION",
    val renewalBasis: String = "FROM_EXPIRY",
    val expirationStrategy: String = "REVOKE_ACCESS",
    val scheme: String = "ECDSA_P256_SIGN",
    val maxMachines: Int = 2,
)

interface LicenseProvider {
    fun upsertPolicy(spec: PolicySpec): String
    fun createLicense(req: CreateLicenseRequest): ProviderLicense
    fun validate(licenseId: String, fingerprint: String?): Validation
    fun activateMachine(licenseId: String, fingerprint: String, platform: String): ProviderMachine
    fun deactivateMachine(licenseId: String, fingerprint: String)
    fun checkoutMachineFile(licenseId: String, fingerprint: String, ttl: Duration): MachineFile
    fun renew(licenseId: String, idempotencyKey: String): ProviderLicense
    fun suspend(licenseId: String)
    fun reinstate(licenseId: String)
    fun revoke(licenseId: String)
}

/** Adaptador Cloud: no se usa hasta dictamen D-05. */
class KeygenCloudDisabledProvider : LicenseProvider {
    private fun blocked(): Nothing =
        throw IllegalStateException("KEYGEN_CLOUD blocked until D-05 legal opinion (ADR-0003)")

    override fun upsertPolicy(spec: PolicySpec) = blocked()
    override fun createLicense(req: CreateLicenseRequest) = blocked()
    override fun validate(licenseId: String, fingerprint: String?) = blocked()
    override fun activateMachine(licenseId: String, fingerprint: String, platform: String) = blocked()
    override fun deactivateMachine(licenseId: String, fingerprint: String) = blocked()
    override fun checkoutMachineFile(licenseId: String, fingerprint: String, ttl: Duration) = blocked()
    override fun renew(licenseId: String, idempotencyKey: String) = blocked()
    override fun suspend(licenseId: String) = blocked()
    override fun reinstate(licenseId: String) = blocked()
    override fun revoke(licenseId: String) = blocked()
}
