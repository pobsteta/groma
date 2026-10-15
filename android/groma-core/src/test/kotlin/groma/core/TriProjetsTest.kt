package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TriProjetsTest {
    @Test
    fun centre_de_l_emprise() {
        val c = TriProjets.centreEmprise("POLYGON ((5.984172 47.362097, 5.984172 47.362845, 5.981744 47.362845, 5.981744 47.362097, 5.984172 47.362097))")!!
        assertEquals(47.362471, c.latitude, 1e-9)
        assertEquals(5.982958, c.longitude, 1e-9)
        assertEquals(Position(1.0, 2.0), TriProjets.centreEmprise("POINT (2 1)"))
        assertNull(TriProjets.centreEmprise(null))
        assertNull(TriProjets.centreEmprise("POLYGON EMPTY"))
        // Lambert 93 coordinates are not degrees: ignored.
        assertNull(TriProjets.centreEmprise("POINT (870000 6700000)"))
    }

    private data class P(val cle: String, val nom: String, val emprise: String?)

    private fun trier(liste: List<P>, dernier: String?, position: Position?) =
        TriProjets.trier(liste, dernier, position, P::cle, P::emprise, compareBy { it.nom }).map { it.cle }

    @Test
    fun dernier_ouvert_puis_par_distance() {
        val dijon = P("dijon", "Zeta", "POINT (5.04 47.32)")
        val beaune = P("beaune", "Alpha", "POINT (4.84 47.02)")
        val couchey = P("couchey", "Mu", "POINT (4.98 47.26)")
        val ancien = P("ancien", "Beta", null)
        val liste = listOf(dijon, beaune, ancien, couchey)
        // At Couchey: Couchey, then Dijon (~8 km), then Beaune (~27 km); no extent last.
        val ici = Position(47.26, 4.98)
        assertEquals(listOf("couchey", "dijon", "beaune", "ancien"), trier(liste, null, ici))
        assertEquals(listOf("beaune", "couchey", "dijon", "ancien"), trier(liste, "beaune", ici))
        // Without a position: by name, the last opened still first.
        assertEquals(listOf("couchey", "beaune", "ancien", "dijon"), trier(liste, "couchey", null))
        assertEquals(listOf("beaune", "ancien", "couchey", "dijon"), trier(liste, null, null))
    }
}
