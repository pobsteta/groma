package groma.core

import groma.core.Fixtures.d
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DeclinaisonTest {
    private val fixture = Fixtures.lire(File(Fixtures.dossier("declinaison"), "wmm2025.json"))

    @Test
    fun valeurs_officielles_et_france() {
        val tol = fixture["tolerance_deg"].d()!!
        val points = fixture["points"]!!.jsonArray + fixture["france"]!!.jsonArray
        assertEquals(102, points.size)
        for (e in points) {
            val p = e as JsonObject
            val d = Declinaison.declinaisonDeg(p["lat"].d()!!, p["lon"].d()!!, p["annee"].d()!!, p["altitude_km"].d()!! * 1000)
            assertEquals(p["declinaison_deg"].d()!!, d, tol, "point $p")
        }
    }

    @Test
    fun annee_decimale() {
        assertEquals(2028.5, Declinaison.anneeDecimale(LocalDate.of(2028, 7, 2)))
        assertEquals(2026.0, Declinaison.anneeDecimale(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun hors_validite() {
        assertFailsWith<IllegalArgumentException> { Declinaison.declinaisonDeg(47.0, 5.0, 2031.0) }
        assertNull(Declinaison.ouNull(47.0, 5.0, LocalDate.of(2030, 6, 1)))
        assertEquals(2.689, Declinaison.ouNull(47.26, 4.97, LocalDate.of(2026, 1, 1))!!, 0.001)
    }
}
