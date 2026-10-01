package cu.ipvgc.server.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import cu.ipvgc.seed.SeedIds
import cu.ipvgc.server.db.SharedPostgres
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.core.env.MapPropertySource
import org.springframework.test.context.ContextConfiguration
import java.security.Security
import java.sql.DriverManager
import java.util.UUID
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [ApiIT.PgInit::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIT {
    @Autowired
    lateinit var rest: TestRestTemplate

    private val mapper = jacksonObjectMapper()

    class PgInit : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            check(SharedPostgres.ready)
            val pg = SharedPostgres.container
            DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password).use { conn ->
                conn.createStatement().use { it.execute("ALTER ROLE app_rw WITH LOGIN PASSWORD 'app'") }
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(BouncyCastleProvider())
                }
                val hash = Argon2PasswordEncoder(16, 32, 1, 19456, 2).encode(PASSWORD)
                conn.prepareStatement("UPDATE users SET password_hash = ?").use { ps ->
                    ps.setString(1, hash)
                    ps.executeUpdate()
                }
            }
            applicationContext.environment.propertySources.addFirst(
                MapPropertySource(
                    "testcontainers",
                    mapOf(
                        "spring.datasource.url" to pg.jdbcUrl,
                        "spring.datasource.username" to "app_rw",
                        "spring.datasource.password" to "app",
                        "spring.flyway.user" to pg.username,
                        "spring.flyway.password" to pg.password,
                        "ipvgc.env" to "test",
                    ),
                ),
            )
        }
    }

    @Test
    fun `login rejects bad passwords uniformly`() {
        val res = rest.postForEntity(
            "/api/v1/auth/login",
            json(mapOf("email" to "admin@alpha.test", "password" to "wrong-password-99")),
            String::class.java,
        )
        res.statusCode.value() shouldBe 401
        res.body!! shouldContain "invalid_credentials"
        res.body!!.lowercase().contains("alpha") shouldBe false
    }

    @Test
    fun `IDOR cannot read another organization product`() {
        val beta = login("admin@beta.test")
        val res = rest.exchange(
            "/api/v1/products/11111111-1111-7000-8000-000000000070",
            HttpMethod.GET,
            HttpEntity<Void>(bearer(beta)),
            String::class.java,
        )
        res.statusCode.value() shouldBe 404
    }

    @Test
    fun `vertical slice draft to vigente to control and audit verify`() {
        val costeador = login("costeador@alpha.test")
        val productId = post(
            "/api/v1/products",
            costeador,
            mapOf(
                "company_id" to COMPANY,
                "category_id" to CATEGORY,
                "kind" to "PRODUCT",
                "code" to "APIIT-${UUID.randomUUID().toString().take(8)}",
                "name" to "Producto vertical",
            ),
        ).path("id").asText()

        val created = post(
            "/api/v1/cost-sheets",
            costeador,
            mapOf(
                "company_id" to COMPANY,
                "product_id" to productId,
                "branch_id" to BRANCH,
                "code" to "FC-APIIT-${UUID.randomUUID().toString().take(6)}",
            ),
        )
        val sheetId = created.path("id").asText()
        val versionId = created.path("version_id").asText()

        val patched = patch(
            "/api/v1/cost-sheets/$sheetId/versions/$versionId",
            costeador,
            mapOf(
                "yield_qty" to 2,
                "calc_currency" to "CUP",
                "lines" to listOf(
                    mapOf(
                        "line_type" to "MATERIAL",
                        "raw_material_id" to MATERIAL,
                        "ipv_value_id" to IPV,
                        "quantity" to "1.005",
                        "unit_cost_snapshot" to "10.00",
                        "unit_currency" to "CUP",
                    ),
                ),
            ),
        )
        patched.path("lines").size() shouldBe 1
        patched.path("total_cost").asText() shouldContain "10.05"

        post("/api/v1/cost-sheets/$sheetId/versions/$versionId/submit", costeador, emptyMap())
            .path("status").asText() shouldBe "EN_REVISION"

        val revisor = login("revisor@alpha.test")
        post("/api/v1/cost-sheets/$sheetId/versions/$versionId/validate", revisor, emptyMap())
            .path("status").asText() shouldBe "VALIDADA"

        val aprobador = login("aprobador@alpha.test")
        post("/api/v1/cost-sheets/$sheetId/versions/$versionId/approve", aprobador, emptyMap())
            .path("status").asText() shouldBe "APROBADA"
        post(
            "/api/v1/cost-sheets/$sheetId/versions/$versionId/activate",
            aprobador,
            mapOf("valid_from" to "2026-10-01"),
        ).path("status").asText() shouldBe "VIGENTE"

        val admin = login("admin@alpha.test")
        val control = post(
            "/api/v1/ipv-controls",
            admin,
            mapOf(
                "company_id" to COMPANY,
                "branch_id" to BRANCH,
                "mode" to "CONSISTENCIA",
                "period_start" to "2026-10-01",
                "period_end" to "2026-10-31",
            ),
        )
        control.path("control_no").asText() shouldContain "IPV-2026-"
        post(
            "/api/v1/ipv-controls/${control.path("id").asText()}/lines",
            admin,
            mapOf(
                "product_id" to productId,
                "cost_sheet_version_id" to versionId,
                "expected_qty" to 2,
                "expected_unit_cost" to "5.03",
                "observed_qty" to 2,
                "observed_unit_cost" to "5.03",
                "observation_source" to "MANUAL",
            ),
        )
        post(
            "/api/v1/ipv-controls/${control.path("id").asText()}/close",
            admin,
            emptyMap(),
        ).path("status").asText() shouldBe "VALIDADO"

        val verify = get("/api/v1/audit/verify", admin)
        verify.path("ok").asBoolean() shouldBe true
        verify.path("events").asInt() shouldNotBe 0
    }

    @Test
    fun `author cannot validate own sheet four eyes`() {
        val admin = login("admin@alpha.test")
        val created = post(
            "/api/v1/cost-sheets",
            admin,
            mapOf(
                "company_id" to COMPANY,
                "product_id" to "11111111-1111-7000-8000-000000000070",
                "branch_id" to BRANCH,
                "code" to "FC-4EYES-${UUID.randomUUID().toString().take(6)}",
            ),
        )
        val sheetId = created.path("id").asText()
        val versionId = created.path("version_id").asText()
        patch(
            "/api/v1/cost-sheets/$sheetId/versions/$versionId",
            admin,
            mapOf(
                "yield_qty" to 1,
                "lines" to listOf(
                    mapOf(
                        "line_type" to "MATERIAL",
                        "raw_material_id" to MATERIAL,
                        "ipv_value_id" to IPV,
                        "quantity" to "1",
                        "unit_cost_snapshot" to "45.00",
                        "unit_currency" to "CUP",
                    ),
                ),
            ),
        )
        post("/api/v1/cost-sheets/$sheetId/versions/$versionId/submit", admin, emptyMap())
        val headers = bearer(admin)
        val res = rest.exchange(
            "/api/v1/cost-sheets/$sheetId/versions/$versionId/validate",
            HttpMethod.POST,
            HttpEntity("{}", headers),
            String::class.java,
        )
        res.statusCode.value() shouldBe 403
    }

    @Test
    fun `idempotency key replays the same POST`() {
        val admin = login("admin@alpha.test")
        val headers = bearer(admin)
        headers.add("Idempotency-Key", "cat-${UUID.randomUUID()}")
        headers.contentType = MediaType.APPLICATION_JSON
        val body = mapper.writeValueAsString(
            mapOf(
                "company_id" to COMPANY,
                "code" to "IDEM-${UUID.randomUUID().toString().take(4)}",
                "name" to "Idem",
                "kind" to "CUSTOM",
            ),
        )
        val first = rest.exchange("/api/v1/categories", HttpMethod.POST, HttpEntity(body, headers), String::class.java)
        val second = rest.exchange("/api/v1/categories", HttpMethod.POST, HttpEntity(body, headers), String::class.java)
        first.statusCode.value() shouldBe 201
        second.statusCode.value() shouldBe 201
        mapper.readTree(first.body).path("id") shouldBe mapper.readTree(second.body).path("id")
    }

    @Test
    fun `rates are labelled and never presented as official`() {
        val admin = login("admin@alpha.test")
        val rates = get("/api/v1/rates/current", admin)
        rates.isArray shouldBe true
        rates[0].path("label").asText().lowercase().contains("oficial de cuba") shouldBe false
        rates[0].path("label").asText() shouldContain "referencia"
    }

    private fun login(email: String): String {
        val res = rest.postForEntity(
            "/api/v1/auth/login",
            json(mapOf("email" to email, "password" to PASSWORD)),
            String::class.java,
        )
        res.statusCode.value() shouldBe 200
        return mapper.readTree(res.body).path("access_token").asText()
    }

    private fun post(path: String, token: String, body: Map<String, Any?>): JsonNode {
        val res = rest.exchange(path, HttpMethod.POST, HttpEntity(body, bearer(token)), String::class.java)
        check(res.statusCode.is2xxSuccessful) { "$path -> ${res.statusCode} ${res.body}" }
        return mapper.readTree(res.body ?: "{}")
    }

    private fun patch(path: String, token: String, body: Map<String, Any?>): JsonNode {
        val res = rest.exchange(path, HttpMethod.PATCH, HttpEntity(body, bearer(token)), String::class.java)
        check(res.statusCode.is2xxSuccessful) { "$path -> ${res.statusCode} ${res.body}" }
        return mapper.readTree(res.body ?: "{}")
    }

    private fun get(path: String, token: String): JsonNode {
        val res = rest.exchange(path, HttpMethod.GET, HttpEntity<Void>(bearer(token)), String::class.java)
        check(res.statusCode.is2xxSuccessful) { "$path -> ${res.statusCode} ${res.body}" }
        return mapper.readTree(res.body ?: "{}")
    }

    private fun json(body: Map<String, Any?>): HttpEntity<Map<String, Any?>> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return HttpEntity(body, headers)
    }

    private fun bearer(token: String): HttpHeaders {
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        headers.contentType = MediaType.APPLICATION_JSON
        return headers
    }

    companion object {
        const val PASSWORD = "Seed-Passw0rd!"
        val COMPANY: String = SeedIds.COMPANY_ALPHA.toString()
        const val BRANCH = "11111111-1111-7000-8000-000000000020"
        const val CATEGORY = "11111111-1111-7000-8000-000000000050"
        const val MATERIAL = "11111111-1111-7000-8000-000000000060"
        const val IPV = "11111111-1111-7000-8000-000000000080"
    }
}
