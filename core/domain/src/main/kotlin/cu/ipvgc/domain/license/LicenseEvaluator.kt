package cu.ipvgc.domain.license

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Función pura `(archivo verificado, reloj fiable) → estado` (doc 7.6).
 * Orden de evaluación fijo y cubierto por vectores dorados.
 */
object LicenseEvaluator {

    fun evaluate(input: LicenseEvaluationInput): LicenseStatus {
        val now = reliableNow(input.clock)
        val clockBroken = isClockInconsistent(input.clock, input.issuedAt)

        if (!input.hasFile) {
            return when {
                input.lastKnownRevoked || provider(input, "NOT_FOUND", "REVOKED") -> LicenseStatus.REVOKED
                input.lastKnownSuspended || provider(input, "SUSPENDED") -> LicenseStatus.SUSPENDED
                provider(input, "TOO_MANY_MACHINES") -> LicenseStatus.DEVICE_LIMIT
                provider(input, "NO_MACHINE", "FINGERPRINT_SCOPE_MISMATCH") -> LicenseStatus.NOT_ACTIVATED
                else -> LicenseStatus.DISCONNECTED
            }
        }

        val cryptoOk =
            input.algActual == input.algExpected &&
                input.signatureValid &&
                input.fingerprintMatches
        if (!cryptoOk) {
            return LicenseStatus.NOT_ACTIVATED
        }

        if (input.lastKnownRevoked || provider(input, "NOT_FOUND", "REVOKED")) {
            return LicenseStatus.REVOKED
        }
        if (input.lastKnownSuspended || provider(input, "SUSPENDED")) {
            return LicenseStatus.SUSPENDED
        }
        if (provider(input, "TOO_MANY_MACHINES")) {
            return LicenseStatus.DEVICE_LIMIT
        }
        if (provider(input, "NO_MACHINE", "FINGERPRINT_SCOPE_MISMATCH")) {
            return LicenseStatus.NOT_ACTIVATED
        }

        val expiredByClock =
            input.licenseExpiresAt != null && !now.isBefore(input.licenseExpiresAt)
        if (expiredByClock || provider(input, "EXPIRED")) {
            return LicenseStatus.EXPIRED
        }

        val fileExpired = input.fileExpiresAt != null && !now.isBefore(input.fileExpiresAt)
        if (clockBroken || fileExpired) {
            return LicenseStatus.DISCONNECTED
        }

        if (!input.serverReachable) {
            return LicenseStatus.OFFLINE_GRACE
        }

        val remaining = input.remainingDays ?: daysUntil(input.licenseExpiresAt, now)
        if (remaining != null && remaining <= input.expiringThresholdDays) {
            return LicenseStatus.EXPIRING
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
