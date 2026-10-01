package cu.ipvgc.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Forest = Color(0xFF1C4D36)
private val Sand = Color(0xFFF3EEE4)
private val Paper = Color(0xFFFBFAF6)
private val Ink = Color(0xFF13241C)
private val Gold = Color(0xFFB8893A)

private val colors =
    lightColorScheme(
        primary = Forest,
        onPrimary = Sand,
        secondary = Gold,
        background = Paper,
        surface = Sand,
        onBackground = Ink,
        onSurface = Ink,
    )

@Composable
fun IpvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
