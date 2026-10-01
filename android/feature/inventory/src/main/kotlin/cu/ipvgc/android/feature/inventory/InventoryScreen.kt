package cu.ipvgc.android.feature.inventory

import androidx.compose.runtime.Composable
import cu.ipvgc.android.core.designsystem.SimpleList

@Composable
fun InventoryRoute() {
    SimpleList(
        title = "Inventario",
        subtitle = "Conteos y movimientos offline: Fase 6a/6b. Aquí solo hay lectura de saldos cuando el API los publique.",
        rows = emptyList(),
    )
}
