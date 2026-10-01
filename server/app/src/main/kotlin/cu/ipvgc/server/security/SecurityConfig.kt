package cu.ipvgc.server.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import cu.ipvgc.server.web.IdempotencyFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val idempotencyFilter: IdempotencyFilter,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/mfa", "/api/v1/auth/refresh")
                    .permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/v1/openapi.yaml", "/actuator/health").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/webhooks/keygen").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(idempotencyFilter, JwtAuthFilter::class.java)
        return http.build()
    }

    @Bean
    fun disableDuplicateIdempotency(filter: IdempotencyFilter): FilterRegistrationBean<IdempotencyFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }
}
