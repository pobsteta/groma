package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Mission 016: writing a target placed in Groma to `cibles` (replace, R*Tree, source_azimut). */
class EcritureCibleTest {
    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    private fun declencheurs(db: SqliteJdbc) =
        db.requete("SELECT name, sql FROM sqlite_master WHERE type = 'trigger' AND tbl_name = 'cibles' ORDER BY name")

    private fun compte(db: SqliteJdbc) =
        (db.requete("SELECT feature_count FROM gpkg_ogr_contents WHERE table_name = 'cibles'").single()["feature_count"] as Number).toLong()

    @Test
    fun ecritureEtRemplacement() = exemple { db ->
        val avant = declencheurs(db)
        val lignes0 = GeoPackage.lirePoints(db, "cibles", "P002").size
        val n0 = compte(db)
        val c = CiblePlacee("C2", 130.0, 11.0, 41.0, SourceAzimut.BOUSSOLE_TELEPHONE)
        val fid1 = GeoPackage.ecrireCible(db, "P002", c, 925010.0, 6700020.0, 144.4444)
        assertEquals(avant, declencheurs(db), "déclencheurs inchangés (aucun index R*Tree)")
        val l = GeoPackage.lirePoints(db, "cibles", "P002").filter { it["id_cible"] == "C2" }
        assertEquals(1, l.size)
        assertEquals("boussole_telephone", l.single()["source_azimut"])
        assertEquals(144.4444, (l.single()["azimut_mesure"] as Number).toDouble(), 1e-9)
        assertEquals(925010.0, l.single()["x"] as Double, 1e-6)
        assertEquals(lignes0, GeoPackage.lirePoints(db, "cibles", "P002").size)

        // Replaced: still one row, old R*Tree row gone, other targets of the plot kept.
        val fid2 = GeoPackage.ecrireCible(db, "P002", c.copy(distanceM = 12.0, source = SourceAzimut.BOUSSOLE_VISEE), 925011.0, 6700021.0, 144.0)
        assertNotEquals(fid1, fid2)
        val l2 = GeoPackage.lirePoints(db, "cibles", "P002").filter { it["id_cible"] == "C2" }
        assertEquals(1, l2.size)
        assertEquals(12.0, (l2.single()["distance_mesuree_m"] as Number).toDouble(), 0.0)
        assertEquals("boussole_visee", l2.single()["source_azimut"])
        assertEquals(lignes0, GeoPackage.lirePoints(db, "cibles", "P002").size)
        assertEquals(n0, compte(db))
        assertEquals(avant, declencheurs(db))
    }

    @Test
    fun autrePlacetteIntacte() = exemple { db ->
        val p1 = GeoPackage.lirePoints(db, "cibles", "P001")
        GeoPackage.ecrireCible(db, "P002", CiblePlacee("C1", 10.0, 9.0, 30.0, SourceAzimut.BOUSSOLE_VISEE), 1.0, 2.0, 10.0)
        assertEquals(p1, GeoPackage.lirePoints(db, "cibles", "P001"))
    }

    @Test
    fun projetAvant1_3_0_recoitLaColonne() = exemple { db ->
        db.executer("ALTER TABLE cibles DROP COLUMN source_azimut")
        GeoPackage.ecrireCible(db, "P002", CiblePlacee("C3", 10.0, 9.0, 30.0, SourceAzimut.BOUSSOLE_VISEE), 1.0, 2.0, 10.0)
        assertEquals("boussole_visee", GeoPackage.lirePoints(db, "cibles", "P002").single { it["id_cible"] == "C3" }["source_azimut"])
    }
}
