package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChoixThemeNuitTest {
    @Test
    fun `nuit forcee suit le telephone pour systeme`() = assertNull(ChoixTheme.SYSTEME.nuitForcee)

    @Test
    fun `nuit forcee est faux pour clair`() = assertEquals(false, ChoixTheme.CLAIR.nuitForcee)

    @Test
    fun `nuit forcee est vrai pour sombre`() = assertEquals(true, ChoixTheme.SOMBRE.nuitForcee)
}
