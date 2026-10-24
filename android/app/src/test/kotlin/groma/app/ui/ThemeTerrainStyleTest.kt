package groma.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import fr.nemeton.terrain.style.FormesTerrain
import fr.nemeton.terrain.style.Schema
import fr.nemeton.terrain.style.StyleTerrain
import fr.nemeton.terrain.style.StyleTexte
import fr.nemeton.terrain.style.TypoTerrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Groma's theme must equal terrain-style v0.7.1 (Marculus style, ADR 0006 of terrain):
 * light and dark ColorScheme role by role, Shapes, Typography. Contract: Theme.kt exposes
 * `internal val SchemaClair`, `SchemaSombre` (ColorScheme), `FormesGroma` (Shapes) and
 * `TypoGroma` (Typography). Expected values are read from terrain-style, never copied here.
 * reussi, alerte and interditsEnTexte are not ColorScheme roles: out of scope.
 * Buttons (BoutonsTerrain) are not tested: Groma sets no button, its values are those of
 * Material 3 1.4.0, as BoutonsTerrain's are.
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

    /**
     * Inverse guard: a Compose BOM bump that adds a ColorScheme role would leave it at
     * its light default, even in the dark theme, with no test seeing it.
     */
    @Test
    fun `ColorScheme n a pas de role que le tableau ignore`() {
        val roleMaterial = ColorScheme::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE }
            .filter { it.name.startsWith("get") && '-' in it.name && '$' !in it.name }
            .map { it.name.removePrefix("get").substringBefore('-').replaceFirstChar { c -> c.lowercase() } }
            .toSet()
        val attendus = roles.map { it.nom }.toSet()
        val enTrop = (roleMaterial - attendus).sorted()
        val enMoins = (attendus - roleMaterial).sorted()
        assertTrue(
            enTrop.isEmpty() && enMoins.isEmpty(),
            "roles de ColorScheme (Material 3) sans equivalent teste : $enTrop ; " +
                "roles testes absents de ColorScheme : $enMoins. " +
                "A faire : monter terrain-style, ou demander le role a terrain.",
        )
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

    // ---- Shapes ----

    @Test
    fun `FormesGroma egale FormesTerrain sur les 5 formes`() {
        val formes = listOf(
            Triple("extraSmall", FormesTerrain.extraSmall, FormesGroma.extraSmall),
            Triple("small", FormesTerrain.small, FormesGroma.small),
            Triple("medium", FormesTerrain.medium, FormesGroma.medium),
            Triple("large", FormesTerrain.large, FormesGroma.large),
            Triple("extraLarge", FormesTerrain.extraLarge, FormesGroma.extraLarge),
        )
        val e = formes.mapNotNull { (nom, rayon, obtenu) ->
            if (RoundedCornerShape(rayon.dp) == obtenu) null else "$nom : attendu $rayon dp, obtenu $obtenu"
        }
        assertTrue(e.isEmpty(), "formes fautives :\n" + e.joinToString("\n"))
    }

    // ---- Typography ----

    private class Style(val nom: String, val terrain: StyleTexte, val groma: (Typography) -> TextStyle)

    private val styles = listOf(
        Style("displayLarge", TypoTerrain.displayLarge) { it.displayLarge },
        Style("displayMedium", TypoTerrain.displayMedium) { it.displayMedium },
        Style("displaySmall", TypoTerrain.displaySmall) { it.displaySmall },
        Style("headlineLarge", TypoTerrain.headlineLarge) { it.headlineLarge },
        Style("headlineMedium", TypoTerrain.headlineMedium) { it.headlineMedium },
        Style("headlineSmall", TypoTerrain.headlineSmall) { it.headlineSmall },
        Style("titleLarge", TypoTerrain.titleLarge) { it.titleLarge },
        Style("titleMedium", TypoTerrain.titleMedium) { it.titleMedium },
        Style("titleSmall", TypoTerrain.titleSmall) { it.titleSmall },
        Style("bodyLarge", TypoTerrain.bodyLarge) { it.bodyLarge },
        Style("bodyMedium", TypoTerrain.bodyMedium) { it.bodyMedium },
        Style("bodySmall", TypoTerrain.bodySmall) { it.bodySmall },
        Style("labelLarge", TypoTerrain.labelLarge) { it.labelLarge },
        Style("labelMedium", TypoTerrain.labelMedium) { it.labelMedium },
        Style("labelSmall", TypoTerrain.labelSmall) { it.labelSmall },
    )

    /** Reflection guard: the 15 names cover every StyleTexte field of TypoTerrain, none more. */
    @Test
    fun `le tableau des styles couvre les 15 champs de TypoTerrain`() {
        assertEquals(15, styles.size)
        val champs = TypoTerrain::class.java.declaredFields
            .filter { it.type == StyleTexte::class.java }
            .map { it.name }
            .toSet()
        assertEquals(styles.map { it.nom }.toSet(), champs, "styles de TypoTerrain non couverts ou en trop")
    }

    private fun sp(x: TextUnit) = x.type == TextUnitType.Sp

    @Test
    fun `TypoGroma egale TypoTerrain sur 15 styles et 4 valeurs`() {
        val e = mutableListOf<String>()
        for (s in styles) {
            val t = s.groma(TypoGroma)
            val a = s.terrain
            if (!sp(t.fontSize) || t.fontSize.value != a.tailleSp) {
                e += "${s.nom}.fontSize : attendu ${a.tailleSp} sp, obtenu ${t.fontSize}"
            }
            if (!sp(t.lineHeight) || t.lineHeight.value != a.interligneSp) {
                e += "${s.nom}.lineHeight : attendu ${a.interligneSp} sp, obtenu ${t.lineHeight}"
            }
            if (t.fontWeight?.weight != a.graisse) {
                e += "${s.nom}.fontWeight : attendu ${a.graisse}, obtenu ${t.fontWeight?.weight}"
            }
            if (!sp(t.letterSpacing) || t.letterSpacing.value != a.interlettrageSp) {
                e += "${s.nom}.letterSpacing : attendu ${a.interlettrageSp} sp, obtenu ${t.letterSpacing}"
            }
        }
        assertTrue(e.isEmpty(), "valeurs fautives (typographie) :\n" + e.joinToString("\n"))
    }
}
