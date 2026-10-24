package groma.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import fr.nemeton.terrain.style.Schema
import fr.nemeton.terrain.style.StyleTerrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Groma's light and dark ColorScheme must equal, role by role, the tokens of
 * terrain-style (StyleTerrain.clair / StyleTerrain.sombre). Contract: Theme.kt
 * exposes `internal val SchemaClair` and `internal val SchemaSombre`.
 * reussi, alerte and interditsEnTexte are not ColorScheme roles: out of scope.
 */
class ThemeTerrainStyleTest {

    private class Role(
        val nom: String,
        val schema: (Schema) -> Long,
        val couleur: (ColorScheme) -> Color,
    )

    private val roles = listOf(
        Role("primary", { it.primary }, { it.primary }),
        Role("onPrimary", { it.onPrimary }, { it.onPrimary }),
        Role("primaryContainer", { it.primaryContainer }, { it.primaryContainer }),
        Role("onPrimaryContainer", { it.onPrimaryContainer }, { it.onPrimaryContainer }),
        Role("inversePrimary", { it.inversePrimary }, { it.inversePrimary }),
        Role("secondary", { it.secondary }, { it.secondary }),
        Role("onSecondary", { it.onSecondary }, { it.onSecondary }),
        Role("secondaryContainer", { it.secondaryContainer }, { it.secondaryContainer }),
        Role("onSecondaryContainer", { it.onSecondaryContainer }, { it.onSecondaryContainer }),
        Role("tertiary", { it.tertiary }, { it.tertiary }),
        Role("onTertiary", { it.onTertiary }, { it.onTertiary }),
        Role("tertiaryContainer", { it.tertiaryContainer }, { it.tertiaryContainer }),
        Role("onTertiaryContainer", { it.onTertiaryContainer }, { it.onTertiaryContainer }),
        Role("background", { it.background }, { it.background }),
        Role("onBackground", { it.onBackground }, { it.onBackground }),
        Role("surface", { it.surface }, { it.surface }),
        Role("onSurface", { it.onSurface }, { it.onSurface }),
        Role("surfaceVariant", { it.surfaceVariant }, { it.surfaceVariant }),
        Role("onSurfaceVariant", { it.onSurfaceVariant }, { it.onSurfaceVariant }),
        Role("surfaceTint", { it.surfaceTint }, { it.surfaceTint }),
        Role("inverseSurface", { it.inverseSurface }, { it.inverseSurface }),
        Role("inverseOnSurface", { it.inverseOnSurface }, { it.inverseOnSurface }),
        Role("error", { it.error }, { it.error }),
        Role("onError", { it.onError }, { it.onError }),
        Role("errorContainer", { it.errorContainer }, { it.errorContainer }),
        Role("onErrorContainer", { it.onErrorContainer }, { it.onErrorContainer }),
        Role("outline", { it.outline }, { it.outline }),
        Role("outlineVariant", { it.outlineVariant }, { it.outlineVariant }),
        Role("scrim", { it.scrim }, { it.scrim }),
        Role("surfaceBright", { it.surfaceBright }, { it.surfaceBright }),
        Role("surfaceDim", { it.surfaceDim }, { it.surfaceDim }),
        Role("surfaceContainer", { it.surfaceContainer }, { it.surfaceContainer }),
        Role("surfaceContainerHigh", { it.surfaceContainerHigh }, { it.surfaceContainerHigh }),
        Role("surfaceContainerHighest", { it.surfaceContainerHighest }, { it.surfaceContainerHighest }),
        Role("surfaceContainerLow", { it.surfaceContainerLow }, { it.surfaceContainerLow }),
        Role("surfaceContainerLowest", { it.surfaceContainerLowest }, { it.surfaceContainerLowest }),
        Role("primaryFixed", { it.primaryFixed }, { it.primaryFixed }),
        Role("primaryFixedDim", { it.primaryFixedDim }, { it.primaryFixedDim }),
        Role("onPrimaryFixed", { it.onPrimaryFixed }, { it.onPrimaryFixed }),
        Role("onPrimaryFixedVariant", { it.onPrimaryFixedVariant }, { it.onPrimaryFixedVariant }),
        Role("secondaryFixed", { it.secondaryFixed }, { it.secondaryFixed }),
        Role("secondaryFixedDim", { it.secondaryFixedDim }, { it.secondaryFixedDim }),
        Role("onSecondaryFixed", { it.onSecondaryFixed }, { it.onSecondaryFixed }),
        Role("onSecondaryFixedVariant", { it.onSecondaryFixedVariant }, { it.onSecondaryFixedVariant }),
        Role("tertiaryFixed", { it.tertiaryFixed }, { it.tertiaryFixed }),
        Role("tertiaryFixedDim", { it.tertiaryFixedDim }, { it.tertiaryFixedDim }),
        Role("onTertiaryFixed", { it.onTertiaryFixed }, { it.onTertiaryFixed }),
        Role("onTertiaryFixedVariant", { it.onTertiaryFixedVariant }, { it.onTertiaryFixedVariant }),
    )

    /** Reflection guard: Schema must have exactly these 48 roles plus the 3 non-role fields. */
    @Test
    fun `le tableau des roles couvre les 48 champs de Schema`() {
        assertEquals(48, roles.size)
        val horsRole = setOf("reussi", "alerte", "interditsEnTexte")
        val champs = Schema::class.java.declaredFields
            .map { it.name }
            .filter { it !in horsRole && !it.startsWith("$") }
            .toSet()
        assertEquals(roles.map { it.nom }.toSet(), champs, "roles de Schema non couverts ou en trop")
    }

    private fun ecarts(attendu: Schema, obtenu: ColorScheme): List<String> =
        roles.mapNotNull { r ->
            val a = Color(r.schema(attendu))
            val o = r.couleur(obtenu)
            if (a == o) null else "${r.nom} : attendu $a, obtenu $o"
        }

    @Test
    fun `SchemaClair egale StyleTerrain clair sur les 48 roles`() {
        val e = ecarts(StyleTerrain.clair, SchemaClair)
        assertTrue(e.isEmpty(), "roles fautifs (clair) :\n" + e.joinToString("\n"))
    }

    @Test
    fun `SchemaSombre egale StyleTerrain sombre sur les 48 roles`() {
        val e = ecarts(StyleTerrain.sombre, SchemaSombre)
        assertTrue(e.isEmpty(), "roles fautifs (sombre) :\n" + e.joinToString("\n"))
    }
}
