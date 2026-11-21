package groma.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanCouchesTest {
    /** Little-endian Byte GeoTIFF written by hand: strips of [lignesParBande] rows, optional DEFLATE. */
    private fun ecrireTiff(l: Int, c: Int, x0: Double, y0: Double, res: Double, lignesParBande: Int, deflate: Boolean, valeur: (Int, Int) -> Int): File {
        val bandes = (0 until l step lignesParBande).map { debut ->
            val brut = ByteArray(minOf(lignesParBande, l - debut) * c) { valeur(debut + it / c, it % c).toByte() }
            if (!deflate) brut else {
                val d = Deflater(); d.setInput(brut); d.finish()
                val tampon = ByteArray(brut.size + 64); val n = d.deflate(tampon); d.end(); tampon.copyOf(n)
            }
        }
        val donnees = bandes.sumOf { it.size }
        val nb = bandes.size
        val entrees = 10
        val ifd = 8 + donnees
        val apres = ifd + 2 + 12 * entrees + 4
        val tOff = apres; val tCpt = tOff + 4 * nb; val tEch = tCpt + 4 * nb; val tPt = tEch + 24
        val b = ByteBuffer.allocate(tPt + 48).order(ByteOrder.LITTLE_ENDIAN)
        b.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(ifd)
        val debuts = IntArray(nb); var pos = 8
        bandes.forEachIndexed { i, x -> debuts[i] = pos; b.put(x); pos += x.size }
        fun e(tag: Int, type: Int, count: Int, v: Int) {
            b.putShort(tag.toShort()).putShort(type.toShort()).putInt(count)
            if (type == 3 && count == 1) b.putShort(v.toShort()).putShort(0) else b.putInt(v)
        }
        b.putShort(entrees.toShort())
        e(256, 3, 1, c); e(257, 3, 1, l); e(258, 3, 1, 8); e(259, 3, 1, if (deflate) 8 else 1)
        e(273, 4, nb, tOff); e(278, 3, 1, lignesParBande); e(279, 4, nb, tCpt)
        e(33550, 12, 3, tEch); e(33922, 12, 6, tPt); e(277, 3, 1, 1)
        b.putInt(0)
        debuts.forEach { b.putInt(it) }; bandes.forEach { b.putInt(it.size) }
        b.putDouble(res).putDouble(res).putDouble(0.0)
        b.putDouble(0.0).putDouble(0.0).putDouble(0.0).putDouble(x0).putDouble(y0).putDouble(0.0)
        return Files.createTempFile("cvat", ".tif").toFile().also { it.deleteOnExit(); it.writeBytes(b.array()) }
    }

    // 8 columns x 6 rows, 2 m pixels, top-left (1000, 2012): value = 10 * row + col + 1, except (row 2, col 3) = 0.
    private fun synthetique(deflate: Boolean) =
        ecrireTiff(6, 8, 1000.0, 2012.0, 2.0, 4, deflate) { l, c -> if (l == 2 && c == 3) 0 else 10 * l + c + 1 }

    @Test
    fun fenetreInterieure_brutEtDeflate() {
        for (deflate in listOf(false, true)) {
            // Columns 2..4 (x 1004 to 1010), rows 1..2 (y 2006 to 2010) -> rows 1 and 2.
            val g = assertNotNull(GeoTiffOctets.fenetre(synthetique(deflate), Emprise(1004.0, 2006.0, 1010.0, 2010.0)))
            assertEquals(3, g.largeur); assertEquals(2, g.hauteur)
            assertEquals(1004.0, g.x0, 1e-9); assertEquals(2010.0, g.y0, 1e-9)
            assertEquals(listOf(13, 14, 15, 23, 0, 25), g.octets.map { it.toInt() and 0xFF })
        }
    }

    @Test
    fun fenetreSurDeuxBandes() {
        // Rows 3 and 4 sit in two different strips (4 rows per strip).
        val g = assertNotNull(GeoTiffOctets.fenetre(synthetique(true), Emprise(1000.0, 2002.0, 1002.0, 2006.0)))
        assertEquals(1, g.largeur); assertEquals(2, g.hauteur)
        assertEquals(listOf(31, 41), g.octets.map { it.toInt() and 0xFF })
    }

    @Test
    fun zeroEstTransparent_autreValeurEnGris() {
        val g = assertNotNull(GeoTiffOctets.fenetre(synthetique(true), Emprise(1004.0, 2006.0, 1010.0, 2010.0)))
        val argb = PlanCouches.argbRelief(g)
        assertEquals(0, argb[4])
        assertEquals(0xFF000000.toInt() or (13 shl 16) or (13 shl 8) or 13, argb[0])
    }

    @Test
    fun fenetreSurLeBord_estRogneeAuRaster() {
        // Larger than the raster on every side: the whole 8 x 6 raster, nothing more.
        val g = assertNotNull(GeoTiffOctets.fenetre(synthetique(true), Emprise(900.0, 1900.0, 1100.0, 2100.0)))
        assertEquals(8, g.largeur); assertEquals(6, g.hauteur)
        assertEquals(1000.0, g.x0, 1e-9); assertEquals(2012.0, g.y0, 1e-9)
        assertEquals(1, g.valeur(0, 0)); assertEquals(58, g.valeur(5, 7))
        // Window overlapping only the last column and last row.
        val coin = assertNotNull(GeoTiffOctets.fenetre(synthetique(false), Emprise(1015.0, 2000.0, 1500.0, 2001.0)))
        assertEquals(1, coin.largeur); assertEquals(1, coin.hauteur)
        assertEquals(58, coin.valeur(0, 0))
    }

    @Test
    fun fenetreHorsDuRaster_estVide() {
        assertNull(GeoTiffOctets.fenetre(synthetique(true), Emprise(5000.0, 5000.0, 5100.0, 5100.0)))
        assertNull(GeoTiffOctets.fenetre(synthetique(true), Emprise(1000.0, 2100.0, 1016.0, 2200.0)))
        assertNull(GeoTiffOctets.fenetre(synthetique(true), Emprise(900.0, 1900.0, 1000.0, 2100.0)))
    }

    @Test
    fun fenetreDeLExempleCvat() {
        // Checked with gdal_translate -srcwin 100 100 3 2 on the same file.
        val f = Fixtures.racine.resolve("exemples/inventaire_video/rasters/cvat.tif")
        val g = assertNotNull(GeoTiffOctets.fenetre(f, Emprise(925050.0, 6700046.0, 925056.0, 6700050.0)))
        assertEquals(2.0, g.res, 1e-12)
        assertEquals(925050.0, g.x0, 1e-9); assertEquals(6700050.0, g.y0, 1e-9)
        assertEquals(listOf(128, 130, 131, 126, 128, 129), g.octets.map { it.toInt() and 0xFF })
    }

    @Test
    fun conversionLambertVersPlan_decalageAuCentreNordEnHaut() {
        val centre = Pair(925000.0, 6700000.0)
        assertEquals(Pair(0.0, 0.0), PlanCouches.decalage(925000.0, 6700000.0, centre))
        // 3 m east and 5 m north: positive on both axes (the screen flips y when drawing).
        assertEquals(Pair(3.0, 5.0), PlanCouches.decalage(925003.0, 6700005.0, centre))
        assertEquals(Pair(-4.0, -2.0), PlanCouches.decalage(924996.0, 6699998.0, centre))
        val g = GrilleOctets(3, 2, 924996.0, 6700001.0, 2.0, ByteArray(6))
        assertEquals(listOf(-4.0, 1.0, 2.0, -3.0), PlanCouches.rectangleRelief(g, centre))
    }

    // --- Crowns of an emprise -------------------------------------------------------------

    private fun ouvrir(chemin: String) = Fixtures.racine.resolve(chemin).toPath().let { src ->
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp")
    }

    @Test
    fun houppiersEn170_toutesPlacettesEtPlotIdNull() {
        ouvrir("exemples/inventaire_video/inventaire_video.gpkg").use { cx ->
            val db = SqliteJdbc(cx)
            val tous = Houppiers.dansEmprise(db, Emprise(0.0, 0.0, 9e6, 9e6))
            assertEquals(78, tous.size)
            assertTrue(tous.all { it.sommet != null })
            val nuls = db.requete("SELECT houppier_id FROM houppiers WHERE plot_id IS NULL").map { (it["houppier_id"] as Number).toInt() }.toSet()
            assertTrue(nuls.isNotEmpty() && nuls.all { id -> tous.any { it.houppierId == id } })
        }
    }

    @Test
    fun houppiersEn170_bornesIncluses() {
        ouvrir("exemples/inventaire_video/inventaire_video.gpkg").use { cx ->
            val db = SqliteJdbc(cx)
            val h = Houppiers.dansEmprise(db, Emprise(0.0, 0.0, 9e6, 9e6)).first()
            val (sx, sy) = h.sommet!!
            // Box reduced to the top itself: the bounds are included.
            val seul = Houppiers.dansEmprise(db, Emprise(sx, sy, sx, sy))
            assertTrue(seul.any { it.houppierId == h.houppierId })
            // Box entirely east of the ring: not returned, even with a wide margin on the top.
            val xmaxAnneau = h.anneau.maxOf { it.first }
            val apres = Houppiers.dansEmprise(db, Emprise(xmaxAnneau + 1, sy - 1, xmaxAnneau + 2, sy + 1), margeSommetM = 50.0)
            assertTrue(apres.none { it.houppierId == h.houppierId })
            // Top just west of the box, ring reaching into it: lost without margin, kept with it.
            val xmin = sx + 0.01
            val boite = Emprise(xmin, sy - 1, xmin + 5, sy + 1)
            assertTrue(Houppiers.dansEmprise(db, boite).none { it.houppierId == h.houppierId })
            if (xmaxAnneau >= xmin) assertTrue(Houppiers.dansEmprise(db, boite, margeSommetM = 20.0).any { it.houppierId == h.houppierId })
        }
    }

    @Test
    fun houppiersEn160_contoursSansSommets() {
        ouvrir("fixtures/schema_1_6_0/inventaire_video.gpkg").use { cx ->
            val db = SqliteJdbc(cx)
            val tous = Houppiers.dansEmprise(db, Emprise(0.0, 0.0, 9e6, 9e6))
            assertTrue(tous.isNotEmpty())
            assertTrue(tous.all { it.sommet == null })
            assertTrue(!CouchesPlan(null, tous).aSommets)
            // Filtering by the box of the ring: a point of one ring keeps that crown, not all of them.
            val h = tous.first()
            val (x, y) = h.anneau.first()
            val proches = Houppiers.dansEmprise(db, Emprise(x, y, x, y))
            assertTrue(proches.any { it.houppierId == h.houppierId })
            assertTrue(proches.size < tous.size)
        }
    }
}
