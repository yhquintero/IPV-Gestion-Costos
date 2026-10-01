package cu.ipvgc.android.feature.home

import androidx.compose.runtime.Composable
import cu.ipvgc.android.core.designsystem.SimpleList

@Composable
fun HomeRoute() {
    SimpleList(
        title = "Inicio",
        subtitle = "Catálogo, valores IPV, fichas y controles se leen en línea y quedan en Room+SQLCipher. Las transiciones de ficha no se encolan (Fase 6).",
        rows = listOf("Catálogo", "Valores IPV", "Fichas", "Controles IPV", "Tasas", "Licencia"),
    )
}
