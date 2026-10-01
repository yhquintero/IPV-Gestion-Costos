package cu.ipvgc.android.core.common

/**
 * Solo valores públicos. Nunca tokens de elTOQUE/Keygen ni claves privadas.
 */
data class PublicConfig(
    val apiBaseUrl: String,
    val certificatePins: List<String> = emptyList(),
    val licensePublicKeyPem: String = "",
    val expectedLicenseAlg: String = "base64+ecdsa-p256",
    val expiringThresholdDays: Long = 7,
)
