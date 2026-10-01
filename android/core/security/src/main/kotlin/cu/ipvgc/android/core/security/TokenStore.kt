package cu.ipvgc.android.core.security

/**
 * Tokens en Keystore/Tink. **No** EncryptedSharedPreferences (obsoleta).
 * El access token nunca se escribe en logs.
 */
interface TokenStore {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun save(access: String, refresh: String?)
    fun clear()
}

class InMemoryTokenStore : TokenStore {
    @Volatile private var access: String? = null
    @Volatile private var refresh: String? = null

    override fun accessToken(): String? = access

    override fun refreshToken(): String? = refresh

    override fun save(access: String, refresh: String?) {
        this.access = access
        this.refresh = refresh
    }

    override fun clear() {
        access = null
        refresh = null
    }
}
