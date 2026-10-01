package cu.ipvgc.android.feature.license

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import cu.ipvgc.android.core.designsystem.SimpleList
import cu.ipvgc.domain.license.ClockSnapshot
import cu.ipvgc.domain.license.LicenseEvaluationInput
import cu.ipvgc.domain.license.LicenseEvaluator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class LicenseViewModel @Inject constructor() : ViewModel() {
    private val status = LicenseEvaluator.evaluate(
        LicenseEvaluationInput(
            hasFile = false,
            serverReachable = false,
            clock = ClockSnapshot(wallClock = Instant.now()),
        ),
    )
    val state: StateFlow<String> = MutableStateFlow(status.name)
}

@Composable
fun LicenseRoute(vm: LicenseViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    SimpleList(
        title = "Licencia",
        subtitle = "El estado se recalcula; no hay bandera premium. Archivos dorados en core:domain. Proveedor Keygen: Fase 7.",
        rows = listOf("Estado: $s"),
    )
}
