package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoTiffTest {
    @Test
    fun lectureMntExemple() {
        // DTM of the synthetic plot P001: z = 420 + 0.12 dx - 0.03 dy around (925000, 6700000).
        val f = Fixtures.racine.resolve("exemples/inventaire_video/rasters/P001_mnt.tif")
        val g = GeoTiff.lire(f.readBytes())
        assertEquals(0.5, g.res, 1e-12)
        for ((dx, dy) in listOf(0.0 to 0.0, 3.3 to -2.1, -7.25 to 9.9)) {
            assertEquals(420.0 + 0.12 * dx - 0.03 * dy, g.echantillonner(925000.0 + dx, 6700000.0 + dy), 1e-3)
        }
        assertTrue(g.echantillonner(925100.0, 6700000.0).isNaN())
    }
}
