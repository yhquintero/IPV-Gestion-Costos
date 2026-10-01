package cu.ipvgc.server.security

import cu.ipvgc.domain.security.ApiSecurityHeaders
import cu.ipvgc.domain.security.LoginThrottle
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
class HardeningConfig {
    @Bean
    fun loginThrottle(): LoginThrottle = LoginThrottle()
}

@Component
class ApiSecurityHeadersFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        ApiSecurityHeaders.ALL.forEach { (k, v) -> response.setHeader(k, v) }
        filterChain.doFilter(request, response)
    }
}
