package cu.ipvgc.android.core.security

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun tokenStore(): TokenStore = InMemoryTokenStore()

    @Provides
    @Singleton
    fun biometric(): BiometricGate = InMemoryBiometricGate()

    @Provides
    @Singleton
    fun dbKey(): DatabaseKeyProvider = RandomDatabaseKeyProvider()
}
