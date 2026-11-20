package groma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilmCaptureTest {
    private val go = FilmCapture.PLACE_MINIMALE_OCTETS

    @Test fun caseDecocheeAucunFilm() {
        assertEquals(FilmCapture.Decision.Aucun, FilmCapture.decision(false, false, 100 * go))
    }

    @Test fun caseCocheeEtPlaceSuffisanteEnregistre() {
        assertEquals(FilmCapture.Decision.Enregistrer, FilmCapture.decision(true, false, go))
    }

    @Test fun manquePlacePrevientSansEnregistrer() {
        val d = FilmCapture.decision(true, false, go - 1)
        assertEquals(FilmCapture.Decision.PlaceInsuffisante(go - 1, go), d)
    }

    @Test fun uneRelectureNEnregistrePasUnFilm() {
        assertEquals(FilmCapture.Decision.Aucun, FilmCapture.decision(true, true, 100 * go))
    }

    @Test fun nomFilmSimpleSiLibre() {
        assertEquals("P065.groma.mp4", FilmCapture.nomFilm("P065", emptySet(), "20261114T101500"))
        assertEquals("P065.groma.mp4", FilmCapture.nomFilm("P065", setOf("P066.groma.mp4"), "20261114T101500"))
    }

    @Test fun nomFilmSuffixeSiExistant() {
        assertEquals("P065-20261114T101500.groma.mp4", FilmCapture.nomFilm("P065", setOf("P065.groma.mp4"), "20261114T101500"))
    }

    @Test fun nomFilmNEcraseJamais() {
        val h = "20261114T101500"
        val existants = mutableSetOf("P065.groma.mp4", "P065-$h.groma.mp4")
        repeat(5) {
            val nom = FilmCapture.nomFilm("P065", existants, h)
            assertFalse(nom in existants, nom)
            existants += nom
        }
        assertEquals(
            setOf("P065.groma.mp4", "P065-$h.groma.mp4", "P065-$h-2.groma.mp4", "P065-$h-3.groma.mp4",
                "P065-$h-4.groma.mp4", "P065-$h-5.groma.mp4", "P065-$h-6.groma.mp4"),
            existants,
        )
    }

    @Test fun paquetRejeuDistinctDuPaquetDuTerrain() {
        val nom = FilmCapture.nomPaquetRejeu("P065", setOf("P065.groma.zip"), "20261114T101500")
        assertEquals("P065.rejeu.groma.zip", nom)
        assertEquals("P065.rejeu-20261114T101500.groma.zip", FilmCapture.nomPaquetRejeu("P065", setOf(nom), "20261114T101500"))
    }

    @Test fun nomDeFilmSansSeparateurDeDossier() {
        val nom = FilmCapture.nomFilm("P065", setOf("P065.groma.mp4"), "20261114T101500")
        assertTrue('/' !in nom)
    }

    @Test fun sourceDuManifeste() {
        assertEquals("rejeu", FilmCapture.source(true))
        assertEquals("terrain", FilmCapture.source(false))
    }

    @Test fun rejeuReserveALaVersionDEssai() {
        assertTrue(FilmCapture.rejeuAutorise("fr.nemeton.groma.fumee"))
        assertFalse(FilmCapture.rejeuAutorise("fr.nemeton.groma"))
    }

    @Test fun identiteDuFilm() {
        val id = FilmCapture.Identite("marsannay", "P065", "2026.11.7", "2026-11-14T10:15:00Z", 15.0, 7.5)
        val j = Json.parseToJsonElement(id.json()).jsonObject
        assertEquals("groma-film", j["format"]!!.jsonPrimitive.content)
        assertEquals("marsannay", j["projet_id"]!!.jsonPrimitive.content)
        assertEquals("P065", j["plot_id"]!!.jsonPrimitive.content)
        assertEquals("2026.11.7", j["version_appli"]!!.jsonPrimitive.content)
        assertEquals("2026-11-14T10:15:00Z", j["date"]!!.jsonPrimitive.content)
        assertEquals(15.0, j["rayon_m"]!!.jsonPrimitive.content.toDouble())
        assertEquals(7.5, j["seuil_diam_cm"]!!.jsonPrimitive.content.toDouble())
    }

    @Test fun identiteEchappeLesGuillemets() {
        val id = FilmCapture.Identite("a\"b\\c", "P1", "v", "d", 15.0, 7.5)
        assertEquals("a\"b\\c", Json.parseToJsonElement(id.json()).jsonObject["projet_id"]!!.jsonPrimitive.content)
    }
}
