package cu.ipvgc.android.feature.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.ipvgc.android.core.data.sync.OutboxRepository
import cu.ipvgc.android.core.designsystem.SimpleList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class InventoryUi(val rows: List<String> = emptyList(), val pending: Int = 0)

@HiltViewModel
class InventoryViewModel @Inject constructor(private val outbox: OutboxRepository) : ViewModel() {
    private val _state = MutableStateFlow(InventoryUi())
    val state: StateFlow<InventoryUi> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val rows = outbox.counts().map { "${it.itemCode} · ${it.observedQty} · ${it.syncState}" }
            _state.value = InventoryUi(rows, outbox.pendingCount())
        }
    }

    fun addCount() {
        viewModelScope.launch {
            outbox.enqueueCount("HARINA", "2.00", LocalDate.now().toString())
            refresh()
        }
    }
}

@Composable
fun InventoryRoute(vm: InventoryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    Column(Modifier.padding(8.dp)) {
        SimpleList(
            title = "Inventario · conteos (6a)",
            subtitle = "Datos al dispositivo · ${s.pending} cambios pendientes. Los conteos se anexan; no se fusionan.",
            rows = s.rows,
        )
        Button(onClick = vm::addCount, modifier = Modifier.padding(16.dp)) {
            Text("Registrar conteo offline")
        }
    }
}
