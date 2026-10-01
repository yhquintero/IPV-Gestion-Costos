package cu.ipvgc.domain.security

import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/** Límite progresivo de login por clave (IP o correo). A-01 / ASVS V2.2. */
class LoginThrottle(
    private val maxPerWindow: Int = 10,
    private val window: Duration = Duration.ofMinutes(15),
    private val clock: () -> Instant = { Instant.now() },
) {
    private val hits = ConcurrentHashMap<String, ArrayDeque<Instant>>()

    fun allow(key: String): Boolean {
        val now = clock()
        val q = hits.getOrPut(key) { ArrayDeque() }
        synchronized(q) {
            while (q.isNotEmpty() && Duration.between(q.first(), now) > window) q.removeFirst()
            if (q.size >= maxPerWindow) return false
            q.addLast(now)
            return true
        }
    }
}

object OutboundAllowlist {
    fun elToqueHost(host: String?): Boolean = host == "tasas.eltoque.com"

    fun keygenHost(host: String?): Boolean =
        host == "api.keygen.sh" || (host != null && host.endsWith(".keygen.sh"))
}

object ApiSecurityHeaders {
    val ALL: Map<String, String> =
        mapOf(
            "X-Content-Type-Options" to "nosniff",
            "X-Frame-Options" to "DENY",
            "Referrer-Policy" to "strict-origin-when-cross-origin",
            "Cache-Control" to "no-store",
            "X-DNS-Prefetch-Control" to "off",
        )
}
