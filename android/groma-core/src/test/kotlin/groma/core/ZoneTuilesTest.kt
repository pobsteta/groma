package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZoneTuilesTest {
    @Test
    fun tuile_d_un_point() {
        // lon 5°: (185 / 360) × 4096 = 2104.9; lat 47°: asinh(tan 47°) = 0.9316,
        // (1 − 0.9316 / π) / 2 × 4096 = 1440.7.
        assertEquals(2104 to 1440, ZoneTuiles.tuile(5.0, 47.0, 12))
        assertEquals(0 to 0, ZoneTuiles.tuile(-180.0, 89.0, 3))
        assertEquals(7 to 7, ZoneTuiles.tuile(180.0, -89.0, 3))
    }

    @Test
    fun nombre_et_ordre_des_tuiles() {
        val z = ZoneTuiles(Emprise(4.98, 47.19, 5.02, 47.21), 17, 18)
        // Zoom 18: columns 134698..134727 (30), rows 91978..91999 (22).
        assertEquals((134698..134727) to (91978..91999), z.plage(18))
        val (xs17, ys17) = z.plage(17)
        val attendu = 30L * 22 + xs17.count().toLong() * ys17.count()
        assertEquals(attendu, z.nombre)
        val liste = z.tuiles().toList()
        assertEquals(attendu, liste.size.toLong())
        assertEquals(17, liste.first().z)
        assertEquals(Tuile(18, 134727, 91999), liste.last())
    }

    @Test
    fun marge_autour_du_projet() {
        val e = Emprise(5.0, 47.0, 5.01, 47.01)
        val z = ZoneTuiles.projet(e)
        // 200 m ≈ 0.0018° of latitude, ≈ 0.0026° of longitude at 47°.
        assertEquals(47.0 - 0.0018, z.emprise.sud, 1e-4)
        assertEquals(5.01 + 0.0026, z.emprise.est, 1e-4)
        assertEquals(12, z.zoomMin)
        assertEquals(18, z.zoomMax)
        // A forest of about 1 km: some hundreds of tiles, far below the limit.
        assertTrue(z.nombre in 200..ZoneTuiles.NOMBRE_MAX)
    }
}
