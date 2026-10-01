package cu.ipvgc.domain.license

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WebhookHmac {
    fun sign(payload: ByteArray, secret: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(payload).joinToString("") { "%02x".format(it) }
    }

    fun verify(payload: ByteArray, secret: ByteArray, header: String): Boolean {
        val expected = sign(payload, secret)
        if (header.length != expected.length) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].code xor header[i].code)
        }
        return diff == 0
    }

    fun utf8(s: String): ByteArray = s.toByteArray(StandardCharsets.UTF_8)
}
