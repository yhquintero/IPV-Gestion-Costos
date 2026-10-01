package cu.ipvgc.domain.crypto

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * TOTP RFC 6238 (HMAC-SHA1, 30s, 6 dígitos). Sin dependencias de framework.
 */
object Totp {
    fun generate(secret: ByteArray, unixSeconds: Long, stepSeconds: Long = 30, digits: Int = 6): String {
        val counter = unixSeconds / stepSeconds
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val otp = binary % 10.0.pow(digits).toInt()
        return otp.toString().padStart(digits, '0')
    }

    fun matches(secret: ByteArray, code: String, unixSeconds: Long, skewSteps: Int = 1): Boolean {
        val expected = code.trim()
        return (-skewSteps..skewSteps).any { delta ->
            generate(secret, unixSeconds + delta * 30L) == expected
        }
    }
}
