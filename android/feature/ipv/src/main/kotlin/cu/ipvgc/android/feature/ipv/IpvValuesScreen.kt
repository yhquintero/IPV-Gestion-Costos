package cu.ipvgc.android.feature.ipv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.ipvgc.android.core.common.Outcome
import cu.ipvgc.android.core.data.IpvValueRepository
import cu.ipvgc.android.core.designsystem.SimpleList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IpvUi(val rows: List<String> = emptyList(), val error: String? = null)

@HiltViewModel
class IpvValuesViewModel @Inject constructor(private val repo: IpvValueRepository) : ViewModel() {
    private val _state = MutableStateFlow(IpvUi())
    val state: StateFlow<IpvUi> = _state
    init {
        viewModelScope.launch {
            when (val live = repo.list(true)) {
                is Outcome.Ok -> _state.value = IpvUi(live.value.map { "${it.currency} ${it.unitPrice} · ${it.sourceRef ?: ""}" })
                is Outcome.Err -> _state.value = IpvUi(error = live.message)
            }
        }
    }
}

@Composable
fun IpvValuesRoute(vm: IpvValuesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    SimpleList(title = "Valores IPV", subtitle = "Registro previo a la ficha. costs:view.", error = s.error, rows = s.rows)
}
