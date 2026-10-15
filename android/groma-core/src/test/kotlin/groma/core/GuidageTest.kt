package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuidageTest {
    private val ici = Position(47.0, 5.0)

    private fun placette(lat: Double, lon: Double, unite: String = "deg", declinaison: Double? = 2.4) =
        PlacetteCarte("P012", "Base", "a_faire", lon, lat, 0.0, 0.0, 15.0, 12, unite, declinaison)

    @Test
    fun plein_nord_en_degres() {
        // 0.001° of latitude at 47° N: about 111.2 m due north.
        val g = Guidage.calculer(ici, placette(47.001, 5.0))
        assertEquals(111.2, g.distanceM, 0.2)
        assertEquals(0, g.azimutGeo)
        // Declination 2.4° east: the compass reads 357.6°, shown 358.
        assertEquals(358, g.azimutVise)
        assertFalse(g.arrive)
    }

    @Test
    fun plein_est_en_grades() {
        // Due east, declination 2.7°: geographic 100 gr, magnetic 87.3° = 97 gr.
        val g = Guidage.calculer(ici, placette(47.0, 5.001, unite = "gr", declinaison = 2.7))
        assertEquals(100, g.azimutGeo)
        assertEquals(97, g.azimutVise)
        assertEquals(76.1, g.distanceM, 0.1)
    }

    @Test
    fun sans_declinaison_on_vise_le_geographique() {
        val g = Guidage.calculer(ici, placette(47.0, 5.001, declinaison = null))
        assertNull(g.azimutMagDeg)
        assertEquals(90, g.azimutVise)
    }

    @Test
    fun le_nord_s_affiche_zero() {
        assertEquals(0, Guidage("P1", 10.0, 359.7, 359.7, "deg").azimutVise)
        assertEquals(0, Guidage("P1", 10.0, 359.9, null, "gr").azimutGeo)
    }

    @Test
    fun fleche_relative_au_cap() {
        val g = Guidage("P1", 10.0, 90.0, null, "deg")
        assertEquals(100.0, g.relatifDeg(350.0)!!, 1e-9)
        assertEquals(-10.0, g.relatifDeg(100.0)!!, 1e-9)
        assertNull(g.relatifDeg(null))
    }

    @Test
    fun arrivee_a_moins_de_cinq_metres() {
        // 0.00004° of latitude ≈ 4.4 m.
        assertTrue(Guidage.calculer(ici, placette(47.00004, 5.0)).arrive)
        assertFalse(Guidage("P1", 5.0, 0.0, null, "deg").arrive)
    }

    @Test
    fun unite_inconnue_en_degres() {
        assertEquals("deg", Guidage.calculer(ici, placette(47.001, 5.0, unite = "mil")).unite)
    }

    @Test
    fun prochaine_placette_a_faire_du_parcours() {
        fun p(id: String, etat: String, ordre: Int?, type: String = "Base") = PlacetteCarte(id, type, etat, 5.0, 47.0, 0.0, 0.0, 15.0, ordre)
        val d = DonneesCarte(
            listOf(p("P1", "provisoire", 1), p("P2", "a_faire", 2), p("P3", "definitif", 3), p("P4", "a_faire", 4), p("O1", "a_faire", 5, "Over")),
            emptyList(), emptyList(),
        )
        assertEquals("P2", d.prochaine()?.plotId)
        assertEquals("P4", d.prochaine("P2")?.plotId)
        // Wraps round to the start of the route; Over plots are not on it.
        assertEquals("P2", d.prochaine("P4")?.plotId)
        assertEquals("P2", d.prochaine("O1")?.plotId)
        val fini = DonneesCarte(listOf(p("P1", "definitif", 1), p("P2", "a_faire", 2)), emptyList(), emptyList())
        assertNull(fini.prochaine("P2"))
    }
}
