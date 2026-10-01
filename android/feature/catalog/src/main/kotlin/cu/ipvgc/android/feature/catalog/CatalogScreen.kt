package cu.ipvgc.android.feature.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import cu.ipvgc.android.core.designsystem.SimpleList

@Composable
fun CatalogRoute(vm: CatalogViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    SimpleList(title = "Catálogo", subtitle = "Solo lectura en Fase 5. Edición en línea vía API.", error = s.error, rows = s.rows)
}
