package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResumeProjetTest {
    private fun p(type: String, etat: String, rayon: Double?) = mapOf("type" to type, "etat_traitement" to etat, "rayon_m" to rayon)

    @Test
    fun avancementEtSurfaceInventoriee() {
        val r = ResumeProjet.calculer(
            listOf(
                p("Base", "provisoire", 15.0),
                p("Base", "definitif", 20.0),
                p("Base", "a_faire", 15.0),
                p("Over", "envoye", 15.0),
                p("Over", "a_faire", null),
            ),
            listOf(mapOf("date_mesure" to "2026-10-12T09:30:00"), mapOf("date_mesure" to "2026-10-13T08:00:00"), mapOf("date_mesure" to null)),
        )
        assertEquals(3 to 2, r.nbBase to r.nbRempl)
        assertEquals(3, r.faites)
        assertEquals(2, r.baseFaites)
        assertEquals(2, r.envoyees)
        assertEquals(1, r.nonEnvoyees)
        // Each plot with its own radius: 15 m, 20 m and 15 m.
        assertEquals(PI * (225 + 400 + 225) / 10_000, r.surfaceInventorieeHa, 1e-12)
        assertEquals(15.0 to 20.0, r.rayonMinM to r.rayonMaxM)
        assertEquals("2026-10-13T08:00:00", r.dernierReleve)
        assertFalse(r.termine)
    }

    @Test
    fun termineSansLesRemplacements() {
        assertTrue(ResumeProjet.calculer(listOf(p("Base", "envoye", 15.0), p("Over", "a_faire", 15.0))).termine)
        assertFalse(ResumeProjet.calculer(emptyList()).termine)
        // Plan without type column: every plot is a Base plot.
        val r = ResumeProjet.calculer(listOf(mapOf("etat_traitement" to null, "rayon_m" to 20.0)))
        assertEquals(1 to 0, r.nbBase to r.nbRempl)
        assertEquals(0.0, r.surfaceInventorieeHa)
        assertEquals(null, r.dernierReleve)
    }

    @Test
    fun donneesTerrainBloquentLeRemplacement() {
        val src = Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath()
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { cx ->
            val db = SqliteJdbc(cx)
            // The example holds detections, targets and centres of P001.
            assertTrue(ResumeProjet.aDesDonneesTerrain(db))
            assertEquals(1, ResumeProjet.lire(db).faites)
            for (t in listOf("arbres", "detections", "cibles", "centres_mesures")) db.executer("DELETE FROM $t")
            assertTrue(ResumeProjet.aDesDonneesTerrain(db)) // P001 is still provisoire
            db.executer("UPDATE placettes SET etat_traitement = 'a_faire'")
            assertFalse(ResumeProjet.aDesDonneesTerrain(db))
        }
    }
}
