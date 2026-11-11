package groma.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mission 020, point 5 (decision of 2026-11-11): a tree forgotten by the video, added in Groma.
 *
 * API EXPECTED FROM MISSION 023 (`groma-core`, object GeoPackage; names and signatures fixed here):
 *
 * ```
 * /**
 *  * Adds ONE tree to `arbres` of Groma's own copy (direct INSERT in one immediate transaction, nothing in
 *  * `detections`) and returns its new `uuid` (random UUID v4, lowercase). The position is computed from
 *  * the LATEST measured centre of [plotId] (`dernierCentreParPlacette`) exactly like
 *  * `PlacementCible.position`: [azimut] is the MAGNETIC azimuth in the unit of the plot
 *  * (`placettes.unite_azimut`, "deg" or "gr"), [distanceM] the horizontal distance in metres, with the
 *  * plot's `declinaison_deg` and `convergence_deg`. [dbhCm] is the tape diameter, REQUIRED, in [0, 1000].
 *  * [essence] (code of essences_bdforet_v2.json), [statut] (vivant/mort/chablis/coupe, null = vivant) and
 *  * [qualite] (A/B/C/D) are optional (null or blank = empty); same checks as SaisieArbre.controler.
 *  * Row written: plot_id, tree_id = num_provisoire = "A<n>" (n = 1, 2... per plot, never equal to the
 *  * number of another tree of the plot), dbh_cm = [dbhCm], dbh_source = "mesure", methode = "terrain",
 *  * etat_detection = "provisoire", h_source = "aucune", h_m / notes / photos / espece NULL,
 *  * distance_m = [distanceM], azimut = GEOGRAPHIC azimuth (magnetic + declination) in the plot unit, as
 *  * the video trees. Throws IllegalArgumentException, nothing written, when the plot is unknown or has no
 *  * measured centre, the azimuth is outside [0, 360[ (deg) or [0, 400[ (gr), the distance outside
 *  * [0, 60], the diameter is missing (NaN), negative, above 1000, or a code is out of its list.
 *  */
 * fun GeoPackage.ajouterArbre(
 *     db: BaseSqlite, plotId: String, azimut: Double, distanceM: Double, dbhCm: Double,
 *     essence: String? = null, statut: String? = null, qualite: String? = null,
 * ): String
 *
 * /** Deletes a tree added by mistake: only a row with methode = "terrain". IllegalArgumentException
 *  *  (nothing deleted) for a tree of the video or an unknown uuid. */
 * fun GeoPackage.supprimerArbreAjoute(db: BaseSqlite, uuid: String)
 * ```
 *
 * Also expected: `modifierArbre` refuses to empty the diameter of a tree whose methode is "terrain"
 * (the tape diameter is required: IllegalArgumentException, nothing written); the schema gets the value
 * `terrain` in domain `methode` (next minor version, 023).
 *
 * Hand-written values (example GeoPackage): centre of P001 = (925000.0, 6700000.0), declination 2.4,
 * convergence 2.163577, unit deg. Grid azimuth = magnetic + 2.4 - 2.163577; x = x0 + d sin(g), y = y0 + d cos(g):
 *   90 deg, 10 m -> (925009.9999149, 6699999.9587365);   0 deg, 12 m -> (925000.0495162, 6700011.9998978);
 *   270 deg, 8 m -> (924992.0000681, 6700000.0330108).
 */
class AjouterArbreTest {
    private val CENTRE = Pair(925000.0, 6700000.0)

    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    private fun arbre(db: SqliteJdbc, uuid: String) = db.requete("SELECT * FROM arbres WHERE uuid = ?", listOf(uuid)).single()
    private fun nbArbres(db: SqliteJdbc, plot: String = "P001") =
        (db.requete("SELECT COUNT(*) AS n FROM arbres WHERE plot_id = ?", listOf(plot)).single()["n"] as Number).toInt()

    private fun xy(a: Map<String, Any?>) = GeoPackage.decoderPoint(a["geom"] as ByteArray)

    private fun assertPosition(a: Map<String, Any?>, x: Double, y: Double) {
        val (px, py) = xy(a)
        assertEquals(x, px, 1e-4, "x"); assertEquals(y, py, 1e-4, "y")
    }

    private fun egal(attendu: Any?, obtenu: Any?, msg: String) {
        if (attendu is ByteArray && obtenu is ByteArray) assertContentEquals(attendu, obtenu, msg) else assertEquals(attendu, obtenu, msg)
    }

    @Test
    fun cheminNominal() = exemple { db ->
        val uuid = GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0, essence = "hetre", statut = "mort", qualite = "B")
        assertEquals(19, nbArbres(db))
        val a = arbre(db, uuid)
        assertPosition(a, 925009.9999149, 6699999.9587365)
        assertEquals("P001", a["plot_id"])
        assertEquals("A1", a["tree_id"])
        assertEquals("A1", a["num_provisoire"])
        assertEquals(38.0, (a["dbh_cm"] as Number).toDouble(), 0.0)
        assertEquals("mesure", a["dbh_source"])
        assertEquals("terrain", a["methode"])
        assertEquals("provisoire", a["etat_detection"])
        assertEquals("hetre", a["essence_arbre"])
        assertEquals("mort", a["statut"])
        assertEquals("B", a["qualite"])
        assertEquals(10.0, (a["distance_m"] as Number).toDouble(), 1e-9)
        assertEquals(92.4, (a["azimut"] as Number).toDouble(), 1e-3, "azimut géographique = magnétique + déclinaison")
        assertEquals("aucune", a["h_source"])
        assertNull(a["h_m"]); assertNull(a["notes"]); assertNull(a["photos"]); assertNull(a["espece"])
    }

    @Test
    fun uuidNeufEtValide() = exemple { db ->
        val existants = db.requete("SELECT uuid FROM arbres").map { it["uuid"] }.toSet()
        val u1 = GeoPackage.ajouterArbre(db, "P001", 10.0, 5.0, 20.0)
        val u2 = GeoPackage.ajouterArbre(db, "P001", 20.0, 6.0, 21.0)
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(u1), u1)
        assertNotEquals(u1, u2)
        assertTrue(u1 !in existants && u2 !in existants)
    }

    @Test
    fun positionsAuxQuatreCoinsEtRecoupeeAvecPlacementCible() = exemple { db ->
        val cas = listOf(Triple(0.0, 12.0, 925000.0495162 to 6700011.9998978), Triple(270.0, 8.0, 924992.0000681 to 6700000.0330108))
        for ((az, d, p) in cas) assertPosition(arbre(db, GeoPackage.ajouterArbre(db, "P001", az, d, 30.0).let { it }), p.first, p.second)
        // Same computation as the targets, for any azimuth and distance.
        for ((az, d) in listOf(33.3 to 17.25, 181.0 to 41.0, 359.99 to 0.5)) {
            val attendu = PlacementCible.position(CENTRE, az, "deg", 2.4, 2.163577, d)
            assertPosition(arbre(db, GeoPackage.ajouterArbre(db, "P001", az, d, 30.0)), attendu.first, attendu.second)
        }
    }

    @Test
    fun centreMesureLePlusRecentCompte() = exemple { db ->
        GeoPackage.ajouterCentreMesure(db, "P001", 925100.0, 6700200.0, "fix", 0.02, null, "2026-11-12T08:00:00.000Z")
        val a = arbre(db, GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0))
        assertPosition(a, 925109.9999149, 6700199.9587365)
    }

    @Test
    fun azimutEnGradesSelonLaPlacette() = exemple { db ->
        db.executer("UPDATE placettes SET unite_azimut = 'gr' WHERE plot_id = 'P001'")
        val a = arbre(db, GeoPackage.ajouterArbre(db, "P001", 100.0, 10.0, 38.0)) // 100 gr = 90 deg
        assertPosition(a, 925009.9999149, 6699999.9587365)
        assertEquals(92.4 * 400.0 / 360.0, (a["azimut"] as Number).toDouble(), 1e-3, "azimut stocké en grades")
        assertFailsWith<IllegalArgumentException> { GeoPackage.ajouterArbre(db, "P001", 400.0, 10.0, 38.0) }
        GeoPackage.ajouterArbre(db, "P001", 399.9, 10.0, 38.0)
    }

    @Test
    fun valeursParDefaut() = exemple { db ->
        val a = arbre(db, GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0))
        assertEquals("vivant", a["statut"]); assertNull(a["essence_arbre"]); assertNull(a["qualite"])
        val b = arbre(db, GeoPackage.ajouterArbre(db, "P001", 91.0, 10.0, 38.0, essence = " ", statut = "", qualite = ""))
        assertEquals("vivant", b["statut"]); assertNull(b["essence_arbre"]); assertNull(b["qualite"])
    }

    @Test
    fun diametreAuRubanObligatoireEtBorne() = exemple { db ->
        for (mauvais in listOf(Double.NaN, -0.1, 1000.1, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException>("dbh $mauvais") { GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, mauvais) }
        }
        assertEquals(18, nbArbres(db), "rien d'écrit")
        for (bon in listOf(0.0, 1000.0)) assertNotNull(GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, bon))
        assertEquals(20, nbArbres(db))
    }

    @Test
    fun codesHorsReferentielRefusesSansRienEcrire() = exemple { db ->
        assertFailsWith<IllegalArgumentException> { GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0, essence = "licorne") }
        assertFailsWith<IllegalArgumentException> { GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0, statut = "debout") }
        assertFailsWith<IllegalArgumentException> { GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0, qualite = "Z") }
        assertEquals(18, nbArbres(db))
        // A refused call does not consume a number.
        assertEquals("A1", arbre(db, GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0))["tree_id"])
    }

    @Test
    fun azimutEtDistanceHorsBornesRefuses() = exemple { db ->
        val refus = listOf(360.0 to 10.0, -1.0 to 10.0, 90.0 to -1.0, 90.0 to 60.1, Double.NaN to 10.0, 90.0 to Double.NaN)
        for ((az, d) in refus) assertFailsWith<IllegalArgumentException>("az $az, d $d") { GeoPackage.ajouterArbre(db, "P001", az, d, 38.0) }
        assertEquals(18, nbArbres(db))
        GeoPackage.ajouterArbre(db, "P001", 0.0, 0.0, 38.0)
        GeoPackage.ajouterArbre(db, "P001", 359.9, 60.0, 38.0)
    }

    @Test
    fun placetteInconnueOuSansCentreMesureRefusee() = exemple { db ->
        assertFailsWith<IllegalArgumentException> { GeoPackage.ajouterArbre(db, "P999", 90.0, 10.0, 38.0) }
        db.executer(
            "INSERT INTO placettes (geom, plot_id, rayon_m, seuil_diam_cm, unite_azimut, declinaison_deg, convergence_deg) VALUES (?, 'P003', 20, 7.5, 'deg', 2.4, 2.16)",
            listOf(GeoPackage.encoderPoint(925200.0, 6700100.0)),
        )
        val e = assertFailsWith<IllegalArgumentException> { GeoPackage.ajouterArbre(db, "P003", 90.0, 10.0, 38.0) }
        assertTrue(!e.message.isNullOrBlank(), "message en français non vide")
        assertEquals(0, nbArbres(db, "P003"))
    }

    @Test
    fun numerosProvisoiresDistinctsDeLaVideoEtParPlacette() = exemple { db ->
        val avant = db.requete("SELECT tree_id FROM arbres WHERE plot_id = 'P001'").map { it["tree_id"] }
        assertEquals((1..18).map { it.toString() }, avant.map { it.toString() }.sortedBy { it.toInt() })
        val u1 = GeoPackage.ajouterArbre(db, "P001", 10.0, 5.0, 20.0)
        val u2 = GeoPackage.ajouterArbre(db, "P001", 20.0, 6.0, 21.0)
        val u3 = GeoPackage.ajouterArbre(db, "P002", 30.0, 7.0, 22.0)
        assertEquals("A1", arbre(db, u1)["tree_id"])
        assertEquals("A2", arbre(db, u2)["tree_id"])
        assertEquals("A1", arbre(db, u3)["tree_id"], "numérotation par placette")
        val ids = db.requete("SELECT tree_id FROM arbres WHERE plot_id = 'P001'").map { it["tree_id"] }
        assertEquals(ids.size, ids.toSet().size, "tree_id unique dans la placette")
        // Deleting one and adding again never collides with the remaining added tree.
        GeoPackage.supprimerArbreAjoute(db, u1)
        val u4 = GeoPackage.ajouterArbre(db, "P001", 40.0, 8.0, 23.0)
        assertNotEquals(arbre(db, u2)["tree_id"], arbre(db, u4)["tree_id"])
    }

    @Test
    fun detectionsEtArbresDeLaVideoIntacts() = exemple { db ->
        val arbresAvant = db.requete("SELECT * FROM arbres ORDER BY uuid")
        val detectionsAvant = db.requete("SELECT * FROM detections ORDER BY uuid")
        val uuid = GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0)
        val detectionsApres = db.requete("SELECT * FROM detections ORDER BY uuid")
        assertEquals(detectionsAvant.size, detectionsApres.size, "aucune détection créée")
        assertEquals(0, db.requete("SELECT 1 AS u FROM detections WHERE uuid = ?", listOf(uuid)).size)
        val arbresApres = db.requete("SELECT * FROM arbres WHERE uuid <> ? ORDER BY uuid", listOf(uuid))
        assertEquals(arbresAvant.size, arbresApres.size)
        arbresAvant.zip(arbresApres).forEach { (a, b) -> for (k in a.keys) egal(a[k], b[k], "arbre ${a["uuid"]} : $k") }
    }

    @Test
    fun lesDeclencheursNeToucheTPasAuxArbresAjoutes() = exemple { db ->
        val uuid = GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0, essence = "hetre")
        val avant = arbre(db, uuid)
        db.executer("UPDATE detections SET dbh_cm = dbh_cm + 1, score = 0.5")
        db.executer("UPDATE detections SET etat_detection = etat_detection")
        val apres = arbre(db, uuid)
        for (k in avant.keys) egal(avant[k], apres[k], k)
    }

    @Test
    fun modifierArbreSurUnArbreAjoute() = exemple { db ->
        val uuid = GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0)
        GeoPackage.modifierArbre(db, uuid, mapOf("essence_arbre" to "chene_sessile", "dbh_cm" to "41,5"))
        assertEquals(41.5, (arbre(db, uuid)["dbh_cm"] as Number).toDouble(), 0.0)
        assertEquals("mesure", arbre(db, uuid)["dbh_source"])
        // The tape diameter is required: emptying it (back to "estimate") is refused, nothing written.
        assertFailsWith<IllegalArgumentException> { GeoPackage.modifierArbre(db, uuid, mapOf("essence_arbre" to "hetre", "dbh_cm" to "")) }
        val a = arbre(db, uuid)
        assertEquals(41.5, (a["dbh_cm"] as Number).toDouble(), 0.0)
        assertEquals("mesure", a["dbh_source"])
        assertEquals("chene_sessile", a["essence_arbre"], "refus complet : l'essence n'a pas changé")
    }

    @Test
    fun arbreAjouteVisibleSurLePlan() = exemple { db ->
        val uuid = GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0, essence = "hetre")
        val liste = ArbrePlacette.lire(db, "P001")
        assertEquals(19, liste.size)
        val a = liste.single { it.uuid == uuid }
        assertEquals("A1", a.numero)
        assertEquals(38.0, a.dbhCm!!, 0.0)
        assertTrue(a.dbhMesure)
        assertEquals("hetre", a.essence)
        assertEquals(925009.9999149, a.x, 1e-4)
        assertEquals(6699999.9587365, a.y, 1e-4)
    }

    @Test
    fun supprimerSeulementUnArbreAjoute() = exemple { db ->
        val uuid = GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0)
        GeoPackage.supprimerArbreAjoute(db, uuid)
        assertEquals(18, nbArbres(db))
        assertEquals(0, db.requete("SELECT 1 AS u FROM arbres WHERE uuid = ?", listOf(uuid)).size)
        // A tree of the video is never deleted (rule 5), whatever its state.
        val video = "00000000-0000-4000-8000-000000000014"
        assertFailsWith<IllegalArgumentException> { GeoPackage.supprimerArbreAjoute(db, video) }
        assertFailsWith<IllegalArgumentException> { GeoPackage.supprimerArbreAjoute(db, "00000000-0000-4000-8000-0000000000ff") }
        assertEquals(18, nbArbres(db))
        assertEquals(1, db.requete("SELECT 1 AS u FROM detections WHERE uuid = ?", listOf(video)).size)
    }

    @Test
    fun codesProduitsDansLesReferentiels() = exemple { db ->
        val domaines = Fixtures.lire(File(Fixtures.racine, "core/src/groma/donnees/inventaire_video.schema.json"))["domaines"]!!.jsonObject
        fun domaine(nom: String) = domaines[nom]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        val essences = Essences.TOUTES.map { it.code }.toSet()
        val a = arbre(db, GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0, essence = "hetre", statut = "chablis", qualite = "C"))
        assertTrue(a["methode"] in domaine("methode"), "methode ${a["methode"]} hors du domaine du schéma (valeur « terrain » à ajouter par 023)")
        assertTrue(a["etat_detection"] in domaine("etat_detection"))
        assertTrue(a["dbh_source"] in domaine("dbh_source"))
        assertTrue(a["h_source"] in domaine("h_source"))
        assertTrue(a["statut"] in domaine("statut"))
        assertTrue(a["qualite"] in domaine("qualite"))
        assertTrue(a["essence_arbre"] in essences)
    }

    @Test
    fun arbreAjouteDansLArchiveDeTerrain() = exemple { db ->
        val uuid = GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0, essence = "hetre")
        val fichier = Files.createTempFile("copie", ".gpkg").toFile()
        // The test keeps its connection on a temp file: re-open the same file as the phone would.
        val chemin = db.requete("PRAGMA database_list").single { it["name"] == "main" }["file"].toString()
        Files.copy(File(chemin).toPath(), fichier.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val sortie = Files.createTempFile("terrain", ".gpkg").toFile()
        ExportTerrain { SqliteJdbc(DriverManager.getConnection("jdbc:sqlite:$it")) }.extraire(fichier, "P001", sortie)
        DriverManager.getConnection("jdbc:sqlite:$sortie").use { cx ->
            val t = SqliteJdbc(cx)
            assertEquals(19, nbArbres(t))
            val a = arbre(t, uuid)
            assertEquals(listOf("A1", "terrain", "mesure", "hetre"), listOf(a["tree_id"], a["methode"], a["dbh_source"], a["essence_arbre"]))
            assertEquals(38.0, (a["dbh_cm"] as Number).toDouble(), 0.0)
        }
    }
}
