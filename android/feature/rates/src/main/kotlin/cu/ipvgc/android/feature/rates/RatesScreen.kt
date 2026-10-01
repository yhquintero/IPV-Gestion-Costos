package cu.ipvgc.android.feature.rates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.ipvgc.android.core.common.Outcome
import cu.ipvgc.android.core.data.RateRepository
import cu.ipvgc.android.core.designsystem.SimpleList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RatesUi(val rows: List<String> = emptyList(), val error: String? = null)

@HiltViewModel
class RatesViewModel @Inject constructor(private val repo: RateRepository) : ViewModel() {
    private val _state = MutableStateFlow(RatesUi())
    val state: StateFlow<RatesUi> = _state
    init {
        viewModelScope.launch {
            when (val live = repo.current(true)) {
                is Outcome.Ok ->
                    _state.value =
                        RatesUi(
                            live.value.map { "${it.instrument} ${it.value} · ${it.label ?: "Tasa de referencia, no oficial"}" },
                        )
                is Outcome.Err -> _state.value = RatesUi(error = live.message)
            }
        }
    }
}

@Composable
fun RatesRoute(vm: RatesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    SimpleList(
        title = "Tasas",
        subtitle = "Tasa de referencia, no oficial. Fuente: elTOQUE o DATOS DE PRUEBA. Nunca tasa oficial.",
        error = s.error,
        rows = s.rows,
    )
}
