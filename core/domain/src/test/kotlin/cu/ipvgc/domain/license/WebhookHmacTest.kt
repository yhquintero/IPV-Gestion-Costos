package cu.ipvgc.domain.license

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebhookHmacTest {
    @Test
    fun `accepts matching hex hmac`() {
        val payload = WebhookHmac.utf8("""{"id":"evt-1"}""")
        val secret = WebhookHmac.utf8("fake-webhook-secret")
        val sig = WebhookHmac.sign(payload, secret)
        assertTrue(WebhookHmac.verify(payload, secret, sig))
        assertFalse(WebhookHmac.verify(payload, secret, "00$sig".take(sig.length)))
    }
}
