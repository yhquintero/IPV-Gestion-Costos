package cu.ipvgc.domain.license

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Adaptador simulado. I-18: **no** persiste ni expone la clave de licencia.
 * Firma de machine file = HMAC de prueba (`FAKE-NOT-A-SECRET`), no ECDSA.
 */
class FakeLicenseProvider(
    private val clock: () -> Instant = { Instant.now() },
    private val hmacSecret: ByteArray = FAKE_HMAC,
) : LicenseProvider {

    private val policies = ConcurrentHashMap<String, PolicySpec>()
    private val licenses = ConcurrentHashMap<String, InternalLicense>()
    private val machines = ConcurrentHashMap<String, MutableList<ProviderMachine>>()
    private val createKeys = ConcurrentHashMap<String, String>()
    private val renewKeys = ConcurrentHashMap<String, String>()
    val webhookLog = ConcurrentHashMap<String, String>()

    data class InternalLicense(
        val id: String,
        val policyCode: String,
        var status: String,
        var expiresAt: Instant?,
        val entitlements: Set<String>,
        val maxMachines: Int,
        val duration: Duration,
        var firstActivatedAt: Instant?,
        val organizationId: String,
    )

    override fun upsertPolicy(spec: PolicySpec): String {
        policies[spec.code] = spec
        return spec.code
    }

    override fun createLicense(req: CreateLicenseRequest): ProviderLicense {
        createKeys[req.idempotencyKey]?.let { existing ->
            return toPublic(licenses.getValue(existing))
        }
        val policy = policies[req.policyCode]
            ?: throw IllegalArgumentException("unknown policy ${req.policyCode}")
        val id = UUID.randomUUID().toString()
        val row =
            InternalLicense(
                id = id,
                policyCode = req.policyCode,
                status = "INACTIVE",
                expiresAt = null,
                entitlements = req.entitlements,
                maxMachines = req.maxMachines,
                duration = policy.duration,
                firstActivatedAt = null,
                organizationId = req.organizationId,
            )
        licenses[id] = row
        machines[id] = mutableListOf()
        createKeys[req.idempotencyKey] = id
        return toPublic(row)
    }

    override fun validate(licenseId: String, fingerprint: String?): Validation {
        val lic = licenses[licenseId] ?: return Validation("NOT_FOUND", null, emptySet())
        if (lic.status == "REVOKED") return Validation("REVOKED", lic.expiresAt, lic.entitlements)
        if (lic.status == "SUSPENDED") return Validation("SUSPENDED", lic.expiresAt, lic.entitlements)
        val now = clock()
        if (lic.expiresAt != null && !now.isBefore(lic.expiresAt)) {
            return Validation("EXPIRED", lic.expiresAt, lic.entitlements)
        }
        if (fingerprint != null) {
            val attached = machines[licenseId].orEmpty()
            if (attached.none { it.fingerprint == fingerprint }) {
                return Validation("NO_MACHINE", lic.expiresAt, lic.entitlements)
            }
        }
        return Validation("VALID", lic.expiresAt, lic.entitlements)
    }

    override fun activateMachine(licenseId: String, fingerprint: String, platform: String): ProviderMachine {
        val lic = licenses[licenseId] ?: error("NOT_FOUND")
        if (lic.status == "REVOKED") error("REVOKED")
        val list = machines.getOrPut(licenseId) { mutableListOf() }
        list.find { it.fingerprint == fingerprint }?.let { return it }
        if (list.size >= lic.maxMachines) error("TOO_MANY_MACHINES")
        val machine = ProviderMachine(UUID.randomUUID().toString(), licenseId, fingerprint, platform)
        list += machine
        if (lic.firstActivatedAt == null) {
            val now = clock()
            lic.firstActivatedAt = now
            lic.expiresAt = now.plus(lic.duration)
            lic.status = "ACTIVE"
        }
        return machine
    }

    override fun deactivateMachine(licenseId: String, fingerprint: String) {
        machines[licenseId]?.removeIf { it.fingerprint == fingerprint }
    }

    override fun checkoutMachineFile(licenseId: String, fingerprint: String, ttl: Duration): MachineFile {
        val lic = licenses[licenseId] ?: error("NOT_FOUND")
        if (machines[licenseId].orEmpty().none { it.fingerprint == fingerprint }) error("NO_MACHINE")
        val now = clock()
        val payload =
            """{"license":"$licenseId","fp":"$fingerprint","iat":"$now","exp":"${now.plus(ttl)}"}"""
        val sig = hmac(payload)
        val inner = Base64.getEncoder().encodeToString("$payload|$sig".toByteArray(StandardCharsets.UTF_8))
        val pem = "-----BEGIN MACHINE FILE-----\n$inner\n-----END MACHINE FILE-----"
        return MachineFile(
            pem = pem,
            alg = "HMAC-FAKE",
            issuedAt = now,
            expiresAt = now.plus(ttl),
            fingerprint = fingerprint,
            licenseId = licenseId,
        )
    }

    override fun renew(licenseId: String, idempotencyKey: String): ProviderLicense {
        renewKeys[idempotencyKey]?.let { return toPublic(licenses.getValue(it)) }
        val lic = licenses[licenseId] ?: error("NOT_FOUND")
        if (lic.status == "REVOKED") error("REVOKED")
        val next =
            RenewalMath.nextExpiry(
                currentExpiry = lic.expiresAt,
                duration = lic.duration,
                renewedAt = clock(),
                basis = RenewalMath.Basis.FROM_EXPIRY,
                revoked = false,
                neverActivated = lic.expiresAt == null,
            )
        if (next == null) error("NEVER_ACTIVATED")
        lic.expiresAt = next
        if (lic.status != "SUSPENDED") lic.status = "ACTIVE"
        renewKeys[idempotencyKey] = licenseId
        return toPublic(lic)
    }

    override fun suspend(licenseId: String) {
        licenses[licenseId]?.status = "SUSPENDED"
    }

    override fun reinstate(licenseId: String) {
        val lic = licenses[licenseId] ?: return
        if (lic.status == "REVOKED") return
        lic.status = if (lic.firstActivatedAt != null) "ACTIVE" else "INACTIVE"
    }

    override fun revoke(licenseId: String) {
        licenses[licenseId]?.status = "REVOKED"
    }

    fun get(licenseId: String): ProviderLicense? = licenses[licenseId]?.let(::toPublic)

    fun recordWebhook(eventId: String, kind: String): Boolean =
        webhookLog.putIfAbsent(eventId, kind) == null

    fun verifyMachineFile(pem: String): Boolean {
        val inner =
            pem.lines().filter { !it.startsWith("-----") }.joinToString("").trim()
        val decoded = String(Base64.getDecoder().decode(inner), StandardCharsets.UTF_8)
        val parts = decoded.split("|", limit = 2)
        if (parts.size != 2) return false
        return hmac(parts[0]) == parts[1]
    }

    private fun hmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret, "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun toPublic(row: InternalLicense) =
        ProviderLicense(row.id, row.policyCode, row.status, row.expiresAt, row.entitlements, row.maxMachines)

    companion object {
        val FAKE_HMAC: ByteArray = "FAKE-NOT-A-SECRET".toByteArray(StandardCharsets.UTF_8)
    }
}
