package groma.core

import fr.nemeton.terrain.gnss.QualiteFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GnssGromaTest {
    @Test
    fun requete_ntrip_annonce_groma() {
        val r = NtripGroma.requete("/RTCM3", "caster.exemple.fr", 2101, "u", "p")
        assertTrue("User-Agent: NTRIP Groma\r\n" in r, r)
        assertTrue("Marculus" !in r, r)
    }

    @Test
    fun libelles_de_qualite_comme_avant_pour_toutes_les_valeurs() {
        val attendu = mapOf(
            QualiteFix.INVALIDE to "Sans position", QualiteFix.AUTONOME to "Autonome", QualiteFix.DGPS to "DGPS",
            QualiteFix.PPS to "PPS", QualiteFix.RTK_FIXE to "RTK fixe", QualiteFix.RTK_FLOAT to "RTK flottant",
            QualiteFix.ESTIME to "Estimé", QualiteFix.MANUEL to "Manuel", QualiteFix.SIMULATION to "Simulation",
        )
        assertEquals(QualiteFix.entries.toSet(), attendu.keys)
        for ((q, t) in attendu) assertEquals(t, LibellesGnss.qualite(q), q.name)
    }
}
