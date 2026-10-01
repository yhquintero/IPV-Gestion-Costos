package cu.ipvgc.domain.license

/**
 * Estados de producto (doc 7.6). **No** se persisten como bandera:
 * se recalculan con [LicenseEvaluator] cada vez que hace falta.
 */
enum class LicenseStatus {
    VALID,
    EXPIRING,
    EXPIRED,
    SUSPENDED,
    REVOKED,
    OFFLINE_GRACE,
    DISCONNECTED,
    NOT_ACTIVATED,
    DEVICE_LIMIT,
    ;

    val blocksAccess: Boolean
        get() =
            this == EXPIRED ||
                this == SUSPENDED ||
                this == REVOKED ||
                this == DISCONNECTED ||
                this == NOT_ACTIVATED ||
                this == DEVICE_LIMIT

    val allowsOfflineRead: Boolean
        get() = this == VALID || this == EXPIRING || this == OFFLINE_GRACE
}
