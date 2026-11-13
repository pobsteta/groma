package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Mission 031: the targets follow C0 surveyed again. Expected positions written by hand
 * (Lambert-93, x east, y north):
 * - new C0 (925003, 6700004); C1 at 90° / 10 m -> (925013, 6700004); C2 at 0° / 5 m -> (925003, 6700009);
 * - with declination 2° east, a magnetic 88° is geographic 90°: same points;
 * - in grades, 100 gr = 90°: same point;
 * - azimuth, distance, diameter and source of each target unchanged; a target without
 *   distance, the other plots and the centres already surveyed untouched.
 * Mission 032, trees added by hand, first C0 (925000, 6700000), new C0 (925003, 6700004):
 * - declination 0, convergence 0: magnetic 90° / 10 m -> (925013, 6700004);
 * - declination 2°, convergence 2°: magnetic 90° = geographic 92° (stored) = grid 90°: same point;
 * - the 18 trees of the video (P001) never move, nor any attribute of the tree added.
 */
class RecalageCiblesTest {
    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    private fun cible(db: SqliteJdbc, plot: String, id: String) =
        GeoPackage.lirePoints(db, "cibles", plot).single { it["id_cible"] == id }

    private fun preparer(db: SqliteJdbc, azC1: Double, azC2: Double) {
        db.executer("DELETE FROM cibles WHERE plot_id = 'P002'")
        // Placed from an old C0 (925000, 6700000).
        GeoPackage.ecrireCible(db, "P002", CiblePlacee("C1", azC1, 10.0, 41.0, SourceAzimut.BOUSSOLE_TELEPHONE), 925010.0, 6700000.0, azC1)
        GeoPackage.ecrireCible(db, "P002", CiblePlacee("C2", azC2, 5.0, 33.5, SourceAzimut.BOUSSOLE_VISEE), 925000.0, 6700005.0, azC2)
    }

    @Test
    fun ciblesSuiventLeNouveauC0() = exemple { db ->
        preparer(db, 90.0, 0.0)
        val autre = GeoPackage.lirePoints(db, "cibles", "P001")
        val centres = GeoPackage.lirePoints(db, "centres_mesures")
        val r = GeoPackage.releverC0(db, "P002", 925003.0, 6700004.0, "fix", 0.02, 1.5, OrientationPlacette("deg", 0.0, 0.0))
        assertEquals(listOf("C1", "C2"), r.ciblesRecalees)
        val c1 = cible(db, "P002", "C1")
        assertEquals(925013.0, c1["x"] as Double, 1e-6)
        assertEquals(6700004.0, c1["y"] as Double, 1e-6)
        val c2 = cible(db, "P002", "C2")
        assertEquals(925003.0, c2["x"] as Double, 1e-6)
        assertEquals(6700009.0, c2["y"] as Double, 1e-6)
        // Field attributes kept (rule 5).
        assertEquals(90.0, (c1["azimut_mesure"] as Number).toDouble(), 0.0)
        assertEquals(10.0, (c1["distance_mesuree_m"] as Number).toDouble(), 0.0)
        assertEquals(41.0, (c1["dbh_mesure_cm"] as Number).toDouble(), 0.0)
        assertEquals("boussole_telephone", c1["source_azimut"])
        assertEquals(33.5, (c2["dbh_mesure_cm"] as Number).toDouble(), 0.0)
        assertEquals("boussole_visee", c2["source_azimut"])
        // Other plot untouched, the centre appended (never rewritten).
        assertEquals(autre, GeoPackage.lirePoints(db, "cibles", "P001"))
        val apres = GeoPackage.lirePoints(db, "centres_mesures")
        assertEquals(centres.size + 1, apres.size)
        assertEquals(centres, apres.filter { (it["fid"] as Number).toLong() != r.fid })
        val dernier = GeoPackage.dernierCentreParPlacette(db, "P002").single()
        assertEquals(925003.0, dernier["x"] as Double, 1e-6)
    }

    @Test
    fun declinaisonEtGrades() = exemple { db ->
        preparer(db, 88.0, 358.0)
        GeoPackage.releverC0(db, "P002", 925003.0, 6700004.0, "fix", 0.02, null, OrientationPlacette("deg", 2.0, 0.0))
        assertEquals(925013.0, cible(db, "P002", "C1")["x"] as Double, 1e-6)
        assertEquals(6700004.0, cible(db, "P002", "C1")["y"] as Double, 1e-6)
        assertEquals(925003.0, cible(db, "P002", "C2")["x"] as Double, 1e-6)
        assertEquals(6700009.0, cible(db, "P002", "C2")["y"] as Double, 1e-6)

        preparer(db, 100.0, 0.0)
        GeoPackage.releverC0(db, "P002", 925003.0, 6700004.0, "fix", 0.02, null, OrientationPlacette("gr", 0.0, 0.0))
        assertEquals(925013.0, cible(db, "P002", "C1")["x"] as Double, 1e-6)
        assertEquals(6700004.0, cible(db, "P002", "C1")["y"] as Double, 1e-6)
    }

    @Test
    fun cibleSansDistanceIntacte() = exemple { db ->
        preparer(db, 90.0, 0.0)
        db.executer("UPDATE cibles SET distance_mesuree_m = NULL WHERE plot_id = 'P002' AND id_cible = 'C2'")
        val r = GeoPackage.releverC0(db, "P002", 925003.0, 6700004.0, "fix", 0.02, null, OrientationPlacette("deg", 0.0, 0.0))
        assertEquals(listOf("C1"), r.ciblesRecalees)
        val c2 = cible(db, "P002", "C2")
        assertEquals(925000.0, c2["x"] as Double, 1e-6)
        assertEquals(6700005.0, c2["y"] as Double, 1e-6)
        assertNull(c2["distance_mesuree_m"])
    }

    @Test
    fun sansCibleRienARecaler() = exemple { db ->
        db.executer("DELETE FROM cibles WHERE plot_id = 'P002'")
        val r = GeoPackage.releverC0(db, "P002", 925003.0, 6700004.0, "telephone", 3.0, null, OrientationPlacette("deg", 0.0, 0.0))
        assertEquals(emptyList(), r.ciblesRecalees)
        assertEquals("telephone", GeoPackage.dernierCentreParPlacette(db, "P002").single()["qualite_gnss"])
    }

    private fun arbre(db: SqliteJdbc, uuid: String) =
        GeoPackage.lirePoints(db, "arbres").single { it["uuid"] == uuid }

    @Test
    fun arbresAjoutesSuivent() = exemple { db ->
        for ((decl, conv) in listOf(0.0 to 0.0, 2.0 to 2.0)) {
            db.executer("UPDATE placettes SET unite_azimut = 'deg', declinaison_deg = ?, convergence_deg = ? WHERE plot_id = 'P002'", listOf(decl, conv))
            GeoPackage.ajouterCentreMesure(db, "P002", 925000.0, 6700000.0, "fix", 0.02, null)
            val uuid = GeoPackage.ajouterArbre(db, "P002", 90.0, 10.0, 37.5, "hetre", "vivant", "B")
            val avant = arbre(db, uuid)
            val r = GeoPackage.releverC0(db, "P002", 925003.0, 6700004.0, "fix", 0.02, null, OrientationPlacette("deg", decl, conv))
            assertEquals(true, avant["tree_id"] in r.arbresRecales)
            val apres = arbre(db, uuid)
            assertEquals(925013.0, apres["x"] as Double, 1e-6)
            assertEquals(6700004.0, apres["y"] as Double, 1e-6)
            assertEquals(avant - "x" - "y", apres - "x" - "y")
        }
    }

    @Test
    fun arbresDeLaVideoImmobiles() = exemple { db ->
        val avant = GeoPackage.lirePoints(db, "arbres", "P001")
        val r = GeoPackage.releverC0(db, "P001", 925103.0, 6700104.0, "fix", 0.02, null, OrientationPlacette("deg", 2.4, 2.163577))
        assertEquals(emptyList(), r.arbresRecales)
        assertEquals(avant, GeoPackage.lirePoints(db, "arbres", "P001"))
    }
}
