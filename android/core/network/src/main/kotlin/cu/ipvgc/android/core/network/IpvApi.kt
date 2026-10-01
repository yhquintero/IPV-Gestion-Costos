package cu.ipvgc.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface IpvApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("auth/mfa/verify")
    suspend fun verifyMfa(@Body body: MfaRequest): Response<LoginResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Header("Authorization") bearer: String): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("me")
    suspend fun me(): Response<MeDto>

    @GET("products")
    suspend fun products(): Response<List<CatalogItemDto>>

    @GET("raw-materials")
    suspend fun rawMaterials(): Response<List<CatalogItemDto>>

    @GET("ipv-values")
    suspend fun ipvValues(): Response<List<IpvValueDto>>

    @GET("cost-sheets")
    suspend fun costSheets(): Response<List<CostSheetDto>>

    @POST("cost-sheets")
    suspend fun createCostSheet(
        @Header("Idempotency-Key") key: String,
        @Body body: Map<String, Any>,
    ): Response<CostSheetCreatedDto>

    @PATCH("cost-sheets/{id}/versions/{vid}")
    suspend fun patchVersion(
        @Path("id") id: String,
        @Path("vid") vid: String,
        @Body body: Map<String, Any>,
    ): Response<CostSheetVersionDto>

    @POST("cost-sheets/{id}/versions/{vid}/{action}")
    suspend fun transition(
        @Path("id") id: String,
        @Path("vid") vid: String,
        @Path("action") action: String,
        @Body body: Map<String, Any> = emptyMap(),
    ): Response<CostSheetVersionDto>

    @GET("ipv-controls")
    suspend fun controls(): Response<List<IpvControlDto>>

    @POST("ipv-controls")
    suspend fun createControl(
        @Header("Idempotency-Key") key: String,
        @Body body: Map<String, Any>,
    ): Response<IpvControlDto>

    @GET("rates/current")
    suspend fun rates(): Response<List<RateDto>>

    @POST("sync/push")
    suspend fun syncPush(
        @Header("X-Device-Id") deviceId: String,
        @Header("X-Client-Schema") schema: Int = 1,
        @Body body: SyncPushBody,
    ): Response<SyncPushResponse>

    @GET("sync/changes")
    suspend fun syncChanges(
        @retrofit2.http.Query("cursor") cursor: Long,
        @retrofit2.http.Query("epoch") epoch: Long,
    ): Response<SyncPullResponse>

    @GET("sync/bootstrap")
    suspend fun syncBootstrap(): Response<SyncBootstrapResponse>
}
