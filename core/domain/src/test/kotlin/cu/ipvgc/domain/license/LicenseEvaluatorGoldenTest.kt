package cu.ipvgc.domain.license

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.Instant

class LicenseEvaluatorGoldenTest {
    private val mapper = jacksonObjectMapper()

    @TestFactory
    fun goldenStates(): List<DynamicTest> {
        val root = mapper.readTree(javaClass.getResourceAsStream("/golden/license-states.json"))
        return root.get("cases").map { node ->
            val id = node.get("id").asText()
            DynamicTest.dynamicTest(id) {
                val expected = LicenseStatus.valueOf(node.get("expected").asText())
                LicenseEvaluator.evaluate(parse(node.get("input"))) shouldBe expected
            }
        }
    }

    private fun parse(n: JsonNode): LicenseEvaluationInput {
        val wall = Instant.parse(n.get("wallClock").asText())
        val lastSeen = n.get("lastSeenWall")?.takeUnless { it.isNull }?.asText()?.let(Instant::parse)
        return LicenseEvaluationInput(
            hasFile = n.path("hasFile").asBoolean(false),
            algActual = n.get("algActual")?.takeUnless { it.isNull }?.asText(),
            signatureValid = n.path("signatureValid").asBoolean(false),
            fingerprintMatches = n.path("fingerprintMatches").asBoolean(false),
            issuedAt = n.get("issuedAt")?.takeUnless { it.isNull }?.asText()?.let(Instant::parse),
            licenseExpiresAt = n.get("licenseExpiresAt")?.takeUnless { it.isNull }?.asText()?.let(Instant::parse),
            fileExpiresAt = n.get("fileExpiresAt")?.takeUnless { it.isNull }?.asText()?.let(Instant::parse),
            providerCode = n.get("providerCode")?.takeUnless { it.isNull }?.asText(),
            lastKnownRevoked = n.path("lastKnownRevoked").asBoolean(false),
            lastKnownSuspended = n.path("lastKnownSuspended").asBoolean(false),
            serverReachable = n.path("serverReachable").asBoolean(false),
            remainingDays = if (n.hasNonNull("remainingDays")) n.get("remainingDays").asLong() else null,
            clock = ClockSnapshot(wallClock = wall, lastSeenWall = lastSeen),
        )
    }
}
