package cu.ipvgc.server.web

import cu.ipvgc.domain.hash.ContentHash
import cu.ipvgc.server.access.CurrentUser
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import java.io.ByteArrayInputStream

@Component
class IdempotencyFilter(private val jdbc: JdbcTemplate) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (request.method != "POST" && request.method != "PATCH") return true
        if (request.getHeader("Idempotency-Key").isNullOrBlank()) return true
        return request.requestURI.startsWith("/api/v1/auth/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val key = request.getHeader("Idempotency-Key")
        val cached = request.inputStream.readAllBytes()
        val hash = ContentHash.sha256Hex(request.method + request.requestURI + String(cached))
        val user = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
        if (user != null) {
            val existing = jdbc.query(
                "SELECT request_hash, response_status, response_body FROM idempotency_keys WHERE organization_id = ? AND key = ?",
                { rs, _ -> Triple(rs.getString(1), rs.getInt(2), rs.getString(3)) },
                user.organizationId,
                key,
            ).firstOrNull()
            if (existing != null) {
                if (existing.first != hash) {
                    response.status = 409
                    response.contentType = "application/problem+json"
                    response.writer.write("""{"code":"idempotency_conflict","status":409,"detail":"Idempotency-Key reused with a different body"}""")
                    return
                }
                response.status = existing.second
                response.contentType = "application/json"
                response.writer.write(existing.third)
                return
            }
        }
        val replay = CachedBodyRequest(request, cached)
        val wrappedRes = ContentCachingResponseWrapper(response)
        filterChain.doFilter(replay, wrappedRes)
        if (user != null && wrappedRes.status in 200..299) {
            val responseBody = String(wrappedRes.contentAsByteArray).ifBlank { "{}" }
            jdbc.update(
                """
                INSERT INTO idempotency_keys (organization_id, key, method, path, request_hash, response_status, response_body)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                user.organizationId, key, request.method, request.requestURI, hash, wrappedRes.status, responseBody,
            )
        }
        wrappedRes.copyBodyToResponse()
    }

    private class CachedBodyRequest(request: HttpServletRequest, private val cached: ByteArray) :
        HttpServletRequestWrapper(request) {
        override fun getInputStream(): ServletInputStream {
            val inner = ByteArrayInputStream(cached)
            return object : ServletInputStream() {
                override fun read(): Int = inner.read()
                override fun isFinished(): Boolean = inner.available() == 0
                override fun isReady(): Boolean = true
                override fun setReadListener(listener: ReadListener?) {}
            }
        }
    }
}
