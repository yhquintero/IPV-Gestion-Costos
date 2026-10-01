package cu.ipvgc.android.feature.sync

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
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cu.ipvgc.android.core.data.sync.OutboxRepository
import cu.ipvgc.android.core.data.sync.SyncWorker
import cu.ipvgc.android.core.designsystem.SimpleList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUi(val pending: Int = 0, val conflicts: List<String> = emptyList())

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val outbox: OutboxRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(SyncUi())
    val state: StateFlow<SyncUi> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value =
                SyncUi(
                    pending = outbox.pendingCount(),
                    conflicts = outbox.conflicts().map { "${it.entityType} ${it.mutationId.take(8)} · ${it.resultCode}" },
                )
        }
    }

    fun syncNow() {
        val req =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(context).enqueue(req)
        refresh()
    }
}

@Composable
fun SyncRoute(vm: SyncViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    Column {
        SimpleList(
            title = "Sincronización",
            subtitle = "Datos al dispositivo · ${s.pending} cambios pendientes. Push antes que pull. RESYNC_REQUIRED conserva la outbox.",
            rows = if (s.conflicts.isEmpty()) listOf("Sin conflictos abiertos") else s.conflicts,
        )
        Button(onClick = vm::syncNow, modifier = Modifier.padding(16.dp)) {
            Text("Sincronizar ahora")
        }
        Text("Centro de conflictos: conservar la mía / usar servidor / duplicar (doc 9.8).", modifier = Modifier.padding(16.dp))
    }
}
