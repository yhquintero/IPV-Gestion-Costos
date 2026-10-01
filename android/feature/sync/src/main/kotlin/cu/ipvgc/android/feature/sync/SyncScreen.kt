package cu.ipvgc.android.feature.sync

import androidx.compose.runtime.Composable
import cu.ipvgc.android.core.designsystem.SimpleList

@Composable
fun SyncRoute() {
    SimpleList(
        title = "Sincronización",
        subtitle = "Fase 5: pull de lectura. Outbox, conflictos y RESYNC_REQUIRED son Fase 6.",
        rows = listOf("Último pull: al abrir cada pantalla o WorkManager (Wi-Fi)."),
    )
}
