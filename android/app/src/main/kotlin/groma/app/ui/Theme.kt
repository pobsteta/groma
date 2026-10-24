package groma.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same palette as Marculus, so both field apps look alike.
private val VertMarculus = Color(0xFF374742)
private val VertVif = Color(0xFF3E7A40)
private val Creme = Color(0xFFF5EBD8)

private val LightColors = lightColorScheme(
    primary = VertVif,
    onPrimary = Color.White,
    primaryContainer = VertMarculus,
    onPrimaryContainer = Creme,
    secondary = VertMarculus,
)

private val DarkColors = darkColorScheme(
    primary = VertVif,
    onPrimary = Color.White,
    primaryContainer = VertMarculus,
    onPrimaryContainer = Creme,
    secondary = Creme,
)

@Composable
fun GromaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
