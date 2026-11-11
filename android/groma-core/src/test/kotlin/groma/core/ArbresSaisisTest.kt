package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mission 017: triggers (Kotlin side, same SQLite as Android) and data sent by the local server.
 *
 * Expected from mission 018:
 *  - the example GeoPackage `exemples/inventaire_video/` is regenerated with the new column
 *    `arbres.dbh_source` (default 'estime', existing rows 'estime') and the new `trg_detections_update`;
 *  - `GeoPackage.arbresSaisis(db: BaseSqlite): List<Map<String, Any?>>`: one map per tree of `arbres`
 *    that carries an entry made in Groma (essence_arbre or qualite not null, statut other than
 *    'vivant', or dbh_source = 'mesure'), with exactly the keys `uuid`, `essence_arbre`, `dbh_cm`,
 *    `dbh_source`, `statut`, `qualite` (null values kept as null);
 *  - `ServeurLocal.inventaire` adds it to its JSON answer as `"arbres": [ ... ]` (same maps, by `uuid`),
 *    next to `detections` and `centres`. (ServeurLocal needs android.database: its glue is not tested
 *    here, all the logic is in `arbresSaisis`.)
 */
class ArbresSaisisTest {
    private val U = "00000000-0000-4000-8000-000000000014"
    private val V = "00000000-0000-4000-8000-000000000021"
    private val W = "00000000-0000-4000-8000-000000000009"

    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    private fun arbre(db: SqliteJdbc, uuid: String) = db.requete("SELECT * FROM arbres WHERE uuid = ?", listOf(uuid)).single()
    private fun d(v: Any?) = (v as Number).toDouble()

    // --- déclencheurs ----------------------------------------------------------------------

    @Test
    fun colonneDbhSourceEtDefautEstime() = exemple { db ->
        assertTrue(db.requete("SELECT * FROM arbres").all { it["dbh_source"] == "estime" })
        db.executer("UPDATE arbres SET dbh_source = 'mesure' WHERE uuid = ?", listOf(U))
        assertEquals("mesure", arbre(db, U)["dbh_source"])
    }

    @Test
    fun detectionSansSaisieInchangee() = exemple { db ->
        db.executer("UPDATE detections SET dbh_cm = 51.0, distance_m = 7.5 WHERE uuid = ?", listOf(U))
        val a = arbre(db, U)
        assertEquals(51.0, d(a["dbh_cm"]), 1e-9)
        assertEquals(7.5, d(a["distance_m"]), 1e-9)
        assertEquals("estime", a["dbh_source"])
    }

    @Test
    fun detectionNouvelleCreeUnArbreEstime() = exemple { db ->
        val det = db.requete("SELECT * FROM detections WHERE uuid = ?", listOf(U)).single()
        val p = det["plot_id"] as String
        val x = GeoPackage.lirePoints(db, "detections", p).first { it["uuid"] == U }
        val nouveau = Detection(uuid = "nouveau-17", plotId = p, x = x["x"] as Double + 1, y = x["y"] as Double + 1, numProvisoire = "99",
            azimut = 10.0, distanceM = 3.0, dbhCm = 21.0, hM = null, hSource = "aucune", houppierId = null, etatDetection = "provisoire", methode = "embarque", score = null)
        assertEquals(1 to 0, GeoPackage.ecrireDetections(db, listOf(nouveau)))
        assertEquals("estime", arbre(db, "nouveau-17")["dbh_source"])
    }

    @Test
    fun diametreMesureEtSaisiesJamaisEcrasesParUneDetection() = exemple { db ->
        db.executer("UPDATE arbres SET dbh_cm = 42.5, dbh_source = 'mesure', essence_arbre = 'hetre', statut = 'mort', qualite = 'B' WHERE uuid = ?", listOf(U))
        db.executer("UPDATE detections SET dbh_cm = 60.0, num_provisoire = '77', distance_m = 9.0 WHERE uuid = ?", listOf(U))
        val a = arbre(db, U)
        assertEquals(42.5, d(a["dbh_cm"]), 1e-9)
        assertEquals("mesure", a["dbh_source"])
        assertEquals("hetre", a["essence_arbre"]); assertEquals("mort", a["statut"]); assertEquals("B", a["qualite"])
        assertEquals("77", a["tree_id"]); assertEquals(9.0, d(a["distance_m"]), 1e-9) // the rest follows the phone
    }

    @Test
    fun unArbreMesureNeProtegePasLesAutres() = exemple { db ->
        db.executer("UPDATE arbres SET dbh_cm = 42.5, dbh_source = 'mesure' WHERE uuid = ?", listOf(U))
        db.executer("UPDATE detections SET dbh_cm = 60.0 WHERE uuid IN (?, ?)", listOf(U, V))
        assertEquals(42.5, d(arbre(db, U)["dbh_cm"]), 1e-9)
        assertEquals(60.0, d(arbre(db, V)["dbh_cm"]), 1e-9)
    }

    @Test
    fun saisieViaModifierArbreResisteALaDetectionSuivante() = exemple { db ->
        GeoPackage.modifierArbre(db, U, mapOf("dbh_cm" to "38", "essence_arbre" to "charme"))
        db.executer("UPDATE detections SET dbh_cm = 55.0 WHERE uuid = ?", listOf(U))
        val a = arbre(db, U)
        assertEquals(38.0, d(a["dbh_cm"]), 1e-9); assertEquals("charme", a["essence_arbre"])
    }

    // --- données envoyées au serveur local -----------------------------------------------------

    @Test
    fun arbresSaisisRenvoieLesAttributsParUuid() = exemple { db ->
        db.executer("UPDATE arbres SET essence_arbre = NULL, qualite = NULL, statut = 'vivant', dbh_source = 'estime'")
        assertTrue(GeoPackage.arbresSaisis(db).isEmpty(), "rien saisi : rien à envoyer")

        GeoPackage.modifierArbre(db, U, mapOf("essence_arbre" to "hetre", "dbh_cm" to "47.5", "statut" to "mort", "qualite" to "B"))
        GeoPackage.modifierArbre(db, V, mapOf("qualite" to "C"))
        db.executer("UPDATE arbres SET statut = 'chablis' WHERE uuid = ?", listOf(W))

        val par = GeoPackage.arbresSaisis(db).associateBy { it["uuid"] }
        assertEquals(setOf(U, V, W), par.keys)
        val cles = setOf("uuid", "essence_arbre", "dbh_cm", "dbh_source", "statut", "qualite")
        assertTrue(par.values.all { it.keys == cles })

        val u = par.getValue(U)
        assertEquals("hetre", u["essence_arbre"]); assertEquals(47.5, d(u["dbh_cm"]), 1e-9)
        assertEquals("mesure", u["dbh_source"]); assertEquals("mort", u["statut"]); assertEquals("B", u["qualite"])

        val v = par.getValue(V)
        assertEquals("C", v["qualite"]); assertNull(v["essence_arbre"]); assertEquals("estime", v["dbh_source"]); assertEquals("vivant", v["statut"])

        assertEquals("chablis", par.getValue(W)["statut"])
        assertFalse(par.containsKey("00000000-0000-4000-8000-000000000024"))
    }
}
