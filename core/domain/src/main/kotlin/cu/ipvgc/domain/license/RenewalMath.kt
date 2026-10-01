package cu.ipvgc.domain.license

import java.time.Duration
import java.time.Instant

/**
 * Mandato: `renewalBasis = FROM_EXPIRY` (doc 8). No se cambia a
 * `FROM_NOW_IF_EXPIRED` sin D-12. Una licencia **revocada** no se renueva.
 */
object RenewalMath {
    enum class Basis { FROM_EXPIRY, FROM_NOW, FROM_NOW_IF_EXPIRED }

    fun nextExpiry(
        currentExpiry: Instant?,
        duration: Duration,
        renewedAt: Instant,
        basis: Basis = Basis.FROM_EXPIRY,
        revoked: Boolean = false,
        neverActivated: Boolean = currentExpiry == null,
    ): Instant? {
        if (revoked) return null
        if (neverActivated) return null
        val expiry = currentExpiry!!
        return when (basis) {
            Basis.FROM_EXPIRY -> expiry.plus(duration)
            Basis.FROM_NOW -> renewedAt.plus(duration)
            Basis.FROM_NOW_IF_EXPIRED ->
                if (renewedAt.isAfter(expiry)) renewedAt.plus(duration) else expiry.plus(duration)
        }
    }

    fun stillExpiredAfterRenewal(newExpiry: Instant?, now: Instant): Boolean =
        newExpiry == null || !now.isBefore(newExpiry)
}
