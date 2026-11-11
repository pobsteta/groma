package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mission 023: complements AjouterArbreTest (project before 1.4.0, flag of the plan). */
class AjouterArbreProjetAncienTest {
    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    @Test
    fun projetSansDbhSourceMigreALAjout() = exemple { db ->
        db.executer("DROP TRIGGER trg_detections_insert"); db.executer("DROP TRIGGER trg_detections_update")
        db.executer("ALTER TABLE arbres DROP COLUMN dbh_source")
        val uuid = GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0)
        val a = db.requete("SELECT dbh_source, methode FROM arbres WHERE uuid = ?", listOf(uuid)).single()
        assertEquals("mesure", a["dbh_source"]); assertEquals("terrain", a["methode"])
    }

    @Test
    fun lePlanDistingueLesArbresAjoutes() = exemple { db ->
        val uuid = GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 38.0)
        val liste = ArbrePlacette.lire(db, "P001")
        assertTrue(liste.single { it.uuid == uuid }.ajoute)
        assertEquals(18, liste.count { !it.ajoute })
        assertFalse(liste.filter { it.uuid != uuid }.any { it.ajoute })
    }
}
