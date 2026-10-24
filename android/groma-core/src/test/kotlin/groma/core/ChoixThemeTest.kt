package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChoixThemeTest {

    // --- estSombre : 3 choix x 2 états du téléphone ---

    @Test
    fun `systeme suit le telephone en sombre`() {
        assertTrue(ChoixTheme.SYSTEME.estSombre(systemeSombre = true))
    }

    @Test
    fun `systeme suit le telephone en clair`() {
        assertFalse(ChoixTheme.SYSTEME.estSombre(systemeSombre = false))
    }

    @Test
    fun `clair reste clair sur un telephone en sombre`() {
        assertFalse(ChoixTheme.CLAIR.estSombre(systemeSombre = true))
    }

    @Test
    fun `clair reste clair sur un telephone en clair`() {
        assertFalse(ChoixTheme.CLAIR.estSombre(systemeSombre = false))
    }

    @Test
    fun `sombre reste sombre sur un telephone en sombre`() {
        assertTrue(ChoixTheme.SOMBRE.estSombre(systemeSombre = true))
    }

    @Test
    fun `sombre reste sombre sur un telephone en clair`() {
        assertTrue(ChoixTheme.SOMBRE.estSombre(systemeSombre = false))
    }

    // --- choixThemeDepuis ---

    @Test
    fun `chaque nom valide est relu tel quel`() {
        for (c in ChoixTheme.entries) {
            assertEquals(c, choixThemeDepuis(c.name))
        }
    }

    @Test
    fun `il y a exactement trois choix`() {
        assertEquals(
            setOf("SYSTEME", "CLAIR", "SOMBRE"),
            ChoixTheme.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `nom absent donne systeme`() {
        assertEquals(ChoixTheme.SYSTEME, choixThemeDepuis(null))
    }

    @Test
    fun `nom vide donne systeme`() {
        assertEquals(ChoixTheme.SYSTEME, choixThemeDepuis(""))
    }

    @Test
    fun `nom inconnu donne systeme`() {
        assertEquals(ChoixTheme.SYSTEME, choixThemeDepuis("VIOLET"))
    }

    // Comportement retenu, aligné sur Marculus (comparaison stricte sur name) :
    // la casse compte, "sombre" n'est pas "SOMBRE" -> inconnu -> SYSTEME.
    @Test
    fun `casse differente est inconnue donc systeme`() {
        assertEquals(ChoixTheme.SYSTEME, choixThemeDepuis("sombre"))
        assertEquals(ChoixTheme.SYSTEME, choixThemeDepuis("Clair"))
    }

    @Test
    fun `espaces autour du nom sont inconnus donc systeme`() {
        assertEquals(ChoixTheme.SYSTEME, choixThemeDepuis(" SOMBRE "))
    }
}
