package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mission 019: species groups (Marculus colours are chosen in the app), search, trees of the plan. */
class EssencesEtArbresTest {
    @Test
    fun lesGroupesDeMarculus() {
        assertEquals(GroupeEssence.CHENE, Essences.groupe("chene_sessile"))
        assertEquals(GroupeEssence.CHENE, Essences.groupe("chene_pedoncule"))
        assertEquals(GroupeEssence.HETRE, Essences.groupe("hetre"))
        assertEquals(GroupeEssence.SAPIN, Essences.groupe("sapin_pectine"))
        assertEquals(GroupeEssence.EPICEA, Essences.groupe("epicea_commun"))
        assertEquals(GroupeEssence.FEUILLU, Essences.groupe("charme"))
        assertEquals(GroupeEssence.RESINEUX, Essences.groupe("douglas"))
        assertEquals(GroupeEssence.INCONNUE, Essences.groupe(null))
        assertEquals(GroupeEssence.INCONNUE, Essences.groupe(""))
        assertEquals(GroupeEssence.INCONNUE, Essences.groupe("sapin_magique"))
    }

    @Test
    fun leReferentielEstCeluiDeSaisieArbre() {
        assertEquals(SaisieArbre.ESSENCES, Essences.TOUTES.map { it.code }.toSet())
        assertTrue(Essences.TOUTES.all { it.libelle.isNotBlank() })
    }

    @Test
    fun rechercheParCodeEtLibelleSansAccent() {
        assertEquals("hetre", Essences.rechercher("hêtre").first().code)
        assertEquals("hetre", Essences.rechercher("HETRE").first().code)
        assertEquals(listOf("chene_sessile"), Essences.rechercher("chene sess").map { it.code })
        assertTrue(Essences.rechercher("chêne").size >= 2)
        assertTrue(Essences.rechercher("zzz").isEmpty())
        assertEquals(5, Essences.rechercher("", 5).size)
    }

    @Test
    fun resolutionDeLEssenceDuChamp() {
        // Out-of-list species already in the tree: shown as is, kept while the field is untouched.
        assertEquals("orme_inconnu (hors liste)", Essences.affichage("orme_inconnu"))
        assertEquals("orme_inconnu", Essences.resoudre("orme_inconnu (hors liste)", "orme_inconnu", null))
        // Touched: the usual rule (a listed species or empty), not the old code.
        assertEquals("", Essences.resoudre("  ", "orme_inconnu", null))
        assertEquals("hetre", Essences.resoudre(Essences.affichage("hetre"), "orme_inconnu", "hetre"))
        assertEquals("hetre", Essences.resoudre("hetre", null, null))
        assertNull(Essences.resoudre("chene", null, null))
        assertNull(Essences.resoudre("zzz", "orme_inconnu", null))
        // A listed species untouched stays itself; an out-of-list one is grey.
        assertEquals("charme", Essences.resoudre(Essences.affichage("charme"), "charme", null))
        assertEquals(GroupeEssence.INCONNUE, Essences.groupe("orme_inconnu"))
    }

    @Test
    fun rayonBorneEtCroissant() {
        assertEquals(ArbrePlacette.RAYON_MIN_DP, ArbrePlacette.rayonDp(null))
        assertEquals(ArbrePlacette.RAYON_MIN_DP, ArbrePlacette.rayonDp(0.0))
        assertEquals(ArbrePlacette.RAYON_MAX_DP, ArbrePlacette.rayonDp(199.8))
        assertTrue(ArbrePlacette.rayonDp(20.0) < ArbrePlacette.rayonDp(40.0))
        assertEquals((ArbrePlacette.RAYON_MIN_DP + ArbrePlacette.RAYON_MAX_DP) / 2, ArbrePlacette.rayonDp(43.75), 1e-4f)
    }

    @Test
    fun ordreDesNumeros() {
        assertEquals(listOf("1", "2", "10", "T1"), listOf("10", "2", "T1", "1").sortedWith(ArbrePlacette.ORDRE_NUMEROS))
    }

    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    @Test
    fun lectureDesArbresDeLaPlacette() = exemple { db ->
        val tous = ArbrePlacette.lire(db, "P001")
        assertEquals(18, tous.size)
        assertEquals(listOf("1", "2", "3"), tous.take(3).map { it.numero })
        val a = tous.first()
        assertEquals("hetre", a.essence)
        assertEquals(39.5, a.dbhCm)
        assertTrue(!a.dbhMesure)
        assertEquals(39.5, a.dbhEstimeCm)
        // Position = the Lambert-93 point of the geometry.
        val p = GeoPackage.lirePoints(db, "arbres", "P001").first { it["uuid"] == a.uuid }
        assertEquals(p["x"], a.x)
        assertEquals(p["y"], a.y)
        assertTrue(ArbrePlacette.lire(db, "ABSENTE").isEmpty())
    }

    @Test
    fun fauxPositifsEcartesEtSaisieRelue() = exemple { db ->
        val tous = ArbrePlacette.lire(db, "P001")
        val cible = tous[1]
        db.executer("UPDATE arbres SET etat_detection = 'faux_positif' WHERE uuid = ?", listOf(tous[0].uuid))
        GeoPackage.modifierArbre(db, cible.uuid, mapOf("essence_arbre" to "charme", "dbh_cm" to "52,5", "statut" to "mort", "qualite" to "A"))
        val apres = ArbrePlacette.lire(db, "P001")
        assertEquals(17, apres.size)
        assertNull(apres.firstOrNull { it.uuid == tous[0].uuid })
        val b = apres.first { it.uuid == cible.uuid }
        assertEquals(ArbrePlacette(cible.uuid, cible.numero, cible.x, cible.y, 52.5, true, "charme", "mort", "A", cible.dbhCm), b)
        // « Arbre suivant » follows the numbers, and stops after the last one.
        assertEquals(apres[1].uuid, ArbrePlacette.suivant(apres, apres[0].uuid)?.uuid)
        assertNotNull(ArbrePlacette.suivant(apres, b.uuid))
        assertNull(ArbrePlacette.suivant(apres, apres.last().uuid))
        assertNull(ArbrePlacette.suivant(apres, "inconnu"))
    }
}
