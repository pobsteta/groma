package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Mission 017: entering the attributes of a tree in Groma (essence, tape diameter, status, quality).
 *
 * API EXPECTED FROM MISSION 018 (names and signatures fixed here, in `groma-core`):
 *
 * ```
 * object SaisieArbre {
 *     /** The four attributes entered in Groma, with the column of `arbres` they write. */
 *     val CHAMPS: List<String>  // ["essence_arbre", "dbh_cm", "statut", "qualite"]
 *
 *     /**
 *      * Checks one value typed by the operator (raw text, null or blank = empty).
 *      * Returns null when valid, otherwise a non-blank French message.
 *      * - essence_arbre: empty, or a code of essences_bdforet_v2.json;
 *      * - dbh_cm: empty, or a number in [0, 1000] (comma or point decimal separator);
 *      * - statut: empty (means `vivant`), or one of vivant/mort/chablis/coupe;
 *      * - qualite: empty, or one of A/B/C/D;
 *      * - any other field name: invalid.
 *      */
 *     fun controler(champ: String, valeur: String?): String?
 * }
 *
 * // In object GeoPackage (same as lirePoints, ecrireDetections...):
 * /**
 *  * Writes the entered values of ONE tree (by uuid) into `arbres`, in one immediate transaction.
 *  * `saisie` maps a field of SaisieArbre.CHAMPS to the raw text typed; absent key = column untouched.
 *  * Empty value (null or blank) = erase that attribute only:
 *  *   essence_arbre -> NULL, qualite -> NULL, statut -> 'vivant',
 *  *   dbh_cm -> the estimate of the matching `detections` row, with dbh_source = 'estime'.
 *  * Non-empty dbh_cm -> dbh_source = 'mesure'.
 *  * Throws IllegalArgumentException (nothing written) if one value fails SaisieArbre.controler
 *  * or if the uuid is unknown.
 *  */
 * fun GeoPackage.modifierArbre(db: BaseSqlite, uuid: String, saisie: Map<String, String?>)
 * ```
 */
class SaisieArbreTest {
    private val U = "00000000-0000-4000-8000-000000000014"
    private val AUTRE = "00000000-0000-4000-8000-000000000021"

    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    private fun arbre(db: SqliteJdbc, uuid: String) = db.requete("SELECT * FROM arbres WHERE uuid = ?", listOf(uuid)).single()

    // --- contrôle des valeurs -------------------------------------------------------------

    @Test
    fun valeursValides() {
        for ((champ, valeur) in listOf(
            "essence_arbre" to "hetre", "essence_arbre" to "chene_sessile", "essence_arbre" to null, "essence_arbre" to "",
            "dbh_cm" to "0", "dbh_cm" to "45", "dbh_cm" to "45.5", "dbh_cm" to "45,5", "dbh_cm" to "1000", "dbh_cm" to null, "dbh_cm" to "  ",
            "statut" to "vivant", "statut" to "mort", "statut" to "chablis", "statut" to "coupe", "statut" to null,
            "qualite" to "A", "qualite" to "D", "qualite" to null, "qualite" to "",
        )) assertNull(SaisieArbre.controler(champ, valeur), "$champ=$valeur devrait être accepté")
    }

    @Test
    fun valeursHorsReferentiel() {
        for ((champ, valeur) in listOf(
            "essence_arbre" to "sapin_magique", "essence_arbre" to "Hetre", "essence_arbre" to "hêtre",
            "dbh_cm" to "-1", "dbh_cm" to "1000.1", "dbh_cm" to "abc", "dbh_cm" to "NaN", "dbh_cm" to "12cm",
            "statut" to "mourant", "statut" to "Vivant",
            "qualite" to "E", "qualite" to "a",
            "hauteur" to "12", "dbh_source" to "mesure", "uuid" to "x",
        )) {
            val m = SaisieArbre.controler(champ, valeur)
            assertNotNull(m, "$champ=$valeur devrait être refusé")
            assert(m.isNotBlank())
        }
    }

    @Test
    fun lesChampsSaisis() {
        assertEquals(listOf("essence_arbre", "dbh_cm", "statut", "qualite"), SaisieArbre.CHAMPS)
    }

    // --- écriture dans arbres --------------------------------------------------------------

    @Test
    fun saisieComplete() = exemple { db ->
        val avant = arbre(db, U)
        val autreAvant = arbre(db, AUTRE)
        GeoPackage.modifierArbre(db, U, mapOf("essence_arbre" to "hetre", "dbh_cm" to "47,5", "statut" to "mort", "qualite" to "B"))
        val a = arbre(db, U)
        assertEquals("hetre", a["essence_arbre"])
        assertEquals(47.5, (a["dbh_cm"] as Number).toDouble(), 1e-9)
        assertEquals("mesure", a["dbh_source"])
        assertEquals("mort", a["statut"])
        assertEquals("B", a["qualite"])
        // Everything else is untouched, and so is every other tree.
        for (k in avant.keys - setOf("essence_arbre", "dbh_cm", "dbh_source", "statut", "qualite")) assertEquals(avant[k], a[k], k)
        assertEquals(autreAvant, arbre(db, AUTRE))
    }

    @Test
    fun seulesLesColonnesSaisiesChangent() = exemple { db ->
        db.executer("UPDATE arbres SET essence_arbre = 'charme', qualite = 'C', statut = 'chablis' WHERE uuid = ?", listOf(U))
        val avant = arbre(db, U)
        GeoPackage.modifierArbre(db, U, mapOf("qualite" to "A"))
        val a = arbre(db, U)
        assertEquals("A", a["qualite"])
        for (k in avant.keys - "qualite") assertEquals(avant[k], a[k], k)
        assertEquals(avant["dbh_source"], a["dbh_source"], "pas de diamètre saisi : dbh_source inchangé")
    }

    @Test
    fun diametreSaisiPasseEnMesure() = exemple { db ->
        db.executer("UPDATE arbres SET dbh_source = 'estime' WHERE uuid = ?", listOf(U))
        GeoPackage.modifierArbre(db, U, mapOf("dbh_cm" to "0"))
        val a = arbre(db, U)
        assertEquals(0.0, (a["dbh_cm"] as Number).toDouble(), 0.0)
        assertEquals("mesure", a["dbh_source"])
    }

    @Test
    fun valeurVideEfface_cetAttributSeulement() = exemple { db ->
        GeoPackage.modifierArbre(db, U, mapOf("essence_arbre" to "hetre", "qualite" to "B", "statut" to "mort", "dbh_cm" to "47.5"))
        GeoPackage.modifierArbre(db, U, mapOf("essence_arbre" to ""))
        var a = arbre(db, U)
        assertNull(a["essence_arbre"])
        assertEquals("B", a["qualite"]); assertEquals("mort", a["statut"])
        assertEquals(47.5, (a["dbh_cm"] as Number).toDouble(), 1e-9); assertEquals("mesure", a["dbh_source"])

        GeoPackage.modifierArbre(db, U, mapOf("qualite" to null))
        a = arbre(db, U)
        assertNull(a["qualite"]); assertEquals("mort", a["statut"])

        GeoPackage.modifierArbre(db, U, mapOf("statut" to " "))
        a = arbre(db, U)
        assertEquals("vivant", a["statut"]); assertEquals("mesure", a["dbh_source"])
    }

    @Test
    fun diametreVideRevientALEstimation() = exemple { db ->
        val estime = (db.requete("SELECT dbh_cm FROM detections WHERE uuid = ?", listOf(U)).single()["dbh_cm"] as Number).toDouble()
        GeoPackage.modifierArbre(db, U, mapOf("dbh_cm" to "99", "essence_arbre" to "hetre"))
        GeoPackage.modifierArbre(db, U, mapOf("dbh_cm" to ""))
        val a = arbre(db, U)
        assertEquals(estime, (a["dbh_cm"] as Number).toDouble(), 1e-9)
        assertEquals("estime", a["dbh_source"])
        assertEquals("hetre", a["essence_arbre"])
    }

    @Test
    fun valeurInvalideNEcritRien() = exemple { db ->
        val avant = arbre(db, U)
        assertFailsWith<IllegalArgumentException> {
            GeoPackage.modifierArbre(db, U, mapOf("essence_arbre" to "hetre", "dbh_cm" to "1500"))
        }
        assertFailsWith<IllegalArgumentException> {
            GeoPackage.modifierArbre(db, U, mapOf("qualite" to "Z", "statut" to "mort"))
        }
        assertEquals(avant, arbre(db, U), "transaction annulée : ni essence ni statut écrits")
    }

    @Test
    fun uuidInconnu() = exemple { db ->
        assertFailsWith<IllegalArgumentException> { GeoPackage.modifierArbre(db, "inconnu", mapOf("qualite" to "A")) }
    }
}
