package cu.ipvgc.android.core.security

/**
 * Clave de SQLCipher (256 bit) envuelta por Keystore. En pruebas: aleatoria en memoria.
 */
interface DatabaseKeyProvider {
    fun passphrase(): ByteArray
}

class RandomDatabaseKeyProvider : DatabaseKeyProvider {
    private val key: ByteArray = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }

    override fun passphrase(): ByteArray = key.copyOf()
}
