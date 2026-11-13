package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Mission 034. Expected values written by hand: heading 90° (facing east) -> map turned by 270°
 * (east at the top); north up -> 0 whatever the heading; no heading -> 0; a 1° jitter is ignored,
 * 359° -> 3° counts as 4° (shortest way); back to north up always applied.
 */
class OrientationCarteTest {
    @Test
    fun rotation() {
        assertEquals(270.0, OrientationCarte.rotation(true, 90.0), 1e-9)
        assertEquals(0.0, OrientationCarte.rotation(true, 0.0), 1e-9)
        assertEquals(45.0, OrientationCarte.rotation(true, 315.0), 1e-9)
        assertEquals(0.0, OrientationCarte.rotation(false, 90.0), 1e-9)
        assertEquals(0.0, OrientationCarte.rotation(true, null), 1e-9)
    }

    @Test
    fun seuil() {
        assertNull(OrientationCarte.aTourner(270.0, 271.0))
        assertEquals(273.0, OrientationCarte.aTourner(270.0, 273.0))
        assertEquals(3.0, OrientationCarte.aTourner(359.0, 3.0))
        assertNull(OrientationCarte.aTourner(359.5, 0.5))
        assertEquals(0.0, OrientationCarte.aTourner(1.0, 0.0))
    }
}
