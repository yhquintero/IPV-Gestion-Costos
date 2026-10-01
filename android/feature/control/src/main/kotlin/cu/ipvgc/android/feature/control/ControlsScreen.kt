package cu.ipvgc.android.feature.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.ipvgc.android.core.common.Outcome
import cu.ipvgc.android.core.data.ControlRepository
import cu.ipvgc.android.core.designsystem.SimpleList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControlsUi(val rows: List<String> = emptyList(), val error: String? = null)

@HiltViewModel
class ControlsViewModel @Inject constructor(private val repo: ControlRepository) : ViewModel() {
    private val _state = MutableStateFlow(ControlsUi())
    val state: StateFlow<ControlsUi> = _state
    init {
        viewModelScope.launch {
            when (val live = repo.list(true)) {
                is Outcome.Ok -> _state.value = ControlsUi(live.value.map { "${it.controlNo ?: it.id} · ${it.status}" })
                is Outcome.Err -> _state.value = ControlsUi(error = live.message)
            }
        }
    }
}

@Composable
fun ControlsRoute(vm: ControlsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    SimpleList(title = "Controles IPV", subtitle = "El número lo asigna el servidor (I-19).", error = s.error, rows = s.rows)
}
