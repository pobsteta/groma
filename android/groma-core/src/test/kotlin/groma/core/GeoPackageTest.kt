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
}
