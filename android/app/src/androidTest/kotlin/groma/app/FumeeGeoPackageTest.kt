package groma.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import groma.core.CiblePlacee
import groma.core.Detection
import groma.core.ExportTerrain
import groma.core.GeoPackage
import groma.core.OrientationPlacette
import groma.core.SourceAzimut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * Smoke test (mission 026, modelled on Surculus ADR 0011): the write code of `groma-core` played on the SQLite of
 * ANDROID (version, no ST_* functions, compat WAL), which no JVM test (sqlite-jdbc) can show. Played on an emulator
 * (release.yml) and on the phone, always under `fr.nemeton.groma.fumee`, never on the installed app.
 *
 * Source: a copy of exemples/inventaire_video/inventaire_video.gpkg (assets of the test APK).
 * Values written by hand from that file: plots P001 and P002 (unit deg, declination 2.4, convergence 2.163577);
 * 18 trees (uuid 00000000-0000-4000-8000-0000000000NN) all of P001, methode 'embarque'; 2 measured centres; 6 targets.
 */
@RunWith(AndroidJUnit4::class)
class FumeeGeoPackageTest {
    private val arbre14 = "00000000-0000-4000-8000-000000000014" // 43.2 cm estimated
    private val arbre21 = "00000000-0000-4000-8000-000000000021" // 33.7 cm estimated

    private lateinit var dossier: File
    private lateinit var gpkg: File

    @Before
    fun preparer() {
        val instr = InstrumentationRegistry.getInstrumentation()
        dossier = File(instr.targetContext.cacheDir, "fumee").apply { deleteRecursively(); mkdirs() }
        gpkg = File(dossier, "inventaire_video.gpkg")
        instr.context.assets.open("inventaire_video.gpkg").use { e -> gpkg.outputStream().use { e.copyTo(it) } }
    }

    private fun ouvrir(f: File = gpkg, ecriture: Boolean = true) =
        SqliteAndroid(SQLiteDatabase.openDatabase(f.path, null, if (ecriture) SQLiteDatabase.OPEN_READWRITE else SQLiteDatabase.OPEN_READONLY))

    private fun ancien(): File = File(dossier, "projet_ancien.gpkg").also { f ->
        InstrumentationRegistry.getInstrumentation().context.assets.open("projet_ancien.gpkg").use { e -> f.outputStream().use { e.copyTo(it) } }
    }

    private fun nombre(db: SqliteAndroid, sql: String, args: List<Any?> = emptyList()) =
        (db.requete(sql, args).single().values.single() as Number).toInt()

    private fun declencheurs(db: SqliteAndroid) =
        db.requete("SELECT name, sql FROM sqlite_master WHERE type = 'trigger' ORDER BY name")

    @Test
    fun lePaquetPorteLeSuffixeFumee() {
        // The guard of the whole mission: without the suffix, the test could replace the installed app.
        val paquet = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assertEquals("fr.nemeton.groma.fumee", paquet)
    }

    @Test
    fun ouvertureEtLecture() {
        ouvrir(ecriture = false).use { db ->
            println("FUMEE sqlite_version=" + db.requete("SELECT sqlite_version() AS v").single()["v"])
            assertEquals(setOf("P001", "P002"), GeoPackage.lirePoints(db, "placettes").map { it["plot_id"] }.toSet())
            assertEquals(18, GeoPackage.lirePoints(db, "arbres").size)
            assertEquals(2, GeoPackage.lirePoints(db, "centres_mesures").size)
            assertNotNull(GeoPackage.lireProjet(db))
            // ADR 0001: no R*Tree on the layers the phone writes (Android's SQLite has no rtree module).
            assertEquals(0, nombre(db, "SELECT count(*) FROM sqlite_master WHERE name LIKE 'rtree_centres_mesures_%' OR name LIKE 'rtree_cibles_%'"))
            GeoPackage.arbresSaisis(db) // readable without error
        }
    }

    @Test
    fun declencheursDetectionsVersArbres_rubanConserve() {
        ouvrir().use { db ->
            // The tape diameter of a tree (dbh_source = mesure) survives a new detection; the other tree follows.
            GeoPackage.modifierArbre(db, arbre14, mapOf("dbh_cm" to "42,5"))
            db.executer("UPDATE detections SET dbh_cm = 60.0 WHERE uuid IN (?, ?)", listOf(arbre14, arbre21))
            val a14 = db.requete("SELECT dbh_cm, dbh_source FROM arbres WHERE uuid = ?", listOf(arbre14)).single()
            val a21 = db.requete("SELECT dbh_cm, dbh_source FROM arbres WHERE uuid = ?", listOf(arbre21)).single()
            assertEquals(42.5, (a14["dbh_cm"] as Number).toDouble(), 1e-9)
            assertEquals("mesure", a14["dbh_source"])
            assertEquals(60.0, (a21["dbh_cm"] as Number).toDouble(), 1e-9)
            assertEquals("estime", a21["dbh_source"])
            // A new detection becomes a tree through trg_detections_insert.
            val avant = nombre(db, "SELECT count(*) FROM arbres")
            GeoPackage.ecrireDetections(
                db,
                listOf(
                    Detection(
                        "00000000-0000-4000-8000-0000000000f1", "P002", 925100.0, 6700100.0, "9", 90.0, 10.0, 25.0, null,
                        "aucune", null, "provisoire", "embarque", null,
                    ),
                ),
            )
            assertEquals(avant + 1, nombre(db, "SELECT count(*) FROM arbres"))
        }
    }

    @Test
    fun ecrireCibleSansStIsEmpty() {
        ouvrir().use { db ->
            val avant = declencheurs(db)
            val fid = GeoPackage.ecrireCible(db, "P002", CiblePlacee("C3", 10.0, 9.0, 30.0, SourceAzimut.BOUSSOLE_VISEE), 925010.0, 6700020.0, 10.0)
            val c = GeoPackage.lirePoints(db, "cibles", "P002").single { it["id_cible"] == "C3" }
            assertEquals("boussole_visee", c["source_azimut"])
            assertEquals(30.0, (c["dbh_mesure_cm"] as Number).toDouble(), 1e-9)
            assertEquals(1, nombre(db, "SELECT count(*) FROM cibles WHERE fid = ?", listOf(fid)))
            assertEquals("déclencheurs remis tels quels", avant, declencheurs(db))
            // Placed again: replaced, never duplicated.
            GeoPackage.ecrireCible(db, "P002", CiblePlacee("C3", 20.0, 9.0, 31.0, SourceAzimut.BOUSSOLE_TELEPHONE), 925011.0, 6700021.0, 20.0)
            assertEquals(1, GeoPackage.lirePoints(db, "cibles", "P002").count { it["id_cible"] == "C3" })
        }
    }

    @Test
    fun ajouterCentreMesure() {
        ouvrir().use { db ->
            val avant = declencheurs(db)
            val fid = GeoPackage.ajouterCentreMesure(db, "P002", 925200.0, 6700200.0, "rtk_fix", 0.02, 1.5, "2026-11-11T10:00:00Z")
            assertEquals(3, nombre(db, "SELECT count(*) FROM centres_mesures"))
            assertEquals(1, nombre(db, "SELECT count(*) FROM centres_mesures WHERE fid = ?", listOf(fid)))
            val dernier = GeoPackage.dernierCentreParPlacette(db, "P002").single()
            assertEquals(925200.0, dernier["x"] as Double, 1e-6)
            assertEquals(6700200.0, dernier["y"] as Double, 1e-6)
            assertEquals(avant, declencheurs(db))
        }
    }

    @Test
    fun modifierArbre() {
        ouvrir().use { db ->
            GeoPackage.modifierArbre(db, arbre21, mapOf("essence_arbre" to "hetre", "qualite" to "B", "statut" to "mort", "notes" to "creux"))
            val a = db.requete("SELECT essence_arbre, qualite, statut, notes FROM arbres WHERE uuid = ?", listOf(arbre21)).single()
            assertEquals(listOf<Any?>("hetre", "B", "mort", "creux"), listOf(a["essence_arbre"], a["qualite"], a["statut"], a["notes"]))
            try {
                GeoPackage.modifierArbre(db, arbre21, mapOf("qualite" to "Z"))
                fail("qualité invalide acceptée")
            } catch (e: IllegalArgumentException) {
                // refused, nothing written
            }
            assertEquals("B", db.requete("SELECT qualite FROM arbres WHERE uuid = ?", listOf(arbre21)).single()["qualite"])
        }
    }

    @Test
    fun ajouterPuisSupprimerUnArbre() {
        ouvrir().use { db ->
            val avant = nombre(db, "SELECT count(*) FROM arbres")
            // P001: centre (925000, 6700000), 90 deg magnetic, 10 m -> about (925009.9999, 6699999.9587).
            val uuid = GeoPackage.ajouterArbre(db, "P001", 90.0, 10.0, 31.4, essence = "hetre")
            assertEquals(avant + 1, nombre(db, "SELECT count(*) FROM arbres"))
            val a = db.requete("SELECT * FROM arbres WHERE uuid = ?", listOf(uuid)).single()
            assertEquals("terrain", a["methode"])
            assertEquals("mesure", a["dbh_source"])
            assertEquals("A1", a["tree_id"])
            val (x, y) = GeoPackage.decoderPoint(a["geom"] as ByteArray)
            assertEquals(925009.9999149, x, 0.01)
            assertEquals(6699999.9587365, y, 0.01)
            // A tree of the video can never be deleted.
            try {
                GeoPackage.supprimerArbreAjoute(db, arbre14)
                fail("arbre de la vidéo supprimé")
            } catch (e: IllegalArgumentException) {
                // refused
            }
            GeoPackage.supprimerArbreAjoute(db, uuid, dossier)
            assertEquals(avant, nombre(db, "SELECT count(*) FROM arbres"))
        }
    }

    @Test
    fun migrationDUnProjetAncien_sansDbhSource() {
        // projet_ancien.gpkg: the example as of before schema 1.3.0 (like the phone's projects of October 2026):
        // GDAL's R*Tree on centres_mesures, cibles and houppiers, no dbh_source. ADD COLUMN must work on Android
        // with the R*Tree of houppiers still in the schema.
        ouvrir(ancien()).use { db ->
            fun detections() = declencheurs(db).filter { it["name"].toString().startsWith("trg_detections_") }
            assertTrue("dbh_source" !in db.requete("PRAGMA table_info(arbres)").map { it["name"] })
            GeoPackage.modifierArbre(db, arbre14, mapOf("dbh_cm" to "42,5", "essence_arbre" to "hetre"))
            assertTrue("dbh_source" in db.requete("PRAGMA table_info(arbres)").map { it["name"] })
            assertEquals(2, detections().size)
            assertEquals("mesure", db.requete("SELECT dbh_source FROM arbres WHERE uuid = ?", listOf(arbre14)).single()["dbh_source"])
            assertEquals(18, nombre(db, "SELECT count(*) FROM arbres"))
            assertEquals("ok", db.requete("PRAGMA integrity_check").single().values.single())
        }
    }

    @Test
    fun projetAncienAvecIndex_centreCibleEtEnvoi() {
        // The real case of the phone (ADR 0001): an old project with GDAL's R*Tree, which Android cannot load.
        val f = ancien()
        ouvrir(f).use { db ->
            GeoPackage.ajouterCentreMesure(db, "P002", 925200.0, 6700200.0, "telephone", 3.5, 1.5, "2026-11-12T08:00:00Z")
            GeoPackage.ecrireCible(db, "P002", CiblePlacee("C3", 10.0, 9.0, 30.0, SourceAzimut.BOUSSOLE_VISEE), 925010.0, 6700020.0, 10.0)
            assertEquals(3, nombre(db, "SELECT count(*) FROM centres_mesures"))
            assertEquals(1, GeoPackage.lirePoints(db, "cibles", "P002").count { it["id_cible"] == "C3" })
            assertEquals(18, nombre(db, "SELECT count(*) FROM arbres"))
            assertEquals(0, nombre(db, "SELECT count(*) FROM sqlite_master WHERE name LIKE 'rtree_centres_mesures_%' OR name LIKE 'rtree_cibles_%'"))
            assertEquals("ok", db.requete("PRAGMA integrity_check").single().values.single())
        }
        val sortie = File(dossier, "terrain-ancien-P001.zip")
        val resultat = ExportTerrain { ouvrir(it) }.archiver(f, dossier, "P001", sortie)
        assertTrue(resultat.empreinte.isNotEmpty())
        ouvrir(f, ecriture = false).use { db -> assertEquals(3, nombre(db, "SELECT count(*) FROM centres_mesures")) }
    }

    @Test
    fun releverC0_ciblesSuivent() {
        // Mission 031 on the old project (R*Tree of GDAL on cibles): UPDATE of the target points on Android's SQLite.
        // By hand: C1 at 90° / 10 m and C2 at 0° / 5 m from the new C0 (925003, 6700004), deg, no declination.
        ouvrir(ancien()).use { db ->
            GeoPackage.retirerIndexSpatial(db) // as Groma does before any write (ADR 0001)
            db.executer("DELETE FROM cibles WHERE plot_id = 'P002'")
            GeoPackage.ecrireCible(db, "P002", CiblePlacee("C1", 90.0, 10.0, 41.0, SourceAzimut.BOUSSOLE_VISEE), 925010.0, 6700000.0, 90.0)
            GeoPackage.ecrireCible(db, "P002", CiblePlacee("C2", 0.0, 5.0, 33.0, SourceAzimut.BOUSSOLE_VISEE), 925000.0, 6700005.0, 0.0)
            val r = GeoPackage.releverC0(db, "P002", 925003.0, 6700004.0, "rtk_fix", 0.02, 1.5, OrientationPlacette("deg", 0.0, 0.0), "2026-11-13T08:00:00Z")
            assertEquals(listOf("C1", "C2"), r.ciblesRecalees)
            val c = GeoPackage.lirePoints(db, "cibles", "P002").associateBy { it["id_cible"] }
            assertEquals(925013.0, c["C1"]!!["x"] as Double, 1e-6)
            assertEquals(6700004.0, c["C1"]!!["y"] as Double, 1e-6)
            assertEquals(925003.0, c["C2"]!!["x"] as Double, 1e-6)
            assertEquals(6700009.0, c["C2"]!!["y"] as Double, 1e-6)
            assertEquals(3, nombre(db, "SELECT count(*) FROM centres_mesures"))
            assertEquals("ok", db.requete("PRAGMA integrity_check").single().values.single())
        }
    }

    @Test
    fun exportTerrain_instantaneEtArchiveRelisible() {
        ouvrir().use { db ->
            GeoPackage.modifierArbre(db, arbre14, mapOf("dbh_cm" to "42,5", "essence_arbre" to "hetre", "notes" to "à revoir"))
        }
        val sortie = File(dossier, "terrain-P001.zip")
        val resultat = ExportTerrain { ouvrir(it) }.archiver(gpkg, dossier, "P001", sortie)
        assertTrue(resultat.empreinte.isNotEmpty())
        assertTrue(resultat.photosManquantes.isEmpty())

        val extrait = File(dossier, "extrait.gpkg")
        ZipFile(sortie).use { z ->
            val e = z.getEntry(ExportTerrain.GPKG)
            assertNotNull("terrain.gpkg absent de l'archive", e)
            z.getInputStream(e).use { i -> extrait.outputStream().use { i.copyTo(it) } }
        }
        ouvrir(extrait, ecriture = false).use { db ->
            assertEquals(listOf<Any?>("P001"), GeoPackage.lirePoints(db, "placettes").map { it["plot_id"] })
            assertEquals(18, nombre(db, "SELECT count(*) FROM arbres"))
            assertEquals(0, nombre(db, "SELECT count(*) FROM arbres WHERE plot_id <> 'P001'"))
            assertEquals("hetre", db.requete("SELECT essence_arbre FROM arbres WHERE uuid = ?", listOf(arbre14)).single()["essence_arbre"])
            assertEquals("ok", db.requete("PRAGMA integrity_check").single().values.single())
        }
        // The source copy is untouched by the extraction: still readable, same trees.
        ouvrir(ecriture = false).use { assertEquals(18, nombre(it, "SELECT count(*) FROM arbres")) }
    }

    @Test
    fun relectureFinaleApresFermeture() {
        ouvrir().use { db ->
            GeoPackage.ajouterCentreMesure(db, "P001", 925001.0, 6700001.0, "rtk_fix", 0.02, 1.4)
            GeoPackage.ecrireCible(db, "P001", CiblePlacee("C1", 45.0, 8.0, 28.0, SourceAzimut.BOUSSOLE_TELEPHONE), 925005.0, 6700005.0, 45.0)
            GeoPackage.modifierArbre(db, arbre21, mapOf("essence_arbre" to "chene_sessile"))
        }
        // New connection, as after a restart of the app: everything written is still there.
        ouvrir(ecriture = false).use { db ->
            assertEquals(3, nombre(db, "SELECT count(*) FROM centres_mesures"))
            assertTrue(GeoPackage.lirePoints(db, "cibles", "P001").any { it["id_cible"] == "C1" })
            assertEquals("chene_sessile", db.requete("SELECT essence_arbre FROM arbres WHERE uuid = ?", listOf(arbre21)).single()["essence_arbre"])
            assertEquals("ok", db.requete("PRAGMA integrity_check").single().values.single())
        }
    }
}
