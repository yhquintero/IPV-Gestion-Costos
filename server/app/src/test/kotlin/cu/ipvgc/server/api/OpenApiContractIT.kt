package cu.ipvgc.server.api

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class OpenApiContractIT {
    @Test
    fun `contract describes the vertical slice`() {
        val yaml = ClassPathResource("openapi/ipv-gc.yaml").inputStream.use { it.reader().readText() }
        yaml shouldContain "/auth/login"
        yaml shouldContain "/cost-sheets/{sheetId}/versions/{versionId}/activate"
        yaml shouldContain "/ipv-controls"
        yaml shouldContain "/audit/verify"
        yaml shouldContain "Idempotency-Key"
        yaml shouldContain "If-Match"
    }
}
