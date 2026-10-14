package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectionsTest {
    // Lambert-93 x, y → longitude, latitude → Web Mercator x, y (PROJ 9 / pyproj, EPSG:2154 → 4326 → 3857).
    private val references = listOf(
        doubleArrayOf(700000.0, 6600000.0, 3.0, 46.5, 333958.4724, 5860839.8299),
        doubleArrayOf(925000.0, 6700000.0, 5.9817438708, 47.3620970514, 665884.6818, 6001379.1099),
        doubleArrayOf(853412.31, 6693028.77, 5.0311905006, 47.3198242146, 560069.5646, 5994434.6675),
        doubleArrayOf(350000.0, 6800000.0, -1.7141138070, 48.2062959130, -190814.2762, 6141244.0023),
        doubleArrayOf(1200000.0, 6100000.0, 9.0108717318, 41.8295061399, 1003085.6528, 5135474.4058),
    )

    @Test
    fun lambert93VersWgs84() {
        for ((x, y, lon, lat) in references.map { it.toList() }) {
            val (l, p) = Projections.versWgs84(x, y)
            assertEquals(lon, l, 1e-9, "longitude de $x, $y") // 1e-9° ≈ 0,1 mm
            assertEquals(lat, p, 1e-9, "latitude de $x, $y")
        }
    }

    @Test
    fun wgs84VersLambert93() {
        for (r in references) {
            val (x, y) = Projections.versLambert93(r[2], r[3])
            assertEquals(r[0], x, 1e-3)
            assertEquals(r[1], y, 1e-3)
        }
    }

    @Test
    fun webMercatorAllerRetour() {
        for (r in references) {
            val (x, y) = Projections.versWebMercator(r[2], r[3])
            assertEquals(r[4], x, 1e-3)
            assertEquals(r[5], y, 1e-3)
            val (lon, lat) = Projections.depuisWebMercator(x, y)
            assertEquals(r[2], lon, 1e-10)
            assertEquals(r[3], lat, 1e-10)
        }
    }
}
