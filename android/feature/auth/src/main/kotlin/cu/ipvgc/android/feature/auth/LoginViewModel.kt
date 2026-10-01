package cu.ipvgc.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.ipvgc.android.core.common.Outcome
import cu.ipvgc.android.core.data.AuthRepository
import cu.ipvgc.android.core.data.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val mfaCode: String = "",
    val challengeId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun setEmail(v: String) {
        _state.value = _state.value.copy(email = v)
    }

    fun setPassword(v: String) {
        _state.value = _state.value.copy(password = v)
    }

    fun setMfa(v: String) {
        _state.value = _state.value.copy(mfaCode = v)
    }

    fun submit() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val out = auth.login(_state.value.email, _state.value.password)) {
                is Outcome.Err -> _state.value = _state.value.copy(loading = false, error = out.message)
                is Outcome.Ok ->
                    when (val r = out.value) {
                        is LoginResult.Mfa ->
                            _state.value = _state.value.copy(loading = false, challengeId = r.challengeId)
                        is LoginResult.Ready ->
                            _state.value = _state.value.copy(loading = false, done = true)
                    }
            }
        }
    }

    fun submitMfa() {
        val id = _state.value.challengeId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val out = auth.verifyMfa(id, _state.value.mfaCode)) {
                is Outcome.Err -> _state.value = _state.value.copy(loading = false, error = out.message)
                is Outcome.Ok -> _state.value = _state.value.copy(loading = false, done = true)
            }
        }
    }
}
