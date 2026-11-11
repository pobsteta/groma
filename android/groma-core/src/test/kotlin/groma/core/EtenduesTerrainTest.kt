package groma.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mission 021 (reprise 1/3): extents of `gpkg_contents` recomputed from the rows kept in the archive. */
class EtenduesTerrainTest {
    private val export = ExportTerrain { SqliteJdbc(DriverManager.getConnection("jdbc:sqlite:$it")) }

    private fun extraite(plot: String): List<Map<String, Any?>> {
        val src = Files.createTempFile("copie", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), src, StandardCopyOption.REPLACE_EXISTING)
        val sortie = File.createTempFile("terrain", ".gpkg")
        export.extraire(src.toFile(), plot, sortie)
        return DriverManager.getConnection("jdbc:sqlite:$sortie").use {
            SqliteJdbc(it).requete("SELECT table_name, min_x, min_y, max_x, max_y FROM gpkg_contents")
        }
    }

    @Test
    fun etenduesLimiteesALaPlacetteGardee() {
        val c = extraite("P001").associateBy { it["table_name"] }
        val centres = c.getValue("centres_mesures")
        assertEquals(925000.0, (centres["min_x"] as Number).toDouble(), 1e-6)
        assertEquals(6700000.0, (centres["max_y"] as Number).toDouble(), 1e-6)
        // The source extent of `placettes` covers two plots; here only P001's point remains.
        val placettes = c.getValue("placettes")
        assertEquals(placettes["min_x"], placettes["max_x"])
        for (t in listOf("houppiers", "parcelles", "ugf")) assertNull(c.getValue(t)["min_x"], t)
    }

    @Test
    fun placetteSansArbreNAPasDEtendueDArbres() {
        val c = extraite("P002").associateBy { it["table_name"] }
        assertNull(c.getValue("arbres")["min_x"])
        assertTrue(c.getValue("cibles")["min_x"] != null)
    }
}
