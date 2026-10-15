package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CarteTest {
    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    @Test
    fun donneesDeLExemple() = exemple { db ->
        val d = DonneesCarte.lire(db)
        assertEquals(listOf("P001", "P002"), d.placettes.map { it.plotId })
        val p1 = d.placettes.first()
        assertEquals("provisoire", p1.etat)
        assertEquals(20.0, p1.rayonM)
        assertEquals("deg", p1.uniteAzimut)
        assertEquals(2.4, p1.declinaisonDeg)
        val (lon, lat) = Projections.versWgs84(p1.x, p1.y)
        assertEquals(lon to lat, p1.lon to p1.lat)
        assertEquals(listOf("P001", "P002"), d.parcours.map { it.plotId })
        // Centres measured on the planned points.
        assertEquals(listOf("P001", "P002"), d.centres.map { it.plotId })
        assertEquals(0.0, d.centres.first().ecartM!!, 1e-6)
        // One parcel in two parts, label from section and number.
        val parcelle = d.parcelles.single()
        assertEquals("A 1", parcelle.etiquette)
        assertEquals(2, parcelle.polygones.size)
        // Two management units, labelled with their ONF number.
        assertEquals(listOf("1", "2"), d.ugf.map { it.etiquette })
        assertEquals("ug_001", d.ugf.first().ugId)
        val e = assertNotNull(d.emprise)
        assertTrue(e.ouest < p1.lon && p1.lon < e.est && e.sud < p1.lat && p1.lat < e.nord)
    }

    @Test
    fun ancienGeoPackage() = exemple { db ->
        // Prepared before schema 1.2.0, no measured centre table: plots only, no error.
        db.executer("DROP TABLE parcelles")
        db.executer("DROP TABLE ugf")
        db.executer("DROP TABLE centres_mesures")
        val d = DonneesCarte.lire(db)
        assertEquals(2, d.placettes.size)
        assertEquals(emptyList(), d.parcelles)
        assertEquals(emptyList(), d.ugf)
        assertEquals(emptyList(), d.centres)
    }

    @Test
    fun parcoursSansRemplacement() {
        fun p(id: String, type: String?, ordre: Int?) = PlacetteCarte(id, type, "a_faire", 5.0, 47.0, 0.0, 0.0, 15.0, ordre)
        val d = DonneesCarte(
            listOf(p("P3", "Base", 3), p("P1", "Base", 1), p("O1", "Over", 4), p("P2", "Base", 2), p("X", "Base", null)),
            emptyList(), emptyList(),
        )
        assertEquals(listOf("P1", "P2", "P3"), d.parcours.map { it.plotId })
        assertEquals(null, DonneesCarte(emptyList(), emptyList(), emptyList()).emprise)
    }

    @Test
    fun etiquetteEtCentreDeParcelle() {
        val grand = listOf(listOf(0.0 to 0.0, 4.0 to 0.0, 4.0 to 2.0, 0.0 to 2.0, 0.0 to 0.0))
        val petit = listOf(listOf(10.0 to 10.0, 11.0 to 10.0, 11.0 to 11.0, 10.0 to 10.0))
        val p = ParcelleCarte("212000000A0036", null, null, listOf(petit, grand))
        assertEquals("212000000A0036", p.etiquette)
        assertEquals(2.0 to 1.0, p.centreEtiquette)
    }

    @Test
    fun etiquetteDUgf() {
        val carre = listOf(listOf(0.0 to 0.0, 2.0 to 0.0, 2.0 to 2.0, 0.0 to 2.0, 0.0 to 0.0))
        assertEquals("10", UgfCarte("ug_1", "Forêt — parcelle 10", "10", listOf(carre)).etiquette)
        assertEquals("Forêt — parcelle 10", UgfCarte("ug_1", "Forêt — parcelle 10", " ", listOf(carre)).etiquette)
        assertEquals("ug_1", UgfCarte("ug_1", null, null, listOf(carre)).etiquette)
        // nemetonshiny names a unit outside ONF forests after its parcel.
        assertEquals("A 577", UgfCarte("ug_1", "451880000A0577", null, listOf(carre)).etiquette)
        assertEquals("B 12", UgfCarte("ug_1", "2A0040000B0012", null, listOf(carre)).etiquette)
        assertEquals(1.0 to 1.0, UgfCarte("ug_1", null, null, listOf(carre)).centreEtiquette)
    }
}
