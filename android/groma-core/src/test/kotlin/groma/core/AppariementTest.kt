package groma.core

import kotlin.test.Test
import kotlin.test.assertContentEquals

class AppariementTest {
    @Test
    fun optimalPasGlouton() {
        // Same geometry as fixtures/rapprochement/03: greedy would lose u1.
        val anciens = listOf(0.0 to 5.0, 0.6 to 5.0)
        val nouveaux = listOf(0.3 to 5.0, 0.9 to 5.0)
        assertContentEquals(intArrayOf(0, 1), Appariement.apparier(anciens, nouveaux))
    }

    @Test
    fun horsTolerance() {
        assertContentEquals(intArrayOf(-1, 0), Appariement.apparier(listOf(10.0 to 0.0), listOf(0.0 to 0.0, 10.2 to 0.0)))
        assertContentEquals(intArrayOf(), Appariement.apparier(listOf(1.0 to 1.0), emptyList()))
    }

    @Test
    fun hongroisRectangulaire() {
        val c = arrayOf(doubleArrayOf(4.0, 1.0, 3.0), doubleArrayOf(2.0, 0.0, 5.0))
        assertContentEquals(intArrayOf(1, 0), Appariement.hongrois(c))
    }
}
