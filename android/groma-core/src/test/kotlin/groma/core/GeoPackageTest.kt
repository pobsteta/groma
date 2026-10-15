package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** JDBC adapter standing in for android.database.sqlite in the tests. */
class SqliteJdbc(private val cx: Connection) : BaseSqlite {
    override fun executer(sql: String, args: List<Any?>) {
        cx.prepareStatement(sql).use { st -> args.forEachIndexed { i, v -> st.setObject(i + 1, v) }; st.executeUpdate() }
    }

    override fun requete(sql: String, args: List<Any?>): List<Map<String, Any?>> =
        cx.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, v -> st.setObject(i + 1, v) }
            st.executeQuery().use { rs ->
                val md = rs.metaData
                buildList { while (rs.next()) add((1..md.columnCount).associate { md.getColumnName(it) to rs.getObject(it) }) }
            }
        }

    override fun <T> transactionImmediate(bloc: () -> T): T {
        cx.createStatement().use { it.execute("BEGIN IMMEDIATE") }
        try {
            val r = bloc()
            cx.createStatement().use { it.execute("COMMIT") }
            return r
        } catch (e: Exception) {
            cx.createStatement().use { it.execute("ROLLBACK") }
            throw e
        }
    }
}

class GeoPackageTest {
    private fun copieExemple(): Connection {
        val src = Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath()
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING)
        val cx = DriverManager.getConnection("jdbc:sqlite:$tmp")
        cx.autoCommit = true
        cx.createStatement().use { it.execute("PRAGMA busy_timeout = 5000") }
        return cx
    }

    @Test
    fun pointAllerRetour() {
        assertEquals(925001.25 to 6700002.5, GeoPackage.decoderPoint(GeoPackage.encoderPoint(925001.25, 6700002.5)))
    }

    @Test
    fun houppiersDeLExemple() {
        copieExemple().use { cx ->
            val db = SqliteJdbc(cx)
            val lignes = db.requete("SELECT houppier_id, h_max, geom FROM houppiers WHERE plot_id = 'P001'")
            val hs = lignes.map { Houppier((it["houppier_id"] as Number).toInt(), GeoPackage.decoderAnneau(it["geom"] as ByteArray), (it["h_max"] as Number).toDouble()) }
            assertTrue(hs.size > 10)
            val (cx0, cy0) = hs[0].anneau.let { a -> Pair(a.sumOf { it.first } / a.size, a.sumOf { it.second } / a.size) }
            assertTrue(hs[0].couvre(cx0, cy0))
        }
    }

    @Test
    fun protocoleSurLeGeoPackageDeGdal() {
        copieExemple().use { cx ->
            val db = SqliteJdbc(cx)
            val placette = GeoPackage.lirePoints(db, "placettes", "P002").single()
            val p = ParametresPlacette("P002", placette["x"] as Double, placette["y"] as Double,
                convergenceDeg = (placette["convergence_deg"] as Number).toDouble())
            val troncs = listOf(
                TroncGeoreference(p.centreX + 3.0, p.centreY + 4.0, 31.0),
                TroncGeoreference(p.centreX - 6.0, p.centreY + 1.0, 24.0),
                TroncGeoreference(p.centreX + 1.0, p.centreY - 9.0, 6.0), // under the threshold: not numbered
            )
            val det = Placette.construireDetections(troncs, p)
            assertEquals(listOf("1", "2"), det.map { it.numProvisoire })
            assertEquals(2 to 0, GeoPackage.ecrireDetections(db, det))
            GeoPackage.mettreAJourEtat(db, "P002", "provisoire")

            // The trigger created the arbres rows; QField then types a species.
            val arbres = GeoPackage.lirePoints(db, "arbres", "P002")
            assertEquals(2, arbres.size)
            assertTrue(arbres.all { it["statut"] == "vivant" && it["etat_detection"] == "provisoire" })
            db.executer("UPDATE arbres SET essence_arbre = 'hetre' WHERE uuid = ?", listOf(det[0].uuid))

            // Re-run: uuids are kept by matching, the measures change, the species stays.
            val nouveaux = Placette.reutiliserUuid(det, troncs.map { it.copy(x = it.x + 0.1, dbhCm = it.dbhCm + 1) })
            val det2 = Placette.construireDetections(nouveaux, p)
            assertEquals(0 to 2, GeoPackage.ecrireDetections(db, det2))
            val apres = GeoPackage.lirePoints(db, "arbres", "P002").associateBy { it["uuid"] }
            assertEquals("hetre", apres.getValue(det[0].uuid)["essence_arbre"])
            assertEquals(32.0, (apres.getValue(det[0].uuid)["dbh_cm"] as Number).toDouble(), 1e-9)
            assertEquals("provisoire", GeoPackage.lirePoints(db, "placettes", "P002").single()["etat_traitement"])
        }
    }

    @Test
    fun projetDeLExemple() {
        copieExemple().use { cx ->
            val db = SqliteJdbc(cx)
            val p = GeoPackage.lireProjet(db)!!
            assertEquals("exemple_synthetique", p.projetId)
            assertEquals("Forêt d'exemple (synthétique)", p.projetNom)
            assertEquals(12.5, p.surfaceZoneHa!!, 1e-9)
            assertEquals(2 to 0, p.nbPlacettesBase to p.nbPlacettesRempl)
            assertEquals(null, p.proprietaire)
            assertEquals(null, p.surfaceCadastreHa)
            assertTrue(p.emprise!!.startsWith("POLYGON (("))
        }
    }

    @Test
    fun parcellesDeLExemple() {
        // Written by GDAL (envelope in the header): two parts, the first with a hole.
        copieExemple().use { cx ->
            val db = SqliteJdbc(cx)
            val p = GeoPackage.lireParcelles(db).single()
            assertEquals("000000000A0001", p.idu)
            assertEquals(listOf("00000", "A", "1"), listOf(p.commune, p.section, p.numero))
            assertEquals(125000.0, p.contenanceM2!!, 1e-9)
            assertEquals(listOf(2, 1), p.polygones.map { it.size })
            assertEquals(924900.0 to 6699900.0, p.polygones[0][0][0])
            assertEquals(5, p.polygones[0][1].size) // closed ring
            assertTrue((925300.0 to 6700200.0) in p.polygones[1][0])
            // Older GeoPackage: no layer, no parcel.
            db.executer("DROP TABLE parcelles")
            assertEquals(emptyList(), GeoPackage.lireParcelles(db))
        }
    }

    @Test
    fun ugfDeLExemple() {
        copieExemple().use { cx ->
            val db = SqliteJdbc(cx)
            val u = GeoPackage.lireUgf(db)
            assertEquals(listOf("ug_001", "ug_002"), u.map { it.ugId })
            val (a, b) = u
            assertEquals(listOf("Forêt d'exemple — parcelle 1", "Forêt d'exemple", "1"), listOf(a.libelle, a.foret, a.parcelleOnf))
            assertEquals(60000.0, a.surfaceM2!!, 1e-9)
            assertEquals(listOf(1), b.polygones.map { it.size })
            assertTrue((925300.0 to 6700200.0) in b.polygones[0][0])
            db.executer("DROP TABLE ugf")
            assertEquals(emptyList(), GeoPackage.lireUgf(db))
        }
    }

    @Test
    fun polygoneSimpleEtCoordonneesZ() {
        // POLYGON Z, big endian, without envelope: Z skipped.
        val b = java.nio.ByteBuffer.allocate(8 + 9 + 4 + 4 * 24)
        b.put('G'.code.toByte()).put('P'.code.toByte()).put(0).put(0)
        b.putInt(2154)
        b.put(0).putInt(1003).putInt(1).putInt(4)
        for ((x, y) in listOf(0.0 to 0.0, 10.0 to 0.0, 0.0 to 10.0, 0.0 to 0.0)) b.putDouble(x).putDouble(y).putDouble(250.0)
        assertEquals(listOf(listOf(listOf(0.0 to 0.0, 10.0 to 0.0, 0.0 to 10.0, 0.0 to 0.0))), GeoPackage.decoderPolygones(b.array()))
    }

    @Test
    fun projetAbsentOuVide() {
        // GeoPackage prepared before schema 1.1.0, then an empty table: no project info, no crash.
        copieExemple().use { cx ->
            val db = SqliteJdbc(cx)
            db.executer("DELETE FROM projet")
            assertEquals(null, GeoPackage.lireProjet(db))
            db.executer("DROP TABLE projet")
            assertEquals(null, GeoPackage.lireProjet(db))
        }
    }
}
