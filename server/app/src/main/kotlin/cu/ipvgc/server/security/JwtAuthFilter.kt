package cu.ipvgc.server.security

import cu.ipvgc.server.access.AccessService
import cu.ipvgc.server.access.CurrentUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.filter.OncePerRequestFilter
import java.sql.PreparedStatement
import java.util.UUID

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val accessService: AccessService,
    private val jdbc: JdbcTemplate,
    tm: PlatformTransactionManager,
) : OncePerRequestFilter() {
    private val tx = TransactionTemplate(tm)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }
        val claims = try {
            jwtService.parse(header.removePrefix("Bearer ").trim())
        } catch (_: Exception) {
            response.status = 401
            response.contentType = "application/problem+json"
            response.writer.write("""{"code":"invalid_credentials","status":401,"detail":"invalid token"}""")
            return
        }
        tx.execute {
            jdbc.execute("SELECT set_config('app.organization_id', ?, true)") { ps: PreparedStatement ->
                ps.setString(1, claims.organizationId.toString())
                ps.execute()
            }
            val (roles, perms) = accessService.permissionsFor(claims.userId)
            val display = jdbc.query(
                "SELECT display_name FROM users WHERE id = ?",
                { rs, _ -> rs.getString(1) },
                claims.userId,
            ).firstOrNull() ?: ""
            val user = CurrentUser(
                userId = claims.userId,
                organizationId = claims.organizationId,
                email = claims.email,
                displayName = display,
                roles = roles,
                permissions = perms,
            )
            val auth = UsernamePasswordAuthenticationToken(
                user,
                null,
                perms.map { SimpleGrantedAuthority("PERM_$it") },
            )
            SecurityContextHolder.getContext().authentication = auth
            filterChain.doFilter(request, response)
        }
    }
}

fun currentUser(): CurrentUser =
    SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
        ?: throw cu.ipvgc.server.web.ApiException.unauthorized()

fun requireUserId(): UUID = currentUser().userId
