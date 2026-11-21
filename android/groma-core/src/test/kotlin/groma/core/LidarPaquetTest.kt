package groma.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Acceptance tests of mission 039 (written by the tester, 039a, without reading the 039 code).
 * Expected values come from: the 039 sheet ("Plan validé"), the 039a sheet (API), schema 1.7.0
 * (rasters.format, rasters.cvat), and the example `exemples/inventaire_video`:
 *
 * The example DTM is a plane: z = 420 + 0.12 (x - 925000) - 0.03 (y - 6700000), valid on
 * 924850-925350 (east) x 6699850-6700250 (north), nodata elsewhere; four 1 km tiles meet at
 * (925000, 6700000). Plots: P001 centre (925000, 6700000), P002 centre (925180, 6700090).
 * `fixtures/lidar_039a/` holds the CVAT reference values (made with rasterio).
 */
class LidarPaquetTest {
    private val exemple = Fixtures.racine.resolve("exemples/inventaire_video")
    private val gpkg170 = "exemples/inventaire_video/inventaire_video.gpkg"
    private val gpkg160 = "fixtures/schema_1_6_0/inventaire_video.gpkg"
    private val sortieKotlin = File(Fixtures.racine, "android/groma-core/build/lidar-039a")
    private val tuiles = listOf("mnt_0924_6699.tif", "mnt_0924_6700.tif", "mnt_0925_6699.tif", "mnt_0925_6700.tif")

    private fun plan(x: Double, y: Double) = 420.0 + 0.12 * (x - 925000.0) - 0.03 * (y - 6700000.0)

    // ---- helpers -------------------------------------------------------------------------------

    private fun ouvrir(chemin: String): Connection {
        val tmp = Files.createTempFile("lidar039a", ".gpkg")
        Files.copy(Fixtures.racine.resolve(chemin).toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        return DriverManager.getConnection("jdbc:sqlite:$tmp")
    }

    /** Project folder in a temp dir whose `rasters/` holds copies of the named files of the example. */
    private fun projet(vararg rasters: String): File {
        val d = Files.createTempDirectory("projet039a").toFile()
        File(d, "rasters").mkdirs()
        for (r in rasters) Files.copy(File(exemple, "rasters/$r").toPath(), File(d, "rasters/$r").toPath())
        return d
    }

    private fun decouper(
        dossier: File, x: Double, y: Double, plot: String = "P001", rayon: Double = 15.0, gpkg: String = gpkg170,
    ): ResultatLidar = ouvrir(gpkg).use { LidarPaquet.decouper(dossier, SqliteJdbc(it), plot, x, y, rayon) }

    private fun grille(r: ResultatLidar, chemin: String = "lidar/mnt.tif"): Grille =
        GeoTiff.lire(assertNotNull(r.fichiers[chemin], "$chemin absent du résultat (remarque : ${r.manifeste["remarque"]})"))

    private fun remarque(r: ResultatLidar): String = (r.manifeste["remarque"] as? String).orEmpty()

    private fun json(octets: ByteArray) = Json.parseToJsonElement(String(octets, Charsets.UTF_8)).jsonObject

    /** Minimal TIFF directory reader: tag -> values (SHORT/LONG as Long, DOUBLE as Double, ASCII as one String). */
    private fun ifd(o: ByteArray): Pair<ByteOrder, Map<Int, List<Any>>> {
        val ordre = if (o[0] == 'I'.code.toByte()) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val b = ByteBuffer.wrap(o).order(ordre)
        assertEquals(42, b.getShort(2).toInt())
        val debut = b.getInt(4)
        val n = b.getShort(debut).toInt() and 0xFFFF
        val tags = HashMap<Int, List<Any>>()
        for (k in 0 until n) {
            val e = debut + 2 + 12 * k
            val tag = b.getShort(e).toInt() and 0xFFFF
            val type = b.getShort(e + 2).toInt()
            val count = b.getInt(e + 4)
            val taille = when (type) { 1, 2 -> 1; 3 -> 2; 4 -> 4; 12 -> 8; else -> 4 }
            val off = if (taille * count <= 4) e + 8 else b.getInt(e + 8)
            tags[tag] = when (type) {
                3 -> List(count) { (b.getShort(off + 2 * it).toInt() and 0xFFFF).toLong() }
                4 -> List(count) { b.getInt(off + 4 * it).toLong() and 0xFFFFFFFFL }
                12 -> List(count) { b.getDouble(off + 8 * it) }
                2 -> listOf(String(o, off, count, Charsets.US_ASCII).trim('\u0000', ' '))
                else -> listOf()
            }
        }
        return ordre to tags
    }

    /** 7 columns x 5 rows, value = 400 + 0.25 col + 0.5 row, NaN at (row 1, col 2) and (row 4, col 6). */
    private fun grilleDeReference(): Grille {
        val l = 5; val c = 7
        val v = FloatArray(l * c) { i -> 400f + 0.25f * (i % c) + 0.5f * (i / c) }
        v[1 * c + 2] = Float.NaN; v[4 * c + 6] = Float.NaN
        return Grille(c, l, 925000.5, 6700010.0, 0.5, v)
    }

    private fun versJson(o: Any?): String = when (o) {
        null -> "null"
        is Map<*, *> -> o.entries.joinToString(",", "{", "}") { "\"${it.key}\":${versJson(it.value)}" }
        is Number, is Boolean -> o.toString()
        else -> "\"" + o.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    private fun alignee(v: Double, origine: Double, pas: Double) = ((v - origine) / pas).let { it - Math.rint(it) }

    // ---- 1. GeoTiff.ecrire / lire ----------------------------------------------------------------

    @Test
    fun ecrireLire_valeursEtGeoreferencement() {
        val g = grilleDeReference()
        val r = GeoTiff.lire(GeoTiff.ecrire(g))
        assertEquals(g.largeur, r.largeur); assertEquals(g.hauteur, r.hauteur)
        assertEquals(g.x0, r.x0, 1e-9); assertEquals(g.y0, r.y0, 1e-9); assertEquals(g.res, r.res, 1e-12)
        for (i in g.valeurs.indices) {
            if (g.valeurs[i].isNaN()) assertTrue(r.valeurs[i].isNaN(), "NaN attendu à l'indice $i")
            else assertEquals(g.valeurs[i], r.valeurs[i], "valeur à l'indice $i")
        }
    }

    @Test
    fun ecrire_balisesDuFormatRastersFormat() {
        val o = GeoTiff.ecrire(grilleDeReference())
        val (ordre, t) = ifd(o)
        assertEquals(ByteOrder.LITTLE_ENDIAN, ordre, "petit-boutiste (II)")
        assertEquals(listOf<Any>(7L), t[256]); assertEquals(listOf<Any>(5L), t[257])
        assertEquals(listOf<Any>(32L), t[258], "Float32 : 32 bits")
        assertEquals(listOf<Any>(3L), t[339], "SampleFormat 3 = réel")
        assertTrue(t[277] == null || t[277] == listOf<Any>(1L), "une seule bande")
        assertTrue(t[259]?.get(0) == 8L || t[259]?.get(0) == 32946L, "DEFLATE (8 ou 32946), lu : ${t[259]}")
        assertTrue(t[317] == null || t[317] == listOf<Any>(1L), "pas de prédicteur")
        assertNull(t[322], "pas de tuiles : des bandes"); assertNotNull(t[273], "StripOffsets"); assertNotNull(t[279])
        val nodata = (t[42113]?.get(0) as? String)?.toDoubleOrNull()
        assertEquals(-9999.0, nodata, "balise GDAL_NODATA -9999")
        val echelle = t.getValue(33550).map { it as Double }
        assertEquals(0.5, echelle[0], 1e-12); assertEquals(0.5, echelle[1], 1e-12)
        val point = t.getValue(33922).map { it as Double }
        assertEquals(925000.5, point[3], 1e-9); assertEquals(6700010.0, point[4], 1e-9)
        // GeoKeyDirectory: key 3072 (ProjectedCSTypeGeoKey) = 2154.
        val cles = t.getValue(34735).map { (it as Long).toInt() }
        val v3072 = (0 until cles[3]).map { k -> cles.subList(4 + 4 * k, 8 + 4 * k) }.firstOrNull { it[0] == 3072 }?.get(3)
        assertEquals(2154, v3072, "GeoKey 3072 = EPSG:2154")
    }

    @Test
    fun ecrire_estCompresse_etLeNanSeRelit() {
        // 200 x 200 constant grid, raw size 160 kB: DEFLATE must bring it far below.
        val v = FloatArray(200 * 200) { 421.5f }
        for (i in 0 until 400) v[i] = Float.NaN
        val o = GeoTiff.ecrire(Grille(200, 200, 925100.0, 6700050.0, 0.5, v))
        assertTrue(o.size < 20_000, "taille ${o.size} : DEFLATE attendu")
        val r = GeoTiff.lire(o)
        assertEquals(400, r.valeurs.count { it.isNaN() })
        assertTrue(r.valeurs.take(400).all { it.isNaN() })
        assertEquals(421.5f, r.valeurs[400])
    }

    @Test
    fun ecrire_ecritUnFichierDeReferencePourLeServeur() {
        // Read back by rasterio in server/tests/test_paquet_lidar_039.py (build/lidar-039a/, not committed).
        sortieKotlin.mkdirs()
        File(sortieKotlin, "geotiff_ecrire.tif").writeBytes(GeoTiff.ecrire(grilleDeReference()))
    }

    // ---- 2. centre relevé à 150 m du centre prévu ----------------------------------------------

    @Test
    fun centreReleveA150m_mntDe200x200AutourDuCentreReleve() {
        val cx = 925150.0; val cy = 6700000.0 // P001 is planned at (925000, 6700000)
        val r = decouper(exemple, cx, cy)
        val g = grille(r)
        assertEquals(200, g.largeur); assertEquals(200, g.hauteur); assertEquals(0.5, g.res, 1e-12)
        assertEquals(cx, g.x0 + 100.0, 0.25 + 1e-9, "centré en x à un demi-pixel près")
        assertEquals(cy, g.y0 - 100.0, 0.25 + 1e-9, "centré en y à un demi-pixel près")
        assertTrue(925000.0 < g.x0, "le centre prévu (925000) est hors du carré")
        assertTrue(g.echantillonner(925000.0, 6700000.0).isNaN())
        assertEquals(plan(cx, cy), g.echantillonner(cx, cy), 0.02, "sol de l'exemple au centre relevé")
        assertTrue(g.valeurs.none { it.isNaN() }, "tout le carré est dans la zone valide de l'exemple")
        assertEquals(0.0, alignee(g.x0, 924000.0, 0.5), 1e-9, "alignée sur la grille des dalles")
        assertEquals(0.0, alignee(g.y0, 6700000.0, 0.5), 1e-9)
        val m = r.manifeste
        assertEquals("lidar/mnt.tif", m["mnt"])
        @Suppress("UNCHECKED_CAST") val d = m["decoupe"] as Map<String, Any?>
        assertEquals(cx, (d["x"] as Number).toDouble(), 0.5); assertEquals(cy, (d["y"] as Number).toDouble(), 0.5)
        assertEquals(50.0, (d["demi_cote_m"] as Number).toDouble(), 1e-12)
        assertEquals("dalles", d["source_mnt"])
        assertEquals("", remarque(r), "couverture totale : pas de remarque")
        assertNull(m["mnh"], "le raster P001_mnh ne couvre pas le centre relevé : pas de MNH")
        assertFalse(r.fichiers.containsKey("lidar/mnh.tif"))
    }

    @Test
    fun ecritUnPaquetDeReferencePourLeServeur() {
        // Centre (925120, 6700040): three crowns have their top in the square (checked with sqlite3 BETWEEN).
        val r = decouper(exemple, 925120.0, 6700040.0)
        sortieKotlin.mkdirs()
        File(sortieKotlin, "centre.txt").writeText("925120.0 6700040.0\n")
        for ((chemin, octets) in r.fichiers) File(sortieKotlin, chemin).also { it.parentFile.mkdirs() }.writeBytes(octets)
        File(sortieKotlin, "manifeste_lidar.json").writeText(versJson(r.manifeste))
        assertTrue(r.fichiers.containsKey("lidar/mnt.tif") && r.fichiers.containsKey("lidar/houppiers.geojson"), remarque(r))
    }

    @Test
    fun centreNonAligne_tailleEtAlignementTiennent() {
        val cx = 925150.3; val cy = 6700000.2
        val g = grille(decouper(exemple, cx, cy))
        assertEquals(200, g.largeur); assertEquals(200, g.hauteur)
        assertEquals(cx, g.x0 + 100.0, 0.5 + 1e-9); assertEquals(cy, g.y0 - 100.0, 0.5 + 1e-9)
        assertEquals(0.0, alignee(g.x0, 924000.0, 0.5), 1e-9, "alignée sur la grille des dalles")
        assertEquals(0.0, alignee(g.y0, 6700000.0, 0.5), 1e-9)
    }

    // ---- 3 et 4. coin des quatre dalles, couture ------------------------------------------------

    @Test
    fun coinDesQuatreDalles_chaquePixelEstCeluiDeSaDalle_sansCouture() {
        val g = grille(decouper(exemple, 925000.0, 6700000.0))
        assertEquals(200, g.largeur); assertEquals(200, g.hauteur)
        assertEquals(924950.0, g.x0, 0.5 + 1e-9); assertEquals(6700050.0, g.y0, 0.5 + 1e-9)
        val dalles = tuiles.associate { nom ->
            (nom.substring(4, 8).toInt() to nom.substring(9, 13).toInt()) to GeoTiff.lire(File(exemple, "rasters/$nom").readBytes())
        }
        var verifies = 0
        for (l in 0 until g.hauteur) for (c in 0 until g.largeur) {
            val x = g.x0 + (c + 0.5) * g.res; val y = g.y0 - (l + 0.5) * g.res
            val kx = floor(x / 1000.0).toInt(); val ky = floor(y / 1000.0).toInt()
            val attendu = dalles.getValue(kx to ky).valeur(floor(((ky + 1) * 1000.0 - y) / 0.5).toInt(), floor((x - kx * 1000.0) / 0.5).toInt())
            val lu = g.valeur(l, c)
            assertFalse(lu.isNaN(), "nodata à la couture ? ligne $l colonne $c (x=$x, y=$y)")
            assertEquals(attendu, lu, "ligne $l colonne $c (x=$x, y=$y)")
            verifies++
        }
        assertEquals(40000, verifies)
        // The four pixels around the corner (925000, 6700000) come from four different tiles and follow the plane.
        val cG = ((925000.0 - g.x0) / 0.5).toInt(); val lG = ((g.y0 - 6700000.0) / 0.5).toInt()
        for ((l, c) in listOf(lG - 1 to cG - 1, lG - 1 to cG, lG to cG - 1, lG to cG)) {
            val x = g.x0 + (c + 0.5) * 0.5; val y = g.y0 - (l + 0.5) * 0.5
            assertEquals(plan(x, y), g.valeur(l, c), 0.01, "pixel du coin ($l, $c)")
        }
    }

    @Test
    fun unPoint_estEnEstNord_etPrendSaValeurDeDalle() {
        val g = grille(decouper(exemple, 925000.0, 6700000.0))
        // One point per tile; the plane differs by far more than the tolerance if east and north are swapped.
        for ((x, y) in listOf(925030.3 to 6700012.8, 924970.2 to 6700041.1, 924981.7 to 6699962.4, 925044.6 to 6699955.9)) {
            val c = floor((x - g.x0) / g.res).toInt(); val l = floor((g.y0 - y) / g.res).toInt()
            assertEquals(plan(x, y), g.valeur(l, c), 0.05, "point ($x, $y)")
        }
    }

    // ---- 5. relief CVAT ------------------------------------------------------------------------

    @Test
    fun cvat_50x50_memesValeursQueLeRasterDeLaForet() {
        val r = decouper(exemple, 925000.0, 6700000.0)
        val octets = assertNotNull(r.fichiers["lidar/cvat.tif"], "lidar/cvat.tif absent")
        assertEquals("lidar/cvat.tif", r.manifeste["cvat"])
        val f = Files.createTempFile("cvat039a", ".tif").toFile().also { it.writeBytes(octets) }
        val g = assertNotNull(GeoTiffOctets.fenetre(f, Emprise(924900.0, 6699900.0, 925100.0, 6700100.0)))
        assertEquals(2.0, g.res, 1e-12); assertEquals(50, g.largeur); assertEquals(50, g.hauteur)
        assertEquals(924950.0, g.x0, 1e-9); assertEquals(6700050.0, g.y0, 1e-9)
        val attendu = File(Fixtures.racine, "fixtures/lidar_039a/cvat_925000_6700000.csv").readLines().filter { it.isNotBlank() }
            .map { l -> l.split(",").map(String::toInt) }
        assertEquals(50, attendu.size)
        for (l in 0 until 50) for (c in 0 until 50) assertEquals(attendu[l][c], g.valeur(l, c), "ligne $l colonne $c")
    }

    @Test
    fun cvat_centreNonAligne_resteDe50x50PixelsSurLaGrilleDeLaForet() {
        val r = decouper(exemple, 925000.7, 6700000.9)
        val f = Files.createTempFile("cvat039a", ".tif").toFile().also { it.writeBytes(assertNotNull(r.fichiers["lidar/cvat.tif"])) }
        val g = assertNotNull(GeoTiffOctets.fenetre(f, Emprise(0.0, 0.0, 9e6, 9e7)))
        assertEquals(50, g.largeur); assertEquals(50, g.hauteur); assertEquals(2.0, g.res, 1e-12)
        assertEquals(0.0, alignee(g.x0, 924850.0, 2.0), 1e-9, "alignée sur le raster CVAT")
        assertEquals(0.0, alignee(g.y0, 6700250.0, 2.0), 1e-9)
    }

    @Test
    fun sansCvat_pasDeFichierEtPasDErreur() {
        val r = decouper(projet(*tuiles.toTypedArray()), 925000.0, 6700000.0) // no cvat.tif
        assertFalse(r.fichiers.containsKey("lidar/cvat.tif"))
        assertNull(r.manifeste["cvat"])
        assertTrue(r.fichiers.containsKey("lidar/mnt.tif"), "le MNT n'est pas perdu pour autant : ${remarque(r)}")
    }

    // ---- 6. houppiers, schéma 1.7.0 ------------------------------------------------------------

    /** Project 1.7.0 whose crowns have unique ids (9000 + fid) and tops far away, to be placed by hand. */
    private fun base170(): Pair<Connection, SqliteJdbc> {
        val cx = ouvrir(gpkg170); val db = SqliteJdbc(cx)
        db.executer("UPDATE houppiers SET houppier_id = fid + 9000, x_sommet = 1000.0, y_sommet = 1000.0", listOf())
        return cx to db
    }

    private fun fid(db: SqliteJdbc, filtre: String, rang: Int): Int =
        (db.requete("SELECT fid FROM houppiers WHERE $filtre ORDER BY fid LIMIT 1 OFFSET $rang", listOf()).first()["fid"] as Number).toInt()

    private fun sommet(db: SqliteJdbc, fid: Int, x: Double, y: Double) =
        db.executer("UPDATE houppiers SET x_sommet = ?, y_sommet = ? WHERE fid = ?", listOf(x, y, fid))

    private fun idsGeojson(o: ByteArray?): List<Int> = o?.let {
        json(it).getValue("features").jsonArray.map { f ->
            f.jsonObject.getValue("properties").jsonObject.getValue("houppier_id").jsonPrimitive.content.toDouble().toInt()
        }
    } ?: listOf()

    @Test
    fun houppiers170_filtreParSommetDansLeCarre_bordsEtPlotIdNull() {
        val (cx, db) = base170()
        cx.use {
            val a = fid(db, "plot_id = 'P001'", 0); val b = fid(db, "plot_id = 'P001'", 1)
            val c = fid(db, "plot_id IS NULL", 0); val d = fid(db, "plot_id = 'P001'", 2)
            val e = fid(db, "plot_id = 'P002'", 0); val f = fid(db, "plot_id = 'P001'", 3)
            val g = fid(db, "plot_id IS NULL", 1)
            sommet(db, a, 925049.99, 6700000.0)   // just inside the east edge: kept
            sommet(db, b, 925050.01, 6700010.0)   // just outside the east edge (its ring is near the centre): dropped
            sommet(db, c, 924950.01, 6700049.99)  // plot_id NULL, just inside the north-west corner: kept
            sommet(db, d, 925050.0, 6700050.0)    // exactly on the north-east corner: BETWEEN includes it
            sommet(db, e, 924990.0, 6699960.0)    // another plot, inside: kept (crowns of the whole forest)
            sommet(db, f, 925000.0, 6700050.5)    // x inside, y just outside: dropped
            sommet(db, g, 6700000.0, 925000.0)    // east and north swapped: dropped
            val r = LidarPaquet.decouper(exemple, db, "P001", 925000.0, 6700000.0, 15.0)
            val fc = json(assertNotNull(r.fichiers["lidar/houppiers.geojson"], remarque(r)))
            assertEquals("FeatureCollection", fc.getValue("type").jsonPrimitive.content)
            assertTrue(fc.getValue("crs").toString().contains("2154"), "membre crs EPSG:2154")
            assertEquals(setOf(a, c, d, e).map { it + 9000 }.sorted(), idsGeojson(r.fichiers["lidar/houppiers.geojson"]).sorted(),
                "gardés : A, C (plot NULL), D (coin exact), E (autre placette)")
            assertEquals("lidar/houppiers.geojson", r.manifeste["houppiers"])
            val feats = fc.getValue("features").jsonArray
            for (ft in feats) {
                val p = ft.jsonObject.getValue("properties").jsonObject
                for (cle in listOf("houppier_id", "h_max", "surface_m2", "x_sommet", "y_sommet")) assertTrue(p.containsKey(cle), "champ $cle")
                val geom = ft.jsonObject.getValue("geometry").jsonObject
                val type = geom.getValue("type").jsonPrimitive.content
                assertTrue(type == "Polygon" || type == "MultiPolygon", type)
                val xy = if (type == "Polygon") geom.getValue("coordinates").jsonArray[0].jsonArray[0].jsonArray
                else geom.getValue("coordinates").jsonArray[0].jsonArray[0].jsonArray[0].jsonArray
                val x = xy[0].jsonPrimitive.content.toDouble(); val y = xy[1].jsonPrimitive.content.toDouble()
                assertTrue(x in 924800.0..925400.0, "x = est ($x)"); assertTrue(y in 6699800.0..6700300.0, "y = nord ($y)")
            }
            val pa = feats.first { it.jsonObject.getValue("properties").jsonObject.getValue("houppier_id").jsonPrimitive.content.toDouble().toInt() == a + 9000 }
                .jsonObject.getValue("properties").jsonObject
            assertEquals(925049.99, pa.getValue("x_sommet").jsonPrimitive.content.toDouble(), 1e-6)
            assertEquals(6700000.0, pa.getValue("y_sommet").jsonPrimitive.content.toDouble(), 1e-6)
        }
    }

    @Test
    fun houppiers170_contourDansLeCarreMaisSommetDehors_estExclu() {
        val (cx, db) = base170()
        cx.use {
            val a = fid(db, "plot_id = 'P001'", 0) // ring around (925000, 6700000), well inside the square
            sommet(db, a, 925050.01, 6700000.0)
            val r = LidarPaquet.decouper(exemple, db, "P001", 925000.0, 6700000.0, 15.0)
            assertFalse(idsGeojson(r.fichiers["lidar/houppiers.geojson"]).contains(a + 9000))
        }
    }

    @Test
    fun houppiers170_identifiantsEnDouble_donnentUneRemarque() {
        val (cx, db) = base170()
        cx.use {
            val a = fid(db, "plot_id = 'P001'", 0); val b = fid(db, "plot_id IS NULL", 0)
            sommet(db, a, 925000.0, 6700000.0); sommet(db, b, 925010.0, 6700010.0)
            db.executer("UPDATE houppiers SET houppier_id = 1 WHERE fid IN (?, ?)", listOf(a, b))
            val r = LidarPaquet.decouper(exemple, db, "P001", 925000.0, 6700000.0, 15.0)
            assertTrue(remarque(r).isNotBlank(), "houppier_id non unique dans la sélection : remarque")
        }
    }

    // ---- 7. repli schéma 1.6.0 -----------------------------------------------------------------

    /** Bounding boxes (R*Tree of the fixture, 32-bit floats) of the crowns of a plot: minx, maxx, miny, maxy. */
    private fun boites(db: SqliteJdbc, plot: String): List<DoubleArray> =
        db.requete("SELECT r.minx, r.maxx, r.miny, r.maxy FROM houppiers h JOIN rtree_houppiers_geom r ON r.id = h.fid WHERE h.plot_id = ?", listOf(plot))
            .map { doubleArrayOf((it["minx"] as Number).toDouble(), (it["maxx"] as Number).toDouble(), (it["miny"] as Number).toDouble(), (it["maxy"] as Number).toDouble()) }

    @Test
    fun houppiers160_ceuxDeLaPlacetteQuiTouchentLeCarre_sansSommet() {
        ouvrir(gpkg160).use { cx ->
            val db = SqliteJdbc(cx)
            // Square 925000-925100 (east) x 6699950-6700050: its west edge cuts through the crowns of P001 (bbox 924974-925020).
            val boites = boites(db, "P001")
            val surs = boites.count { it[0] >= 925000.0 + 0.5 }   // certainly touch the square
            val hors = boites.count { it[1] < 925000.0 - 0.5 }    // certainly do not
            assertTrue(surs >= 5 && hors >= 5, "données d'essai : $surs / $hors")
            val r = LidarPaquet.decouper(exemple, db, "P001", 925050.0, 6700000.0, 15.0)
            val fc = json(assertNotNull(r.fichiers["lidar/houppiers.geojson"], remarque(r)))
            val feats = fc.getValue("features").jsonArray
            assertTrue(feats.size in surs..(boites.size - hors), "${feats.size} houppiers, entre $surs et ${boites.size - hors}")
            for (ft in feats) {
                val p = ft.jsonObject.getValue("properties").jsonObject
                assertTrue(p.containsKey("houppier_id") && p.containsKey("h_max") && p.containsKey("surface_m2"))
                assertFalse(p.containsKey("x_sommet") || p.containsKey("y_sommet"), "schéma 1.6.0 : pas de sommet")
            }
        }
    }

    @Test
    fun houppiers160_aucunHouppierDansLeCarre_remarque() {
        ouvrir(gpkg160).use { cx ->
            // Around (925150, 6700000): P001's crowns are at x <= 925020, P002's at y >= 6700070: none touch.
            val r = LidarPaquet.decouper(exemple, SqliteJdbc(cx), "P001", 925150.0, 6700000.0, 15.0)
            assertFalse(r.fichiers.containsKey("lidar/houppiers.geojson"))
            assertNull(r.manifeste["houppiers"])
            assertTrue(remarque(r).isNotBlank(), "aucun houppier : remarque")
        }
    }

    @Test
    fun houppiers160_neRetientQueLaPlacetteDemandee() {
        ouvrir(gpkg160).use { cx ->
            // P002 asked at P001's centre: P001's crowns touch the square but are not P002's; P002's are > 50 m away.
            val r = LidarPaquet.decouper(exemple, SqliteJdbc(cx), "P002", 925000.0, 6700000.0, 15.0)
            assertFalse(r.fichiers.containsKey("lidar/houppiers.geojson"))
            assertTrue(remarque(r).isNotBlank())
        }
    }

    // ---- 8. repli : sans dalles ----------------------------------------------------------------

    @Test
    fun sansDalles_rasterDePlacetteJointTelQuel() {
        val r = decouper(projet("P001_mnt.tif", "P001_mnh.tif"), 925000.0, 6700000.0, rayon = 15.0)
        assertContentEquals(File(exemple, "rasters/P001_mnt.tif").readBytes(), r.fichiers["lidar/mnt.tif"], remarque(r))
        assertContentEquals(File(exemple, "rasters/P001_mnh.tif").readBytes(), r.fichiers["lidar/mnh.tif"])
        assertEquals("lidar/mnt.tif", r.manifeste["mnt"]); assertEquals("lidar/mnh.tif", r.manifeste["mnh"])
        @Suppress("UNCHECKED_CAST") assertEquals("placette", (r.manifeste["decoupe"] as Map<String, Any?>)["source_mnt"])
    }

    @Test
    fun sansDalles_rasterDePlacetteJusteAssezGrand_etJusteTropPetit() {
        // P001's raster spans +-30 m around (925000, 6700000).
        val d = projet("P001_mnt.tif")
        assertNotNull(decouper(d, 925000.0, 6700000.0, rayon = 29.5).fichiers["lidar/mnt.tif"], "rayon 29,5 m : couvert")
        val trop = decouper(d, 925000.0, 6700000.0, rayon = 30.5)
        assertFalse(trop.fichiers.containsKey("lidar/mnt.tif"), "rayon 30,5 m : le raster ne couvre pas")
        assertTrue(remarque(trop).contains("centre relevé"), remarque(trop))
    }

    @Test
    fun sansDalles_rasterDePlacetteQuiNeCouvrePasLeCentreReleve_remarque() {
        val r = decouper(projet("P001_mnt.tif", "P001_mnh.tif"), 925150.0, 6700000.0) // relevé 150 m from the raster
        assertFalse(r.fichiers.containsKey("lidar/mnt.tif")); assertFalse(r.fichiers.containsKey("lidar/mnh.tif"))
        assertNull(r.manifeste["mnt"]); assertNull(r.manifeste["mnh"])
        assertTrue(remarque(r).contains("centre relevé"), remarque(r))
    }

    @Test
    fun uneDalleSansPixelValide_ouAucuneDalle_memeRepli() {
        // Tile 0924_6699 exists but is all nodata around (924400, 6699400).
        val r = decouper(projet("mnt_0924_6699.tif"), 924400.0, 6699400.0)
        assertFalse(r.fichiers.containsKey("lidar/mnt.tif"))
        assertTrue(remarque(r).contains("centre relevé"), remarque(r))
        // No tile at all under the square.
        val r2 = decouper(exemple, 930000.0, 6710000.0)
        assertFalse(r2.fichiers.containsKey("lidar/mnt.tif"))
        assertTrue(remarque(r2).contains("centre relevé"), remarque(r2))
    }

    @Test
    fun dossierSansRasters_ouInexistant_neLevePas() {
        val vide = Files.createTempDirectory("vide039a").toFile()
        val r = decouper(vide, 925000.0, 6700000.0)
        assertFalse(r.fichiers.containsKey("lidar/mnt.tif"))
        assertTrue(remarque(r).contains("centre relevé"), remarque(r))
        val absent = decouper(File(vide, "n_existe_pas"), 925000.0, 6700000.0)
        assertFalse(absent.fichiers.containsKey("lidar/mnt.tif"))
        assertTrue(remarque(absent).isNotBlank())
    }

    @Test
    fun baseInutilisable_neLevePas_etDitPourquoi() {
        val cassee = object : BaseSqlite {
            override fun executer(sql: String, args: List<Any?>) = throw IllegalStateException("base fermée")
            override fun requete(sql: String, args: List<Any?>): List<Map<String, Any?>> = throw IllegalStateException("base fermée")
            override fun <T> transactionImmediate(bloc: () -> T): T = throw IllegalStateException("base fermée")
        }
        val r = LidarPaquet.decouper(exemple, cassee, "P001", 925000.0, 6700000.0, 15.0)
        assertFalse(r.fichiers.containsKey("lidar/houppiers.geojson"))
        assertTrue(remarque(r).isNotBlank())
    }

    // ---- 9. dalle corrompue --------------------------------------------------------------------

    @Test
    fun dalleCorrompue_neLevePas_pasDeMnt_remarque() {
        val reel = File(exemple, "rasters/mnt_0925_6700.tif").readBytes()
        val cas = mapOf("du bruit" to ByteArray(5000) { (it * 31 % 251).toByte() }, "tronquée" to reel.copyOf(1200), "vide" to ByteArray(0))
        for ((nom, octets) in cas) {
            val d = Files.createTempDirectory("corrompu039a").toFile()
            File(d, "rasters").mkdirs(); File(d, "rasters/mnt_0925_6700.tif").writeBytes(octets)
            // The square (925500 +- 50, 6700500 +- 50) lies entirely in this tile.
            val r = decouper(d, 925500.0, 6700500.0)
            assertFalse(r.fichiers.containsKey("lidar/mnt.tif"), "dalle $nom : pas de MNT")
            assertNull(r.manifeste["mnt"], "dalle $nom")
            assertTrue(remarque(r).isNotBlank(), "dalle $nom : remarque")
        }
    }

    // ---- 10. MNT partiel -----------------------------------------------------------------------

    @Test
    fun mntPartiel_remarqueAvecLePourcentageDeLaPlacette() {
        // Valid data end at x = 925350; the plot (r = 15 m) is centred 10 m before that edge.
        val r = decouper(exemple, 925340.0, 6700000.0, rayon = 15.0)
        assertNotNull(r.fichiers["lidar/mnt.tif"], "au moins un pixel valide : le MNT est écrit")
        val rem = remarque(r)
        assertTrue(rem.contains("MNT partiel"), rem)
        // Disk area on the valid side of the chord at 10 m from the centre.
        val rr = 15.0; val dd = 10.0
        val segment = rr * rr * acos(dd / rr) - dd * sqrt(rr * rr - dd * dd) // part beyond the chord
        val attendu = 100.0 * (PI * rr * rr - segment) / (PI * rr * rr)
        val n = Regex("(\\d+(?:[.,]\\d+)?)\\s*%").find(rem.substringAfter("MNT partiel"))?.groupValues?.get(1)?.replace(',', '.')?.toDouble()
        assertNotNull(n, "« MNT partiel : n % de la placette » : $rem")
        assertEquals(attendu, n, 3.0, "pourcentage de la placette couvert")
        assertTrue(rem.contains("placette"))
    }

    @Test
    fun mntComplet_pasDeRemarquePartiel() {
        assertFalse(remarque(decouper(exemple, 925000.0, 6700000.0)).contains("partiel"))
    }

    // ---- MNH de placette avec les dalles ---------------------------------------------------------

    @Test
    fun mnhDeLaPlacette_estJoint_quandIlCouvreLeCentreReleve() {
        val r = decouper(exemple, 925000.0, 6700000.0) // P001 relevé on its planned centre
        assertContentEquals(File(exemple, "rasters/P001_mnh.tif").readBytes(), r.fichiers["lidar/mnh.tif"], "MNH de placette joint tel quel")
        assertEquals("lidar/mnh.tif", r.manifeste["mnh"])
    }
}
