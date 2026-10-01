package cu.ipvgc.android

import cu.ipvgc.android.core.common.PublicConfig
import cu.ipvgc.android.core.network.AccessTokenProvider
import cu.ipvgc.android.core.security.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun publicConfig(): PublicConfig =
        PublicConfig(
            apiBaseUrl = BuildConfig.API_BASE_URL,
            licensePublicKeyPem = BuildConfig.LICENSE_PUBLIC_KEY_PEM,
        )

    @Provides
    @Singleton
    fun accessTokenProvider(store: TokenStore): AccessTokenProvider =
        AccessTokenProvider { store.accessToken() }
}
