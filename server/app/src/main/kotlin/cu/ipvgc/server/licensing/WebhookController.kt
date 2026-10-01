package cu.ipvgc.server.licensing

import com.fasterxml.jackson.databind.ObjectMapper
import cu.ipvgc.domain.license.WebhookHmac
import cu.ipvgc.server.web.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/webhooks")
class WebhookController(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    @Value("\${ipvgc.keygen.webhook-secret:fake-webhook-secret}") private val secret: String,
    @Value("\${ipvgc.keygen.mode:FAKE}") private val mode: String,
) {
    @PostMapping("/keygen")
    @Transactional
    fun receive(
        @RequestBody payload: String,
        @RequestHeader("X-Webhook-Signature", required = false) signature: String?,
    ): Map<String, Any?> {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val ok = signature != null && WebhookHmac.verify(bytes, WebhookHmac.utf8(secret), signature)
        if (!ok) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "invalid_signature", "webhook signature mismatch")
        }
        val tree = mapper.readTree(payload)
        val id = tree.path("id").asText(null) ?: throw ApiException.badRequest("missing_id", "event id required")
        val type = tree.path("type").asText("unknown")
        val inserted =
            jdbc.update(
                """
                INSERT INTO keygen_webhook_events (id, type, signature_ok, payload, processed_at)
                VALUES (?, ?, true, ?::jsonb, clock_timestamp())
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                id, type, payload,
            )
        return mapOf(
            "id" to id,
            "type" to type,
            "duplicate" to (inserted == 0),
            "mode" to mode,
        )
    }
}
