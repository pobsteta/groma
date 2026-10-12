package groma.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class GeoreferencementTest {
    @Test
    fun triangulation() {
        val cible = doubleArrayOf(3.0, 7.0, 1.3)
        val rayons = listOf(doubleArrayOf(0.0, 0.0, 1.5), doubleArrayOf(5.0, 0.0, 1.5), doubleArrayOf(-2.0, 3.0, 1.6)).map { o ->
            val d = DoubleArray(3) { cible[it] - o[it] }
            val n = sqrt(d.sumOf { it * it })
            Rayon(o, DoubleArray(3) { d[it] / n })
        }
        val p = Georeferencement.trianguler(rayons)
        for (i in 0..2) assertEquals(cible[i], p[i], 1e-9)
    }

    @Test
    fun memeRotationQuePython() {
        // Same scenario as core/tests/test_georeferencement.py: rotation 37°, decl 1.5°, conv 2°.
        val rot = Math.toRadians(37.0)
        val c0 = Pair(3.0, -1.0)
        val loc = HashMap<String, Pair<Double, Double>>(); val mes = ArrayList<CibleMesuree>()
        for ((k, v) in mapOf("C1" to (20.0 to 8.0), "C2" to (140.0 to 12.0), "C3" to (260.0 to 6.5))) {
            val a = Math.toRadians(v.first)
            val dx = v.second * sin(a); val dy = v.second * cos(a)
            loc[k] = Pair(c0.first + dx * cos(rot) - dy * sin(rot), c0.second + dx * sin(rot) + dy * cos(rot))
            mes += CibleMesuree(k, ((v.first + 2.0 - 1.5) % 360) / 0.9, v.second, null)
        }
        val g = Georeferencement.georeferencer(c0, loc, mes, Pair(925000.0, 6700000.0), "gr", 1.5, 2.0)
        assertEquals(37.0, g.rotationDeg, 1e-9)
        val (x, y) = g.versGrille(loc.getValue("C1").first, loc.getValue("C1").second)
        assertEquals(925000.0 + 8 * sin(Math.toRadians(20.0)), x, 1e-9)
        assertEquals(6700000.0 + 8 * cos(Math.toRadians(20.0)), y, 1e-9)
    }
}
