package groma.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CercleTroncsTest {
    private fun arc(xc: Double, yc: Double, r: Double, a0: Double, a1: Double, n: Int, bruit: Double, graine: Int): Pair<DoubleArray, DoubleArray> {
        val rng = Random(graine)
        val x = DoubleArray(n); val y = DoubleArray(n)
        for (i in 0 until n) {
            val t = a0 + rng.nextDouble() * (a1 - a0)
            x[i] = xc + r * cos(t) + bruit * rng.nextGaussian()
            y[i] = yc + r * sin(t) + bruit * rng.nextGaussian()
        }
        return Pair(x, y)
    }

    private fun Random.nextGaussian(): Double {
        val u = nextDouble().coerceAtLeast(1e-12); val v = nextDouble()
        return kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * cos(2 * PI * v)
    }

    @Test
    fun cercleExact() {
        val (x, y) = arc(3.0, -2.0, 0.25, 0.0, 2 * PI, 100, 0.0, 1)
        val (xc, yc, r) = Cercle.algebrique(x, y)
        assertEquals(3.0, xc, 1e-9); assertEquals(-2.0, yc, 1e-9); assertEquals(0.25, r, 1e-9)
    }

    @Test
    fun ransacSurCoordonneesLambert() {
        val (x, y) = arc(925000.0, 6700000.0, 0.3, 0.0, PI, 150, 0.003, 2)
        val rng = Random(3)
        val bx = x + DoubleArray(60) { 924999.0 + 2 * rng.nextDouble() }
        val by = y + DoubleArray(60) { 6699999.0 + 2 * rng.nextDouble() }
        val c = assertNotNull(Cercle.ransac(bx, by, 0.01))
        assertEquals(60.0, c.dbhCm, 1.0)
        assertTrue(hypot(c.xc - 925000.0, c.yc - 6700000.0) < 0.01)
    }

    @Test
    fun troncsEtGrosBoisEnArcsBrises() {
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        fun ajouter(p: Pair<DoubleArray, DoubleArray>) { xs += p.first.toList(); ys += p.second.toList() }
        ajouter(arc(2.0, 3.0, 0.15, 0.0, 2.5, 300, 0.004, 4))
        ajouter(arc(-4.0, 1.0, 0.22, 1.0, 4.0, 300, 0.004, 5))
        // A 210 cm trunk seen as three separated arcs.
        ajouter(arc(6.0, 6.0, 1.05, 3.4, 3.9, 400, 0.004, 6))
        ajouter(arc(6.0, 6.0, 1.05, 4.1, 4.6, 400, 0.004, 7))
        ajouter(arc(6.0, 6.0, 1.05, 4.8, 5.4, 400, 0.004, 8))
        val t = Troncs.detecter(xs.toDoubleArray(), ys.toDoubleArray())
        assertEquals(3, t.size, t.toString())
        val gros = t.single { hypot(it.x - 6.0, it.y - 6.0) < 0.3 }
        assertEquals(210.0, gros.dbhCm, 4.0)
        assertEquals(30.0, t.single { hypot(it.x - 2.0, it.y - 3.0) < 0.1 }.dbhCm, 1.0)
    }
}
