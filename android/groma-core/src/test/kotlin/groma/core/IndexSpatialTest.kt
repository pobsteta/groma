package groma.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mission 028: a project prepared with R*Tree indexes on `centres_mesures` / `cibles` keeps working
 * on a phone whose SQLite has no `rtree` module.
 *
 * The JVM SQLite HAS the module, so the phone is simulated: after building a "legacy" project (real
 * indexes and GDAL-like triggers, calling ST_IsEmpty), the module name in `sqlite_master` is rewritten
 * to `rtree_absent`. Any statement that touches such a virtual table, or fires a trigger that does,
 * then fails with "no such module", exactly as on Android. If the removal ever loaded the module it
 * would fail here too.
 */
class IndexSpatialTest {
    private fun url(f: File) = "jdbc:sqlite:$f"

    private fun <T> sur(f: File, bloc: (SqliteJdbc) -> T): T = DriverManager.getConnection(url(f)).use { bloc(SqliteJdbc(it)) }

    /** Fixture (no index since mission 028) + real R*Tree indexes on [tables] with GDAL-like triggers. */
    private fun projetAncien(tables: List<String> = listOf("centres_mesures", "cibles")): File {
        val f = Files.createTempFile("ancien", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), f, StandardCopyOption.REPLACE_EXISTING)
        sur(f.toFile()) { db ->
            for (t in tables) {
                val r = "rtree_${t}_geom"
                db.executer("CREATE VIRTUAL TABLE $r USING rtree(id, minx, maxx, miny, maxy)")
                for (l in db.requete("SELECT fid, geom FROM $t")) {
                    val (x, y) = GeoPackage.decoderPoint(l["geom"] as ByteArray)
                    db.executer("INSERT INTO $r VALUES (?, ?, ?, ?, ?)", listOf(l["fid"], x, x, y, y))
                }
                db.executer("INSERT INTO gpkg_extensions VALUES ('$t', 'geom', 'gpkg_rtree_index', 'http://www.geopackage.org/spec120/#extension_rtree', 'write-only')")
                db.executer(
                    "CREATE TRIGGER ${r}_insert AFTER INSERT ON $t WHEN (new.geom NOT NULL AND NOT ST_IsEmpty(NEW.geom)) BEGIN " +
                        "INSERT OR REPLACE INTO $r VALUES (NEW.fid, ST_MinX(NEW.geom), ST_MaxX(NEW.geom), ST_MinY(NEW.geom), ST_MaxY(NEW.geom)); END",
                )
                db.executer("CREATE TRIGGER ${r}_delete AFTER DELETE ON $t WHEN old.geom NOT NULL BEGIN DELETE FROM $r WHERE id = OLD.fid; END")
            }
        }
        return f.toFile()
    }

    /** The phone: the same file, but the `rtree` module does not exist. */
    private fun sansModuleRtree(f: File) {
        DriverManager.getConnection(url(f)).use { cx ->
            cx.createStatement().use { st ->
                val version = st.executeQuery("PRAGMA schema_version").use { it.next(); it.getLong(1) }
                st.execute("PRAGMA writable_schema = ON")
                st.execute("UPDATE sqlite_master SET sql = replace(sql, 'USING rtree(', 'USING rtree_absent(') WHERE sql LIKE 'CREATE VIRTUAL TABLE%USING rtree(%'")
                st.execute("PRAGMA schema_version = ${version + 1}")
                st.execute("PRAGMA writable_schema = OFF")
            }
        }
    }

    private fun noms(db: SqliteJdbc, motif: String) =
        db.requete("SELECT name FROM sqlite_master WHERE name LIKE '$motif' ESCAPE '\\' ORDER BY name").map { it["name"] as String }

    private fun lignes(db: SqliteJdbc, t: String) = db.requete("SELECT * FROM $t ORDER BY fid").map { l -> l.mapValues { (_, v) -> if (v is ByteArray) v.toList() else v } }

    @Test
    fun la_simulation_reproduit_android() {
        val f = projetAncien()
        sansModuleRtree(f)
        sur(f) { db ->
            val e = assertFailsWith<Exception> { db.executer("DELETE FROM cibles WHERE plot_id IS NOT 'P001'") }
            assertTrue("no such module" in e.message.orEmpty(), e.message)
            assertFailsWith<Exception> { db.executer("INSERT INTO centres_mesures (geom, plot_id) VALUES (?, 'P002')", listOf(GeoPackage.encoderPoint(1.0, 2.0))) }
        }
    }

    @Test
    fun retrait_sans_module_et_sans_perte() {
        val f = projetAncien()
        sansModuleRtree(f)
        sur(f) { db ->
            val centres = lignes(db, "centres_mesures")
            val cibles = lignes(db, "cibles")
            val autres = noms(db, "rtree\\_houppiers\\_%")
            assertTrue(GeoPackage.retirerIndexSpatial(db))
            assertEquals(emptyList(), noms(db, "rtree\\_centres\\_mesures\\_%"))
            assertEquals(emptyList(), noms(db, "rtree\\_cibles\\_%"))
            assertEquals(0, db.requete("SELECT 1 AS u FROM gpkg_extensions WHERE extension_name = 'gpkg_rtree_index' AND table_name IN ('centres_mesures', 'cibles')").size)
            assertEquals("ok", db.requete("PRAGMA integrity_check").single().values.first())
            assertEquals(centres, lignes(db, "centres_mesures"))
            assertEquals(cibles, lignes(db, "cibles"))
            assertEquals(autres, noms(db, "rtree\\_houppiers\\_%"), "index d'une autre couche non touché")
            assertFalse(GeoPackage.retirerIndexSpatial(db), "idempotent")
            // Writes and DELETE now go through, without the module.
            db.executer("DELETE FROM cibles WHERE plot_id IS NOT 'P001'")
        }
    }

    @Test
    fun ecritures_sur_projet_ancien_sans_module() {
        val f = projetAncien()
        sansModuleRtree(f)
        sur(f) { db ->
            val centres0 = GeoPackage.lirePoints(db, "centres_mesures", "P001")
            val cibles0 = GeoPackage.lirePoints(db, "cibles", "P001")
            val fid = GeoPackage.ajouterCentreMesure(db, "P002", 925003.0, 6700004.0, "fix", 0.02, null, "2026-11-12T10:00:00Z")
            assertEquals(fid, (GeoPackage.dernierCentreParPlacette(db, "P002").single()["fid"] as Number).toLong())
            GeoPackage.ecrireCible(db, "P002", CiblePlacee("C2", 130.0, 11.0, 41.0, SourceAzimut.BOUSSOLE_TELEPHONE), 925010.0, 6700020.0, 144.4)
            GeoPackage.ecrireCible(db, "P002", CiblePlacee("C2", 12.0, 11.0, 41.0, SourceAzimut.BOUSSOLE_VISEE), 925011.0, 6700021.0, 144.0)
            assertEquals(1, GeoPackage.lirePoints(db, "cibles", "P002").count { it["id_cible"] == "C2" })
            assertEquals(centres0, GeoPackage.lirePoints(db, "centres_mesures", "P001"), "autre placette intacte")
            assertEquals(cibles0, GeoPackage.lirePoints(db, "cibles", "P001"))
            assertEquals(emptyList(), noms(db, "rtree\\_cibles\\_%") + noms(db, "rtree\\_centres\\_mesures\\_%"))
            assertEquals("ok", db.requete("PRAGMA integrity_check").single().values.first())
        }
    }

    @Test
    fun envoi_de_terrain_sur_projet_ancien_sans_module() {
        val f = projetAncien()
        sansModuleRtree(f)
        val sortie = Files.createTempFile("terrain", ".gpkg").toFile()
        val source = sur(f) { lignes(it, "cibles").filter { l -> l["plot_id"] == "P001" } }
        val avant = f.readBytes().toList()
        ExportTerrain { SqliteJdbc(DriverManager.getConnection(url(it))) }.extraire(f, "P001", sortie)
        assertEquals(avant, f.readBytes().toList(), "copie de Groma intacte")
        sur(sortie) { db ->
            assertEquals(emptyList(), noms(db, "rtree\\_%"))
            assertEquals("ok", db.requete("PRAGMA integrity_check").single().values.first())
            assertEquals(source.map { it["id_cible"] }, lignes(db, "cibles").map { it["id_cible"] })
            assertEquals(18, db.requete("SELECT 1 AS u FROM arbres").size)
        }
    }

    @Test
    fun avec_le_module_present_aussi() {
        val f = projetAncien()
        sur(f) { db ->
            assertTrue(GeoPackage.retirerIndexSpatial(db))
            assertEquals(emptyList(), noms(db, "rtree\\_cibles\\_%"))
            assertEquals("ok", db.requete("PRAGMA integrity_check").single().values.first())
        }
    }

    @Test
    fun projet_neuf_rien_a_retirer() {
        val f = Files.createTempFile("neuf", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), f, StandardCopyOption.REPLACE_EXISTING)
        sur(f.toFile()) { db -> assertFalse(GeoPackage.retirerIndexSpatial(db)) }
    }
}
