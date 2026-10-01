package cu.ipvgc.domain.rates

import java.time.Duration
import java.time.Instant

object RateStatusMachine {
    fun evaluate(
        now: Instant,
        source: RateSource,
        isTest: Boolean,
        lastValidAt: Instant?,
        providerOk: Boolean,
        freshWindow: Duration = RateWindows.DEFAULT_FRESH,
    ): RateStatus {
        if (isTest || source == RateSource.SEED_TEST) return RateStatus.TEST
        if (source == RateSource.MANUAL) return RateStatus.MANUAL
        if (lastValidAt == null) return RateStatus.UNAVAILABLE
        if (!providerOk) return RateStatus.CACHED
        val age = Duration.between(lastValidAt, now)
        return if (age <= freshWindow) RateStatus.FRESH else RateStatus.STALE
    }
}
