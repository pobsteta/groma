package groma.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.nemeton.terrain.style.Schema
import fr.nemeton.terrain.style.StyleTerrain

/**
 * Copies the 48 roles of a terrain-style [Schema] one for one. Every role is given, so the
 * light/dark defaults of Material never apply; one function serves both schemes.
 */
private fun versColorScheme(s: Schema): ColorScheme = lightColorScheme(
        primary = Color(s.primary),
        onPrimary = Color(s.onPrimary),
        primaryContainer = Color(s.primaryContainer),
        onPrimaryContainer = Color(s.onPrimaryContainer),
        inversePrimary = Color(s.inversePrimary),
        secondary = Color(s.secondary),
        onSecondary = Color(s.onSecondary),
        secondaryContainer = Color(s.secondaryContainer),
        onSecondaryContainer = Color(s.onSecondaryContainer),
        tertiary = Color(s.tertiary),
        onTertiary = Color(s.onTertiary),
        tertiaryContainer = Color(s.tertiaryContainer),
        onTertiaryContainer = Color(s.onTertiaryContainer),
        background = Color(s.background),
        onBackground = Color(s.onBackground),
        surface = Color(s.surface),
        onSurface = Color(s.onSurface),
        surfaceVariant = Color(s.surfaceVariant),
        onSurfaceVariant = Color(s.onSurfaceVariant),
        surfaceTint = Color(s.surfaceTint),
        inverseSurface = Color(s.inverseSurface),
        inverseOnSurface = Color(s.inverseOnSurface),
        error = Color(s.error),
        onError = Color(s.onError),
        errorContainer = Color(s.errorContainer),
        onErrorContainer = Color(s.onErrorContainer),
        outline = Color(s.outline),
        outlineVariant = Color(s.outlineVariant),
        scrim = Color(s.scrim),
        surfaceBright = Color(s.surfaceBright),
        surfaceDim = Color(s.surfaceDim),
        surfaceContainer = Color(s.surfaceContainer),
        surfaceContainerHigh = Color(s.surfaceContainerHigh),
        surfaceContainerHighest = Color(s.surfaceContainerHighest),
        surfaceContainerLow = Color(s.surfaceContainerLow),
        surfaceContainerLowest = Color(s.surfaceContainerLowest),
        primaryFixed = Color(s.primaryFixed),
        primaryFixedDim = Color(s.primaryFixedDim),
        onPrimaryFixed = Color(s.onPrimaryFixed),
        onPrimaryFixedVariant = Color(s.onPrimaryFixedVariant),
        secondaryFixed = Color(s.secondaryFixed),
        secondaryFixedDim = Color(s.secondaryFixedDim),
        onSecondaryFixed = Color(s.onSecondaryFixed),
        onSecondaryFixedVariant = Color(s.onSecondaryFixedVariant),
        tertiaryFixed = Color(s.tertiaryFixed),
        tertiaryFixedDim = Color(s.tertiaryFixedDim),
        onTertiaryFixed = Color(s.onTertiaryFixed),
        onTertiaryFixedVariant = Color(s.onTertiaryFixedVariant),
)

internal val SchemaClair: ColorScheme = versColorScheme(StyleTerrain.clair)

internal val SchemaSombre: ColorScheme = versColorScheme(StyleTerrain.sombre)

@Composable
fun GromaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) SchemaSombre else SchemaClair, content = content)
}
