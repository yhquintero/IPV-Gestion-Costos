package cu.ipvgc.android.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginRoute(
    onLoggedIn: () -> Unit,
    vm: LoginViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) {
        if (state.done) onLoggedIn()
    }
    LoginScreen(
        state = state,
        onEmail = vm::setEmail,
        onPassword = vm::setPassword,
        onMfa = vm::setMfa,
        onSubmit = { if (state.challengeId == null) vm.submit() else vm.submitMfa() },
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onMfa: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(Modifier.padding(24.dp)) {
        Text("IPV Gestión de Costos")
        Spacer(Modifier.height(16.dp))
        if (state.challengeId == null) {
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmail,
                label = { Text("Correo") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPassword,
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Código TOTP")
            OutlinedTextField(
                value = state.mfaCode,
                onValueChange = onMfa,
                label = { Text("MFA") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.error?.let { Text(it) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSubmit, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.loading) "Entrando…" else "Entrar")
        }
        Text("La sesión usa un token corto en Keystore. No hay secretos de elTOQUE ni Keygen en el dispositivo.")
    }
}
