package cu.ipvgc.server.licensing

import cu.ipvgc.domain.license.CreateLicenseRequest
import cu.ipvgc.domain.license.EntitlementChecker
import cu.ipvgc.domain.license.Entitlements
import cu.ipvgc.domain.license.KeygenCloudDisabledProvider
import cu.ipvgc.domain.license.LicenseEvaluationInput
import cu.ipvgc.domain.license.LicenseEvaluator
import cu.ipvgc.domain.license.LicenseProvider
import cu.ipvgc.domain.license.ClockSnapshot
import cu.ipvgc.server.access.CurrentUser
import cu.ipvgc.server.audit.AuditService
import cu.ipvgc.server.security.currentUser
import cu.ipvgc.server.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class IssueLicenseBody(
    val user_id: UUID,
    val policy_code: String,
    val contract_item_id: UUID? = null,
    val max_devices: Int = 2,
)

data class ActivateBody(
    val fingerprint: String,
    val platform: String,
)

@RestController
@RequestMapping("/api/v1")
class LicenseController(
    private val jdbc: JdbcTemplate,
    private val provider: LicenseProvider,
    private val audit: AuditService,
) {
    @GetMapping("/licenses/me")
    fun me(): Map<String, Any?> {
        val user = currentUser()
        val row = loadMine(user) ?: return mapOf("status" to "NOT_ACTIVATED", "entitlements" to emptyList<String>())
        return present(user, row, online = true)
    }

    @GetMapping("/licenses")
    fun list(): List<Map<String, Any?>> {
        val user = currentUser()
        requirePlatformOrAdmin(user)
        return jdbc.query(
            """
            SELECT id, user_id, policy_code, status_cached, expires_at, keygen_license_id, max_devices
              FROM licenses
             WHERE organization_id = ? AND deleted_at IS NULL
             ORDER BY created_at
            """.trimIndent(),
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id"),
                    "user_id" to rs.getObject("user_id"),
                    "policy_code" to rs.getString("policy_code"),
                    "status" to rs.getString("status_cached"),
                    "expires_at" to rs.getTimestamp("expires_at")?.toInstant(),
                    "provider_id" to rs.getString("keygen_license_id"),
                    "max_devices" to rs.getInt("max_devices"),
                )
            },
            user.organizationId,
        )
    }

    @PostMapping("/licenses")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun issue(
        @RequestBody body: IssueLicenseBody,
        @RequestHeader("Idempotency-Key", required = false) idem: String?,
    ): Map<String, Any?> {
        val user = currentUser()
        requirePlatformOrAdmin(user)
        guardProvider()
        val key = idem ?: "issue-${body.user_id}-${body.policy_code}"
        val issued =
            runProvider {
                provider.createLicense(
                    CreateLicenseRequest(
                        policyCode = body.policy_code,
                        organizationId = user.organizationId.toString(),
                        userId = body.user_id.toString(),
                        contractItemId = body.contract_item_id?.toString(),
                        entitlements = Entitlements.POLICY_BASE,
                        maxMachines = body.max_devices,
                        idempotencyKey = key,
                    ),
                )
            }
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO licenses (id, organization_id, user_id, contract_item_id, keygen_license_id,
                                  policy_code, status_cached, max_devices)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, user.organizationId, body.user_id, body.contract_item_id, issued.id,
            body.policy_code, issued.status, body.max_devices,
        )
        for (code in issued.entitlements) {
            jdbc.update(
                """
                INSERT INTO license_entitlements (organization_id, license_id, entitlement_code, source)
                VALUES (?, ?, ?, 'POLICY')
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                user.organizationId, id, code,
            )
        }
        audit.record("LICENSE.ISSUED", "licenses", id, after = mapOf("policy" to body.policy_code))
        return mapOf("id" to id, "provider_id" to issued.id, "status" to issued.status, "policy_code" to body.policy_code)
    }

    @PostMapping("/licenses/{id}/activate")
    @Transactional
    fun activate(
        @PathVariable id: UUID,
        @RequestBody body: ActivateBody,
    ): Map<String, Any?> {
        val user = currentUser()
        val lic = load(user.organizationId, id)
        if (body.platform == "ANDROID") {
            EntitlementChecker(entitlementsOf(id)).require(Entitlements.ANDROID_ACCESS)
        }
        if (body.platform == "WEB") {
            EntitlementChecker(entitlementsOf(id)).require(Entitlements.WEB_ACCESS)
        }
        val providerId = lic.getValue("provider_id") as String
        val machine =
            runProvider {
                provider.activateMachine(providerId, body.fingerprint, body.platform)
            }
        val deviceId = ensureDevice(user, body.fingerprint, body.platform)
        jdbc.update(
            """
            INSERT INTO license_devices (organization_id, license_id, device_id, keygen_machine_id)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (license_id, device_id) DO UPDATE SET keygen_machine_id = EXCLUDED.keygen_machine_id
            """.trimIndent(),
            user.organizationId, id, deviceId, machine.id,
        )
        val after = runProvider { provider.validate(providerId, body.fingerprint) }
        jdbc.update(
            """
            UPDATE licenses
               SET status_cached = 'ACTIVE',
                   first_activated_at = COALESCE(first_activated_at, clock_timestamp()),
                   expires_at = COALESCE(expires_at, ?),
                   last_validated_at = clock_timestamp(),
                   version = version + 1,
                   updated_at = clock_timestamp()
             WHERE id = ?
            """.trimIndent(),
            after.expiry, id,
        )
        jdbc.update(
            """
            INSERT INTO license_events (organization_id, license_id, type, details)
            VALUES (?, ?, 'ACTIVATED', ?::jsonb)
            """.trimIndent(),
            user.organizationId, id, """{"fingerprint":"${body.fingerprint}","platform":"${body.platform}"}""",
        )
        audit.record("LICENSE.ACTIVATED", "licenses", id, after = body)
        return mapOf(
            "machine_id" to machine.id,
            "fingerprint" to machine.fingerprint,
            "expires_at" to after.expiry,
        )
    }

    @PostMapping("/licenses/{id}/web-seat")
    @Transactional
    fun webSeat(@PathVariable id: UUID): Map<String, Any?> {
        val user = currentUser()
        EntitlementChecker(entitlementsOf(id)).require(Entitlements.WEB_ACCESS)
        val fingerprint = "web:$id"
        jdbc.update(
            """
            INSERT INTO license_web_seats (organization_id, license_id, fingerprint)
            VALUES (?, ?, ?)
            ON CONFLICT (license_id) DO NOTHING
            """.trimIndent(),
            user.organizationId, id, fingerprint,
        )
        return activate(id, ActivateBody(fingerprint, "WEB"))
    }

    @PostMapping("/licenses/{id}/machine-file")
    fun checkout(
        @PathVariable id: UUID,
        @RequestBody body: ActivateBody,
    ): Map<String, Any?> {
        currentUser()
        val lic = load(currentUser().organizationId, id)
        val providerId = lic.getValue("provider_id") as String
        val file =
            runProvider {
                provider.checkoutMachineFile(providerId, body.fingerprint, Duration.ofDays(3))
            }
        jdbc.update(
            """
            UPDATE license_devices d
               SET file_expires_at = ?
              FROM devices dev
             WHERE d.device_id = dev.id AND d.license_id = ? AND dev.installation_hash = ?
            """.trimIndent(),
            file.expiresAt, id, body.fingerprint,
        )
        return mapOf(
            "pem" to file.pem,
            "alg" to file.alg,
            "issued_at" to file.issuedAt,
            "expires_at" to file.expiresAt,
        )
    }

    @PostMapping("/licenses/{id}/devices/{fingerprint}/deactivate")
    @Transactional
    fun deactivate(
        @PathVariable id: UUID,
        @PathVariable fingerprint: String,
    ): Map<String, Any?> {
        val user = currentUser()
        requirePlatformOrAdmin(user)
        val lic = load(user.organizationId, id)
        runProvider { provider.deactivateMachine(lic.getValue("provider_id") as String, fingerprint); lic }
        jdbc.update(
            """
            UPDATE license_devices d
               SET deactivated_at = clock_timestamp()
              FROM devices dev
             WHERE d.device_id = dev.id AND d.license_id = ? AND dev.installation_hash = ?
            """.trimIndent(),
            id, fingerprint,
        )
        return mapOf("status" to "DEACTIVATED")
    }

    @PostMapping("/licenses/{id}/renew")
    @Transactional
    fun renew(
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key", required = false) idem: String?,
    ): Map<String, Any?> {
        val user = currentUser()
        requirePlatformOrAdmin(user)
        val key = idem ?: "renew-$id"
        return applyRenewal(user, id, key)
    }

    @GetMapping("/licenses/{id}/reconcile")
    fun reconcile(@PathVariable id: UUID): Map<String, Any?> {
        val user = currentUser()
        requirePlatformOrAdmin(user)
        val lic = load(user.organizationId, id)
        val providerId = lic.getValue("provider_id") as String
        val remote = runProvider { provider.validate(providerId, null) }
        val ours = lic["expires_at"] as Instant?
        val match = ours == remote.expiry
        return mapOf(
            "provider_expiry" to remote.expiry,
            "mirror_expiry" to ours,
            "match" to match,
            "provider_code" to remote.code,
        )
    }

    internal fun applyRenewal(user: CurrentUser, licenseId: UUID, idempotencyKey: String): Map<String, Any?> {
        val existing =
            jdbc.query(
                "SELECT id, status, expiry_after FROM license_renewal_requests WHERE idempotency_key = ?",
                { rs, _ ->
                    mapOf(
                        "id" to rs.getObject("id"),
                        "status" to rs.getString("status"),
                        "expiry_after" to rs.getTimestamp("expiry_after")?.toInstant(),
                    )
                },
                idempotencyKey,
            ).firstOrNull()
        if (existing != null && existing["status"] == "APLICADA") {
            return mapOf("status" to "APLICADA", "expiry_after" to existing["expiry_after"], "idempotent" to true)
        }
        val lic = load(user.organizationId, licenseId)
        val providerId = lic.getValue("provider_id") as String
        val before = runProvider { provider.validate(providerId, null) }
        val reqId = (existing?.get("id") as UUID?) ?: UUID.randomUUID()
        if (existing == null) {
            jdbc.update(
                """
                INSERT INTO license_renewal_requests
                    (id, organization_id, license_id, idempotency_key, status, expiry_before)
                VALUES (?, ?, ?, ?, 'PAGO_CONFIRMADO', ?)
                """.trimIndent(),
                reqId, user.organizationId, licenseId, idempotencyKey, before.expiry,
            )
        }
        val renewed =
            try {
                runProvider { provider.renew(providerId, idempotencyKey) }
            } catch (ex: ApiException) {
                jdbc.update(
                    "UPDATE license_renewal_requests SET status = 'FALLIDA', provider_error = ?, updated_at = clock_timestamp() WHERE id = ?",
                    ex.message, reqId,
                )
                throw ex
            }
        jdbc.update(
            "UPDATE licenses SET expires_at = ?, status_cached = ?, version = version + 1, updated_at = clock_timestamp() WHERE id = ?",
            renewed.expiresAt, renewed.status, licenseId,
        )
        jdbc.update(
            """
            UPDATE license_renewal_requests
               SET status = 'APLICADA', expiry_after = ?, updated_at = clock_timestamp()
             WHERE id = ?
            """.trimIndent(),
            renewed.expiresAt, reqId,
        )
        jdbc.update(
            "INSERT INTO license_events (organization_id, license_id, type, details) VALUES (?, ?, 'RENEWED', '{}'::jsonb)",
            user.organizationId, licenseId,
        )
        audit.record("LICENSE.RENEWED", "licenses", licenseId, after = mapOf("expiry" to renewed.expiresAt))
        return mapOf(
            "status" to "APLICADA",
            "expiry_before" to before.expiry,
            "expiry_after" to renewed.expiresAt,
        )
    }

    private fun present(user: CurrentUser, row: Map<String, Any?>, online: Boolean): Map<String, Any?> {
        val providerId = row["provider_id"] as String?
        val validation = if (providerId != null) runCatching { provider.validate(providerId, null) }.getOrNull() else null
        val expires = (row["expires_at"] as Instant?) ?: validation?.expiry
        val remaining = expires?.let { ChronoUnit.DAYS.between(Instant.now(), it) }
        val status =
            LicenseEvaluator.evaluate(
                LicenseEvaluationInput(
                    hasFile = false,
                    providerCode = validation?.code ?: row["status"] as String?,
                    lastKnownRevoked = row["status"] == "REVOKED",
                    lastKnownSuspended = row["status"] == "SUSPENDED",
                    serverReachable = true,
                    remainingDays = remaining,
                    licenseExpiresAt = expires,
                    clock = ClockSnapshot(Instant.now()),
                    onlineServerAuthority = online,
                ),
            )
        val ents = entitlementsOf(row["id"] as UUID)
        return mapOf(
            "id" to row["id"],
            "policy_code" to row["policy_code"],
            "status" to status.name,
            "blocks_access" to status.blocksAccess,
            "expires_at" to expires,
            "remaining_days" to remaining,
            "entitlements" to ents.toList(),
            "max_devices" to row["max_devices"],
        )
    }

    private fun loadMine(user: CurrentUser): Map<String, Any?>? =
        jdbc.query(
            """
            SELECT id, policy_code, status_cached AS status, expires_at, keygen_license_id AS provider_id, max_devices
              FROM licenses
             WHERE organization_id = ? AND user_id = ? AND deleted_at IS NULL
             ORDER BY created_at DESC
             LIMIT 1
            """.trimIndent(),
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id") as UUID,
                    "policy_code" to rs.getString("policy_code"),
                    "status" to rs.getString("status"),
                    "expires_at" to rs.getTimestamp("expires_at")?.toInstant(),
                    "provider_id" to rs.getString("provider_id"),
                    "max_devices" to rs.getInt("max_devices"),
                )
            },
            user.organizationId,
            user.userId,
        ).firstOrNull()

    private fun load(org: UUID, id: UUID): Map<String, Any?> =
        jdbc.query(
            """
            SELECT id, policy_code, status_cached AS status, expires_at, keygen_license_id AS provider_id, max_devices
              FROM licenses WHERE organization_id = ? AND id = ? AND deleted_at IS NULL
            """.trimIndent(),
            { rs, _ ->
                mapOf(
                    "id" to rs.getObject("id") as UUID,
                    "policy_code" to rs.getString("policy_code"),
                    "status" to rs.getString("status"),
                    "expires_at" to rs.getTimestamp("expires_at")?.toInstant(),
                    "provider_id" to rs.getString("provider_id"),
                    "max_devices" to rs.getInt("max_devices"),
                )
            },
            org, id,
        ).firstOrNull() ?: throw ApiException.notFound("license")

    private fun entitlementsOf(licenseId: UUID): Set<String> =
        jdbc.query(
            "SELECT entitlement_code FROM license_entitlements WHERE license_id = ?",
            { rs, _ -> rs.getString(1) },
            licenseId,
        ).toSet()

    private fun ensureDevice(user: CurrentUser, fingerprint: String, platform: String): UUID {
        val existing =
            jdbc.query(
                "SELECT id FROM devices WHERE organization_id = ? AND installation_hash = ?",
                { rs, _ -> rs.getObject(1) as UUID },
                user.organizationId,
                fingerprint,
            ).firstOrNull()
        if (existing != null) return existing
        val id = UUID.randomUUID()
        val plat = if (platform == "WEB") "WEB" else "ANDROID"
        jdbc.update(
            """
            INSERT INTO devices (id, organization_id, user_id, platform, installation_hash)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            id, user.organizationId, user.userId, plat, fingerprint,
        )
        return id
    }

    private fun requirePlatformOrAdmin(user: CurrentUser) {
        if ("PLATFORM_ADMIN" !in user.roles && "ORG_ADMIN" !in user.roles) {
            throw ApiException.forbidden("forbidden", "platform or org admin required")
        }
    }

    private fun guardProvider() {
        if (provider is KeygenCloudDisabledProvider) {
            throw ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "keygen_blocked",
                "KEYGEN_CLOUD blocked until D-05 legal opinion (ADR-0003)",
            )
        }
    }

    private fun <T> runProvider(block: () -> T): T {
        guardProvider()
        return try {
            block()
        } catch (ex: IllegalStateException) {
            when (ex.message) {
                "TOO_MANY_MACHINES" -> throw ApiException.conflict("device_limit", "TOO_MANY_MACHINES")
                "REVOKED" -> throw ApiException.conflict("revoked", "license revoked")
                "NOT_FOUND" -> throw ApiException.notFound("license")
                "NO_MACHINE" -> throw ApiException.unprocessable("not_activated", "NO_MACHINE")
                "NEVER_ACTIVATED" -> throw ApiException.unprocessable("never_activated", "cannot renew a never-activated license")
                else -> throw ApiException.unprocessable("provider_error", ex.message ?: "provider")
            }
        } catch (ex: IllegalArgumentException) {
            throw ApiException.badRequest("unknown_policy", ex.message ?: "unknown policy")
        }
    }
}
