package cu.ipvgc.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import cu.ipvgc.android.core.security.BiometricGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(val biometric: BiometricGate) : ViewModel()

@Composable
fun SettingsRoute(vm: SettingsViewModel = hiltViewModel()) {
    var on by remember { mutableStateOf(vm.biometric.isEnabled()) }
    Column(Modifier.padding(16.dp)) {
        Text("Ajustes")
        Text("Desbloqueo biométrico / PIN (Keystore). allowBackup=false.")
        Switch(checked = on, onCheckedChange = {
            on = it
            vm.biometric.setEnabled(it)
        })
        Text("Zona horaria de negocio: America/Havana")
    }
}
