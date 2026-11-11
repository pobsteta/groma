package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mission 018: a project prepared before schema 1.4.0 (no `arbres.dbh_source`, triggers of 1.3.0)
 * is migrated by the first `modifierArbre`: column added, triggers recreated as in `declencheurs.sql`.
 */
class MigrationDbhSourceTest {
    private val U = "00000000-0000-4000-8000-000000000014"
    private val V = "00000000-0000-4000-8000-000000000021"

    // Triggers of schema 1.3.0 (before this mission), written here as the old project has them.
    private val INSERT_130 = """CREATE TRIGGER trg_detections_insert AFTER INSERT ON detections
        WHEN NEW.etat_detection IS NOT 'faux_positif' AND NOT EXISTS (SELECT 1 FROM arbres WHERE uuid = NEW.uuid)
        BEGIN
          INSERT INTO arbres (geom, plot_id, tree_id, dbh_cm, h_m, statut, uuid, num_provisoire, azimut, distance_m, h_source, houppier_id, etat_detection, methode)
          VALUES (NEW.geom, NEW.plot_id, NEW.num_provisoire, NEW.dbh_cm, NEW.h_m, 'vivant', NEW.uuid, NEW.num_provisoire, NEW.azimut, NEW.distance_m,
                  COALESCE(NEW.h_source, 'aucune'), NEW.houppier_id, COALESCE(NEW.etat_detection, 'provisoire'), NEW.methode);
        END"""
    private val UPDATE_130 = """CREATE TRIGGER trg_detections_update AFTER UPDATE ON detections
        BEGIN
          UPDATE arbres SET geom = NEW.geom, dbh_cm = NEW.dbh_cm, azimut = NEW.azimut, distance_m = NEW.distance_m,
            num_provisoire = NEW.num_provisoire, tree_id = NEW.num_provisoire, houppier_id = NEW.houppier_id,
            h_m = CASE WHEN h_source = 'mesuree' THEN h_m ELSE NEW.h_m END,
            h_source = CASE WHEN h_source = 'mesuree' THEN h_source ELSE COALESCE(NEW.h_source, 'aucune') END,
            etat_detection = CASE WHEN NEW.etat_detection = 'faux_positif' THEN etat_detection ELSE NEW.etat_detection END
          WHERE uuid = NEW.uuid AND etat_detection IN ('provisoire', 'a_verifier');
        END"""

    private fun ancien(bloc: (SqliteJdbc, List<Map<String, Any?>>) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use {
            val db = SqliteJdbc(it)
            val attendus = triggers(db)  // from declencheurs.sql (example regenerated in 1.4.0)
            db.executer("DROP TRIGGER trg_detections_insert")
            db.executer("DROP TRIGGER trg_detections_update")
            db.executer("ALTER TABLE arbres DROP COLUMN dbh_source")
            db.executer(INSERT_130)
            db.executer(UPDATE_130)
            assertTrue("dbh_source" !in colonnes(db))
            bloc(db, attendus)
        }
    }

    private fun colonnes(db: SqliteJdbc) = db.requete("PRAGMA table_info(arbres)").map { it["name"] }
    private fun triggers(db: SqliteJdbc) =
        db.requete("SELECT name, sql FROM sqlite_master WHERE type = 'trigger' AND name IN ('trg_detections_insert', 'trg_detections_update') ORDER BY name")
    private fun arbre(db: SqliteJdbc, uuid: String) = db.requete("SELECT * FROM arbres WHERE uuid = ?", listOf(uuid)).single()

    @Test
    fun migrationALaPremiereSaisie() = ancien { db, attendus ->
        assertEquals(2, attendus.size)
        GeoPackage.modifierArbre(db, U, mapOf("dbh_cm" to "42,5", "essence_arbre" to "hetre"))
        assertTrue("dbh_source" in colonnes(db))
        assertEquals(attendus, triggers(db), "déclencheurs identiques à declencheurs.sql")
        assertEquals("mesure", arbre(db, U)["dbh_source"])
        assertEquals("estime", arbre(db, V)["dbh_source"])
        assertTrue(db.requete("SELECT dbh_source FROM arbres").filter { it["dbh_source"] == "mesure" }.size == 1, "les autres lignes sont estime")

        // The tape diameter survives a new detection, the other tree follows the phone.
        db.executer("UPDATE detections SET dbh_cm = 60.0 WHERE uuid IN (?, ?)", listOf(U, V))
        assertEquals(42.5, (arbre(db, U)["dbh_cm"] as Number).toDouble(), 1e-9)
        assertEquals(60.0, (arbre(db, V)["dbh_cm"] as Number).toDouble(), 1e-9)
    }

    @Test
    fun saisieSansDiametreMigreAussi() = ancien { db, attendus ->
        GeoPackage.modifierArbre(db, U, mapOf("qualite" to "B"))
        assertTrue("dbh_source" in colonnes(db))
        assertEquals(attendus, triggers(db))
        assertEquals("estime", arbre(db, U)["dbh_source"])
    }
}
