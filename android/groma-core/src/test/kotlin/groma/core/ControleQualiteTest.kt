package groma.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControleQualiteTest {
    private fun tour(cq: ControleQualite, rayons: List<Double>, cibles: Boolean) {
        var t = 0.0
        for (r in rayons) for (k in 0 until 72) {
            val a = Math.toRadians(k * 5.0)
            val x = r * sin(a); val y = r * cos(a)
            for (regard in listOf(a, a + Math.PI)) {
                val vues = if (cibles && k % 6 == 0) setOf("C0", "C1", "C2", "C3") else emptySet()
                cq.ajouter(EchantillonPrise(t, x, y, 1.5, Pair(sin(regard), cos(regard)), true, 120.0, vues))
                t += 0.5
            }
        }
    }

    @Test
    fun priseComplete() {
        val cq = ControleQualite()
        cq.fixerCentre(0.0, 0.0)
        tour(cq, listOf(4.0, 11.0, 17.0), cibles = true)
        val v = cq.verdict()
        assertTrue(v.suffisante, v.raisons.toString())
        assertEquals(100.0, v.couverturePct, 1e-9)
    }

    @Test
    fun priseInsuffisanteAvecRaisons() {
        val cq = ControleQualite()
        cq.fixerCentre(0.0, 0.0)
        tour(cq, listOf(3.0), cibles = false)
        cq.ajouter(EchantillonPrise(1000.0, 0.0, 0.0, 1.5, Pair(0.0, 1.0), false, 0.0, emptySet()))
        cq.ajouter(EchantillonPrise(1010.0, 0.0, 0.0, 1.5, Pair(0.0, 1.0), false, 0.0, emptySet()))
        val v = cq.verdict()
        assertFalse(v.suffisante)
        assertTrue(v.raisons.any { "couverte" in it })
        assertTrue(v.raisons.any { "Suivi perdu" in it })
        assertTrue(v.raisons.any { "Cible C0" in it })
        assertTrue(cq.zonesManquantes().isNotEmpty())
    }

    @Test
    fun alertesInstantanees() {
        val cq = ControleQualite()
        cq.ajouter(EchantillonPrise(0.0, 0.0, 0.0, 1.5, Pair(0.0, 1.0), true, 120.0, emptySet()))
        val a = cq.ajouter(EchantillonPrise(0.5, 2.0, 0.0, 1.5, Pair(0.0, 1.0), true, 10.0, emptySet()))
        assertTrue(a.any { "floue" in it } && a.any { "rapide" in it })
    }

    @Test
    fun nettete() {
        val l = 64
        val uni = ByteArray(l * l) { 128.toByte() }
        val damier = ByteArray(l * l) { i -> if (((i % l) / 4 + (i / l) / 4) % 2 == 0) 0 else 255.toByte() }
        assertEquals(0.0, ControleQualite.nettete(uni, l, l), 1e-9)
        assertTrue(ControleQualite.nettete(damier, l, l, pas = 1) > 1000)
    }

    /** Mission 035: a 15 m plot is fully covered by a walk at 3, 8 and 13 m; at 20 m the rings stay 7, 14, 20 m. */
    @Test
    fun placetteDe15m() {
        val q15 = ControleQualite.pourRayon(15.0)
        q15.fixerCentre(0.0, 0.0)
        tour(q15, listOf(3.0, 8.0, 13.0), cibles = true)
        assertEquals(100.0, q15.couverturePct(), 1e-9)
        val q20 = ControleQualite.pourRayon(20.0)
        val ancien = ControleQualite()
        for (q in listOf(q20, ancien)) { q.fixerCentre(0.0, 0.0); tour(q, listOf(2.0, 5.0), cibles = true) }
        assertEquals(ancien.couverturePct(), q20.couverturePct(), 1e-9)
    }
}
