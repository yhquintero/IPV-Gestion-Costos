package cu.ipvgc.android.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SimpleList(
    title: String,
    subtitle: String? = null,
    error: String? = null,
    rows: List<String>,
) {
    Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        subtitle?.let { Text(it) }
        error?.let { Text(it) }
        LazyColumn {
            items(rows) { row ->
                Text(row, modifier = Modifier.padding(vertical = 8.dp))
                HorizontalDivider()
            }
        }
    }
}
