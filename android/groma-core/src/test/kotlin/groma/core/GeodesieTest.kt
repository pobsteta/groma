package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GeodesieTest {
    // Geodesics on GRS80 (PROJ, pyproj Geod.inv): from, to, azimuth (°), distance (m).
    private class Ref(val a: Position, val b: Position, val azimut: Double, val distance: Double)

    private val references = listOf(
        Ref(Position(47.262, 4.98), Position(47.265, 4.985), 48.605162, 504.4128),
        Ref(Position(47.262, 4.98), Position(47.260, 4.979), 198.798078, 234.8799),
        Ref(Position(47.3, 5.0), Position(47.3, 5.002), 89.999265, 151.2585),
    )

    @Test
    fun distanceEtAzimut() {
        for (r in references) {
            assertEquals(r.distance, Geodesie.distanceM(r.a, r.b), 0.005, "distance") // < 5 mm
            assertEquals(r.azimut, Geodesie.azimutDeg(r.a, r.b), 0.002, "azimut")
        }
        assertEquals(0.0, Geodesie.distanceM(Position(47.0, 5.0), Position(47.0, 5.0)))
    }
}
