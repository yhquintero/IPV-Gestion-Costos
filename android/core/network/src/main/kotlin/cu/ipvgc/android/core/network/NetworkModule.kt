package cu.ipvgc.android.core.network

import cu.ipvgc.android.core.common.PublicConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

fun interface AccessTokenProvider {
    fun token(): String?
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttp(config: PublicConfig, tokens: AccessTokenProvider): OkHttpClient {
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor(tokens))
                .addInterceptor(idempotencyInterceptor())
        if (config.certificatePins.isNotEmpty()) {
            val pinner = CertificatePinner.Builder()
            config.certificatePins.forEach { pinner.add(hostOf(config.apiBaseUrl), it) }
            builder.certificatePinner(pinner.build())
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun api(client: OkHttpClient, config: PublicConfig): IpvApi {
        val base = config.apiBaseUrl.trimEnd('/') + "/api/v1/"
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(JacksonConverterFactory.create(jacksonObjectMapper()))
            .build()
            .create(IpvApi::class.java)
    }

    private fun authInterceptor(tokens: AccessTokenProvider) =
        Interceptor { chain ->
            val token = tokens.token()
            val req =
                if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                }
            chain.proceed(req)
        }

    private fun idempotencyInterceptor() =
        Interceptor { chain ->
            val original = chain.request()
            val method = original.method
            val req =
                if (method == "POST" || method == "PATCH" || method == "PUT") {
                    val b = original.newBuilder()
                    if (original.header("Idempotency-Key") == null) {
                        b.header("Idempotency-Key", UUID.randomUUID().toString())
                    }
                    b.build()
                } else {
                    original
                }
            chain.proceed(req)
        }

    private fun hostOf(url: String): String =
        url.removePrefix("https://").removePrefix("http://").substringBefore("/").substringBefore(":")
}
