package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MesureCentreTest {
    private fun fix(lat: Double, lon: Double, qualite: QualiteFix = QualiteFix.RTK_FIXE, precision: Double? = 0.02, origine: OrigineFix = OrigineFix.RECEPTEUR) =
        FixGnss(Position(lat, lon), qualite, 20, 0.6, 300.0, 1.0, precision, origine = origine)

    @Test
    fun autorisation_selon_le_fix() {
        assertEquals(MesureCentre.Autorisation.SANS_POSITION, MesureCentre.autorisation(null))
        assertEquals(MesureCentre.Autorisation.OUI, MesureCentre.autorisation(fix(47.0, 5.0)))
        assertEquals(MesureCentre.Autorisation.CONFIRMER_FLOTTANT, MesureCentre.autorisation(fix(47.0, 5.0, QualiteFix.RTK_FLOTTANT, 0.4)))
        assertEquals(MesureCentre.Autorisation.SANS_RTK, MesureCentre.autorisation(fix(47.0, 5.0, QualiteFix.DGPS, 0.8)))
        // The phone's GNSS, even when it claims 3 cm, is refused.
        assertEquals(MesureCentre.Autorisation.TELEPHONE, MesureCentre.autorisation(fix(47.0, 5.0, QualiteFix.RTK_FIXE, 0.03, OrigineFix.TELEPHONE)))
    }

    @Test
    fun moyenne_des_fix_fixes_seulement() {
        val m = MesureCentre(accepterFlottant = false)
        assertTrue(m.ajouter(fix(47.0, 5.0)))
        assertTrue(m.ajouter(fix(47.00002, 5.0)))
        assertFalse(m.ajouter(fix(47.1, 5.0, QualiteFix.RTK_FLOTTANT, 0.4)))
        assertFalse(m.ajouter(fix(47.1, 5.0, origine = OrigineFix.TELEPHONE)))
        val c = m.resultat()!!
        assertEquals(2, c.nbFixes)
        assertEquals(47.00001, c.latitude, 1e-9)
        assertEquals("fix", c.qualite)
        // Fixes 2.2 m apart: the spread (1.1 m RMS) wins over the announced 2 cm.
        assertEquals(1.11, c.precisionM, 0.01)
    }

    @Test
    fun precision_annoncee_si_les_fix_sont_groupes() {
        val m = MesureCentre(accepterFlottant = true)
        m.ajouter(fix(47.0, 5.0, precision = 0.02))
        m.ajouter(fix(47.0, 5.0, QualiteFix.RTK_FLOTTANT, precision = 0.4))
        val c = m.resultat()!!
        assertEquals("float", c.qualite)
        // RMS of 0.02 and 0.4.
        assertEquals(0.2832, c.precisionM, 1e-4)
    }

    @Test
    fun sans_fix_accepte_pas_de_centre() {
        assertNull(MesureCentre(accepterFlottant = false).resultat())
    }

    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    private fun declencheurs(db: SqliteJdbc) =
        db.requete("SELECT name, sql FROM sqlite_master WHERE type = 'trigger' AND tbl_name = 'centres_mesures' ORDER BY name")

    @Test
    fun ecriture_malgre_les_declencheurs_rtree() = exemple { db ->
        // Like Android's SQLite, this one has no ST_IsEmpty: a plain insert fails.
        assertFailsWith<Exception> {
            db.executer("INSERT INTO centres_mesures (geom, plot_id) VALUES (?, 'P002')", listOf(GeoPackage.encoderPoint(1.0, 2.0)))
        }
        val avant = declencheurs(db)
        val p2 = GeoPackage.lirePoints(db, "placettes", "P002").single()
        val x = (p2["x"] as Double) + 3.0
        val y = (p2["y"] as Double) + 4.0
        val fid = GeoPackage.ajouterCentreMesure(db, "P002", x, y, "fix", 0.02, 5.0, "2026-10-15T10:00:00Z")
        // Triggers back unchanged, R*Tree row written, feature count kept by GDAL's trigger.
        assertEquals(avant, declencheurs(db))
        val r = db.requete("SELECT minx, maxx, miny, maxy FROM rtree_centres_mesures_geom WHERE id = ?", listOf(fid)).single()
        assertEquals(x, (r["minx"] as Number).toDouble(), 0.1)
        assertEquals(y, (r["maxy"] as Number).toDouble(), 0.1)
        assertEquals(3L, (db.requete("SELECT feature_count FROM gpkg_ogr_contents WHERE table_name = 'centres_mesures'").single()["feature_count"] as Number).toLong())
        // The earlier centre of P002 is kept; the map and the capture use the latest one.
        assertEquals(2, GeoPackage.lirePoints(db, "centres_mesures", "P002").size)
        val dernier = GeoPackage.dernierCentreParPlacette(db, "P002").single()
        assertEquals(fid, (dernier["fid"] as Number).toLong())
        assertEquals("fix", dernier["qualite_gnss"])
        val centre = DonneesCarte.lire(db).centres.single { it.plotId == "P002" }
        assertEquals(5.0, centre.ecartM!!, 1e-6)
    }

    @Test
    fun ecriture_sans_index_spatial() = exemple { db ->
        // A GeoPackage without the R*Tree (or prepared otherwise): plain insert.
        for (d in declencheurs(db)) if ((d["name"] as String).startsWith("rtree_")) db.executer("DROP TRIGGER \"${d["name"]}\"")
        db.executer("DROP TABLE rtree_centres_mesures_geom")
        val fid = GeoPackage.ajouterCentreMesure(db, "P001", 1.0, 2.0, "float", 0.4, null)
        assertEquals("P001", GeoPackage.dernierCentreParPlacette(db, "P001").single()["plot_id"])
        assertEquals(fid, (GeoPackage.dernierCentreParPlacette(db, "P001").single()["fid"] as Number).toLong())
    }
}
