package cu.ipvgc.domain.license

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Función pura `(archivo verificado, reloj fiable) → estado` (doc 7.6).
 * Orden de evaluación **dirigido por tabla** [EVALUATION_TABLE]; cubierto por vectores dorados.
 */
object LicenseEvaluator {

    data class Step(
        val id: String,
        val result: LicenseStatus,
        val match: (LicenseEvaluationInput, Instant, Boolean) -> Boolean,
    )

    /** Orden de doc 7.6. El primer paso que coincide gana. */
    val EVALUATION_TABLE: List<Step> =
        listOf(
            Step("no-file-revoked", LicenseStatus.REVOKED) { i, _, _ ->
                !i.hasFile && (i.lastKnownRevoked || provider(i, "NOT_FOUND", "REVOKED"))
            },
            Step("no-file-suspended", LicenseStatus.SUSPENDED) { i, _, _ ->
                !i.hasFile && (i.lastKnownSuspended || provider(i, "SUSPENDED"))
            },
            Step("no-file-device-limit", LicenseStatus.DEVICE_LIMIT) { i, _, _ ->
                !i.hasFile && provider(i, "TOO_MANY_MACHINES")
            },
            Step("no-file-not-activated", LicenseStatus.NOT_ACTIVATED) { i, _, _ ->
                !i.hasFile && provider(i, "NO_MACHINE", "FINGERPRINT_SCOPE_MISMATCH")
            },
            Step("no-file-disconnected", LicenseStatus.DISCONNECTED) { i, _, _ ->
                !i.hasFile && !i.onlineServerAuthority
            },
            Step("crypto-invalid", LicenseStatus.NOT_ACTIVATED) { i, _, _ ->
                i.hasFile && (i.algActual != i.algExpected || !i.signatureValid || !i.fingerprintMatches)
            },
            Step("revoked", LicenseStatus.REVOKED) { i, _, _ ->
                i.lastKnownRevoked || provider(i, "NOT_FOUND", "REVOKED")
            },
            Step("suspended", LicenseStatus.SUSPENDED) { i, _, _ ->
                i.lastKnownSuspended || provider(i, "SUSPENDED")
            },
            Step("device-limit", LicenseStatus.DEVICE_LIMIT) { i, _, _ -> provider(i, "TOO_MANY_MACHINES") },
            Step("not-activated", LicenseStatus.NOT_ACTIVATED) { i, _, _ ->
                provider(i, "NO_MACHINE", "FINGERPRINT_SCOPE_MISMATCH")
            },
            Step("expired", LicenseStatus.EXPIRED) { i, now, _ ->
                provider(i, "EXPIRED") || (i.licenseExpiresAt != null && !now.isBefore(i.licenseExpiresAt))
            },
            Step("clock-or-file", LicenseStatus.DISCONNECTED) { i, now, clockBroken ->
                clockBroken || (i.fileExpiresAt != null && !now.isBefore(i.fileExpiresAt))
            },
            Step("offline-grace", LicenseStatus.OFFLINE_GRACE) { i, _, _ -> !i.serverReachable },
            Step("expiring", LicenseStatus.EXPIRING) { i, now, _ ->
                val remaining = i.remainingDays ?: daysUntil(i.licenseExpiresAt, now)
                remaining != null && remaining <= i.expiringThresholdDays
            },
        )

    fun evaluate(input: LicenseEvaluationInput): LicenseStatus {
        val now = reliableNow(input.clock)
        val clockBroken = isClockInconsistent(input.clock, input.issuedAt)
        for (step in EVALUATION_TABLE) {
            if (step.match(input, now, clockBroken)) return step.result
        }
        return LicenseStatus.VALID
    }

    fun isClockInconsistent(clock: ClockSnapshot, issuedAt: Instant?): Boolean {
        val wall = clock.wallClock
        val last = clock.lastSeenWall
        if (last != null && wall.isBefore(last.minus(clock.skewTolerance))) {
            return true
        }
        if (issuedAt != null && wall.isBefore(issuedAt.minus(clock.skewTolerance))) {
            return true
        }
        return false
    }

    fun reliableNow(clock: ClockSnapshot): Instant {
        val server = clock.lastServerTime
        val baseElapsed = clock.elapsedRealtimeAtServer
        if (server != null && baseElapsed != null) {
            val delta = clock.elapsedRealtimeMillis - baseElapsed
            if (delta >= 0) {
                return server.plusMillis(delta)
            }
        }
        return clock.wallClock
    }

    private fun provider(input: LicenseEvaluationInput, vararg codes: String): Boolean {
        val code = input.providerCode?.uppercase() ?: return false
        return codes.any { it == code }
    }

    private fun daysUntil(expiresAt: Instant?, now: Instant): Long? {
        if (expiresAt == null) return null
        return ChronoUnit.DAYS.between(now, expiresAt)
    }
}
