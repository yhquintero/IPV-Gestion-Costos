package cu.ipvgc.android.core.data

import cu.ipvgc.android.core.common.Outcome
import cu.ipvgc.android.core.network.IpvApi
import cu.ipvgc.android.core.network.LoginRequest
import cu.ipvgc.android.core.network.MeDto
import cu.ipvgc.android.core.network.MfaRequest
import cu.ipvgc.android.core.security.TokenStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: IpvApi,
    private val tokens: TokenStore,
) {
    suspend fun login(email: String, password: String): Outcome<LoginResult> {
        val res = runCatching { api.login(LoginRequest(email.trim().lowercase(), password)) }
            .getOrElse { return Outcome.Err(it.message ?: "network") }
        if (res.code() == 401) return Outcome.Err("Credenciales inválidas", "invalid_credentials")
        val body = res.body() ?: return Outcome.Err("Respuesta vacía")
        if (body.mfa_required) {
            return Outcome.Ok(LoginResult.Mfa(body.challenge_id ?: ""))
        }
        val access = body.access_token ?: return Outcome.Err("Sin token")
        tokens.save(access, body.refresh_token)
        return Outcome.Ok(LoginResult.Ready(body.user))
    }

    suspend fun verifyMfa(challengeId: String, code: String): Outcome<LoginResult> {
        val res = runCatching { api.verifyMfa(MfaRequest(challengeId, code)) }
            .getOrElse { return Outcome.Err(it.message ?: "network") }
        val body = res.body() ?: return Outcome.Err("MFA rechazado", "mfa")
        val access = body.access_token ?: return Outcome.Err("Sin token")
        tokens.save(access, body.refresh_token)
        return Outcome.Ok(LoginResult.Ready(body.user))
    }

    suspend fun me(): Outcome<MeDto> {
        val res = runCatching { api.me() }.getOrElse { return Outcome.Err(it.message ?: "network") }
        if (!res.isSuccessful) return Outcome.Err("Sesión inválida", "unauthenticated")
        return Outcome.Ok(res.body() ?: return Outcome.Err("vacío"))
    }

    suspend fun logout() {
        runCatching { api.logout() }
        tokens.clear()
    }

    fun hasSession(): Boolean = tokens.accessToken() != null
}

sealed class LoginResult {
    data class Mfa(val challengeId: String) : LoginResult()
    data class Ready(val user: MeDto?) : LoginResult()
}
