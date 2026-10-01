package cu.ipvgc.android.feature.costing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.ipvgc.android.core.common.Outcome
import cu.ipvgc.android.core.data.CostSheetRepository
import cu.ipvgc.android.core.designsystem.SimpleList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SheetsUi(val rows: List<String> = emptyList(), val error: String? = null)

@HiltViewModel
class CostSheetsViewModel @Inject constructor(private val repo: CostSheetRepository) : ViewModel() {
    private val _state = MutableStateFlow(SheetsUi())
    val state: StateFlow<SheetsUi> = _state
    init {
        viewModelScope.launch {
            when (val live = repo.list(true)) {
                is Outcome.Ok -> _state.value = SheetsUi(live.value.map { "${it.code} · ${it.status ?: "—"} · ${it.totalCost ?: "—"}" })
                is Outcome.Err -> _state.value = SheetsUi(error = live.message)
            }
        }
    }
}

@Composable
fun CostSheetsRoute(vm: CostSheetsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    SimpleList(
        title = "Fichas",
        subtitle = "Transiciones (enviar/validar/aprobar/activar) solo en línea. No hay outbox en Fase 5.",
        error = s.error,
        rows = s.rows,
    )
}
