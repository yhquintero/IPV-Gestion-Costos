package cu.ipvgc.domain.hash

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

object ContentHash {
    fun sha256Hex(canonicalJson: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    fun sha256Bytes(canonicalJson: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson.toByteArray(StandardCharsets.UTF_8))
}
