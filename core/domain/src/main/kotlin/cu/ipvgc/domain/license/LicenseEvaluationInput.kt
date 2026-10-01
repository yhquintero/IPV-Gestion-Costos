package cu.ipvgc.domain.license

import java.time.Duration
import java.time.Instant

/**
 * Instantánea para el evaluador puro. La verificación criptográfica
 * (alg + firma) ocurre **antes**; aquí solo entra el resultado booleano.
 */
data class ClockSnapshot(
    val wallClock: Instant,
    val lastSeenWall: Instant? = null,
    val lastServerTime: Instant? = null,
    val elapsedRealtimeMillis: Long = 0,
    val elapsedRealtimeAtServer: Long? = null,
    val skewTolerance: Duration = DEFAULT_SKEW,
) {
    companion object {
        val DEFAULT_SKEW: Duration = Duration.ofMinutes(15)
    }
}

data class LicenseEvaluationInput(
    val hasFile: Boolean,
    val algExpected: String = EXPECTED_ALG,
    val algActual: String? = null,
    val signatureValid: Boolean = false,
    val fingerprintMatches: Boolean = false,
    val issuedAt: Instant? = null,
    val licenseExpiresAt: Instant? = null,
    val fileExpiresAt: Instant? = null,
    val providerCode: String? = null,
    val lastKnownRevoked: Boolean = false,
    val lastKnownSuspended: Boolean = false,
    val serverReachable: Boolean = false,
    val remainingDays: Long? = null,
    val expiringThresholdDays: Long = DEFAULT_EXPIRING_DAYS,
    val clock: ClockSnapshot,
    /** Web en línea: el servidor es autoridad; no hay machine file. */
    val onlineServerAuthority: Boolean = false,
) {
    companion object {
        const val EXPECTED_ALG: String = "base64+ecdsa-p256"
        const val DEFAULT_EXPIRING_DAYS: Long = 7
    }
}
