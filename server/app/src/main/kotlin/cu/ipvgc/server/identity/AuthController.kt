package cu.ipvgc.server.identity

import cu.ipvgc.domain.security.LoginThrottle
import cu.ipvgc.server.security.currentUser
import cu.ipvgc.server.web.ApiException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class LoginBody(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
    val organization_id: UUID? = null,
)

data class RefreshBody(@field:NotBlank val refresh_token: String)
data class MfaBody(@field:NotBlank val mfa_token: String, @field:NotBlank val code: String)
data class LogoutBody(val refresh_token: String? = null)

@RestController
@RequestMapping("/api/v1")
class AuthController(
    private val auth: AuthService,
    private val throttle: LoginThrottle,
) {
    @PostMapping("/auth/login")
    fun login(
        @Valid @RequestBody body: LoginBody,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val ip = request.remoteAddr ?: "unknown"
        if (!throttle.allow("ip:$ip") || !throttle.allow("email:${body.email.trim().lowercase()}")) {
            throw ApiException.tooManyRequests()
        }
        return ResponseEntity.ok(auth.login(LoginRequest(body.email, body.password, body.organization_id)))
    }

    @PostMapping("/auth/mfa")
    fun mfa(@Valid @RequestBody body: MfaBody): TokenResponse =
        auth.completeMfa(MfaRequest(body.mfa_token, body.code))

    @PostMapping("/auth/refresh")
    fun refresh(@Valid @RequestBody body: RefreshBody): TokenResponse =
        auth.refresh(RefreshRequest(body.refresh_token))

    @PostMapping("/auth/logout")
    fun logout(@RequestBody(required = false) body: LogoutBody?): ResponseEntity<Void> {
        auth.logout(body?.refresh_token)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(): Map<String, Any?> {
        val u = currentUser()
        return mapOf(
            "id" to u.userId,
            "organization_id" to u.organizationId,
            "email" to u.email,
            "display_name" to u.displayName,
            "roles" to u.roles,
            "permissions" to u.permissions,
        )
    }
}
