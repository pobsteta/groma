package groma.core

import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mission 045: trees drawn on the images of a package. */
class ImagesPaquetTest {
    private val k = IntrinsequesImage(415.9, 415.9, 320.0, 240.0, 640, 480)
    private val kProf = IntrinsequesImage(103.975, 103.975, 80.0, 45.0, 160, 90)

    /** Row-major world_from_camera of a camera at local [pos] looking at local [cible] (OpenGL: x right, y up, -z forward). */
    private fun pose(pos: DoubleArray, cible: DoubleArray): DoubleArray {
        val f = DoubleArray(3) { cible[it] - pos[it] }.let { v -> val n = sqrt(v.sumOf { it * it }); DoubleArray(3) { v[it] / n } }
        val r = doubleArrayOf(f[1], -f[0], 0.0).let { v -> val n = hypot(v[0], v[1]); DoubleArray(3) { v[it] / n } } // f x up(0,0,1)
        val u = doubleArrayOf(r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
        fun monde(l: DoubleArray) = doubleArrayOf(l[0], l[2], -l[1]) // local -> ARCore world
        val cx = monde(r); val cy = monde(u); val cz = monde(doubleArrayOf(-f[0], -f[1], -f[2])); val t = monde(pos)
        return doubleArrayOf(cx[0], cy[0], cz[0], t[0], cx[1], cy[1], cz[1], t[1], cx[2], cy[2], cz[2], t[2], 0.0, 0.0, 0.0, 1.0)
    }

    @Test
    fun projectionDevantEtDerriere() {
        // Camera at the local origin, 1.5 m high, looking along local +y (= world -Z, the ARCore forward axis).
        val p = pose(doubleArrayOf(0.0, 0.0, 1.5), doubleArrayOf(0.0, 10.0, 1.5))
        val centre = assertNotNull(CameraPaquet.projeter(p, k, 0.0, 5.0, 1.5))
        assertEquals(320.0, centre.u, 1e-9); assertEquals(240.0, centre.v, 1e-9); assertEquals(5.0, centre.profondeurM, 1e-9)
        // 1 m to the right (local +x) at 5 m: u = cx + fx / 5 ; 1 m higher: v = cy - fy / 5.
        val droite = assertNotNull(CameraPaquet.projeter(p, k, 1.0, 5.0, 2.5))
        assertEquals(320.0 + 415.9 / 5, droite.u, 1e-9); assertEquals(240.0 - 415.9 / 5, droite.v, 1e-9)
        assertNull(CameraPaquet.projeter(p, k, 0.0, -5.0, 1.5), "derrière la caméra")
    }

    @Test
    fun conventionDuPaquetReel() {
        // Pose of image 45 of P067 (manifest, row major) : the camera looks toward world -Z... checked by the ray round trip.
        val p = doubleArrayOf(0.3265937566757202, 0.04459916800260544, -0.9441120624542236, -0.03081648424267769,
            -0.039451852440834045, 0.9986587762832642, 0.033528462052345276, 0.17891477048397064,
            0.9443411827087402, 0.02629677765071392, 0.3279152512550354, -2.274913787841797, 0.0, 0.0, 0.0, 1.0)
        val r = CameraPaquet.rayonLocal(p, k, 90.5, 241.0)
        val pt = DoubleArray(3) { r.o[it] + 6.0 * r.d[it] }
        val px = assertNotNull(CameraPaquet.projeter(p, k, pt[0], pt[1], pt[2]))
        assertEquals(90.5, px.u, 1e-3); assertEquals(241.0, px.v, 1e-3) // the float pose is orthonormal to ~1e-7
        val l = CameraPaquet.pointLocal(p, k, 90.5, 241.0, px.profondeurM)
        for (i in 0..2) assertEquals(pt[i], l[i], 1e-4)
    }

    @Test
    fun allerRetourLambert93Local() {
        val g = Georef(Pair(2.5, -1.25), Pair(849659.2231477546, 6689237.972029034), 37.4, emptyMap())
        val (x, y) = g.versGrille(7.123, 4.456)
        val (lx, ly) = g.versLocal(x, y)
        assertEquals(7.123, lx, 1e-3); assertEquals(4.456, ly, 1e-3)
        val (gx, gy) = g.versGrille(lx, ly)
        assertEquals(x, gx, 1e-3); assertEquals(y, gy, 1e-3)
    }

    // ---- synthetic scene -------------------------------------------------------------------------------------

    private val vraiGeoref = Georef(Pair(2.0, -1.0), Pair(849659.2231477546, 6689237.972029034), 37.0, emptyMap())
    private val zSol = 0.4

    private fun lambert(az: Double, d: Double) = vraiGeoref.centreGrille.let { Pair(it.first + d * Math.sin(Math.toRadians(az)), it.second + d * Math.cos(Math.toRadians(az))) }

    private fun scene(avecC0: Boolean = true, mesures: Boolean = true): Pair<ManifestePaquet, List<ArbrePlacette>> {
        val cibles = mapOf("C0" to Pair(vraiGeoref.centreGrille, 0.0), "C1" to Pair(lambert(20.0, 8.0), 20.0), "C2" to Pair(lambert(140.0, 12.0), 140.0), "C3" to Pair(lambert(260.0, 6.5), 260.0))
        val locales = cibles.mapValues { (_, v) -> vraiGeoref.versLocal(v.first.first, v.first.second) }
        val images = ArrayList<ImagePaquet>(); val dets = ArrayList<CibleVue>()
        val cible0 = doubleArrayOf(2.0, -1.0, 1.3)
        for ((n, ang) in listOf(0.0, 70.0, 140.0, 210.0, 280.0).withIndex()) {
            val a = Math.toRadians(ang)
            val pos = doubleArrayOf(2.0 + 7 * Math.cos(a), -1.0 + 7 * Math.sin(a), 1.5)
            val im = ImagePaquet(n, pose(pos, cible0), true, "images/%06d.jpg".format(n), k, null, null)
            images += im
            for ((id, l) in locales) {
                if (id == "C0" && !avecC0) continue
                val px = CameraPaquet.projeter(im.pose, k, l.first, l.second, 1.3) ?: continue
                dets += CibleVue(n, id, px.u, px.v)
            }
        }
        val mes = if (mesures) cibles.filterKeys { it != "C0" }.map { (id, v) -> CibleMesuree(id, v.second, hypot(v.first.first - vraiGeoref.centreGrille.first, v.first.second - vraiGeoref.centreGrille.second), 14.0) } else emptyList()
        val m = ManifestePaquet("P1", vraiGeoref.centreGrille.first, vraiGeoref.centreGrille.second, "deg", 0.0, 0.0, mes, images, dets)
        val t1 = vraiGeoref.versGrille(5.0, 1.0); val t2 = vraiGeoref.versGrille(-1.0, 3.0)
        val arbres = listOf(
            ArbrePlacette("u1", "3", t1.first, t1.second, 32.4, false, null, "vivant", null),
            ArbrePlacette("u2", "7", t2.first, t2.second, null, false, null, "vivant", null),
        )
        return Pair(m, arbres)
    }

    @Test
    fun sceneSynthetiqueLesArbresRetombentAMoinsDeDeuxPixels() {
        val (m, arbres) = scene()
        val place = PlacementPaquet.calculer(m)
        val geo = assertNotNull(place.georef)
        assertNull(place.message)
        assertEquals(37.0, geo.rotationDeg, 1e-6)
        val sols = arbres.associate { it.uuid to zSol }
        val calcul = SurimpressionImage.enLocal(arbres, geo, sols)
        val vrai = SurimpressionImage.enLocal(arbres, vraiGeoref, sols)
        var vus = 0
        for (im in m.retenues) {
            val s = SurimpressionImage.calculer(im, m, calcul)
            val ref = SurimpressionImage.calculer(im, m, vrai)
            assertEquals(ref.arbres.size, s.arbres.size)
            for ((a, b) in s.arbres.zip(ref.arbres)) {
                assertTrue(hypot(a.piedU - b.piedU, a.piedV - b.piedV) < 2.0, "pied de ${a.etiquette}")
                assertTrue(hypot(a.teteU - b.teteU, a.teteV - b.teteV) < 2.0, "tête de ${a.etiquette}")
                vus++
            }
            assertEquals(m.detectionsCibles.count { it.image == im.id }, s.cibles.size)
        }
        assertTrue(vus >= 4, "des arbres sont vus ($vus)")
        assertEquals(setOf("3 · 32 cm", "7"), m.retenues.flatMap { SurimpressionImage.calculer(it, m, calcul).arbres.map { t -> t.etiquette } }.toSet())
    }

    @Test
    fun arbreDerriereLaCameraOuHorsImageNEstPasDessine() {
        val (m, _) = scene()
        val im = m.retenues[0]
        val c = CameraPaquet.centreLocal(im.pose)
        val devant = ArbreLocal(ArbrePlacette("a", "1", 0.0, 0.0, 20.0, false, null, "vivant", null), 2.0, -1.0, zSol)
        val derriere = ArbreLocal(ArbrePlacette("b", "2", 0.0, 0.0, 20.0, false, null, "vivant", null), c[0] + (c[0] - 2.0), c[1] + (c[1] + 1.0), zSol)
        val s = SurimpressionImage.calculer(im, m, listOf(devant, derriere, ArbreLocal(devant.arbre, 2.0, -1.0, null)))
        assertEquals(listOf("1 · 20 cm"), s.arbres.map { it.etiquette })
        assertTrue(s.arbres.none { it.etiquette.startsWith("2") }, "derrière la caméra")
    }

    @Test
    fun sansC0OnNePlacePasLesArbres() {
        val (m, _) = scene(avecC0 = false)
        val p = PlacementPaquet.calculer(m)
        assertNull(p.georef)
        assertEquals(PlacementPaquet.MESSAGE_C0, p.message)
    }

    @Test
    fun ciblesVuesSansMesureNeOriententPas() {
        val (m, _) = scene(mesures = false)
        val p = PlacementPaquet.calculer(m)
        assertNull(p.georef)
        assertTrue(p.message!!.contains("sans azimut ni distance"))
    }

    /** Depth map (uint16 mm) of a flat ground at local height [zg], seen by [im] with the depth intrinsics. */
    private fun profondeurSol(im: ImagePaquet, zg: Double): ByteArray {
        val b = ByteArray(kProf.largeur * kProf.hauteur * 2)
        for (v in 0 until kProf.hauteur) for (u in 0 until kProf.largeur) {
            val r = CameraPaquet.rayonLocal(im.pose, kProf, u + 0.5, v + 0.5)
            if (r.d[2] >= -1e-6) continue
            val t = (zg - r.o[2]) / r.d[2]
            val pt = DoubleArray(3) { r.o[it] + t * r.d[it] }
            val d = CameraPaquet.projeter(im.pose, kProf, pt[0], pt[1], pt[2])?.profondeurM ?: continue
            val mm = (d * 1000).roundToInt().coerceIn(0, 65535)
            b[(v * kProf.largeur + u) * 2] = (mm and 0xff).toByte(); b[(v * kProf.largeur + u) * 2 + 1] = (mm shr 8).toByte()
        }
        return b
    }

    @Test
    fun solLocalTireDesProfondeurs() {
        val arbres = mapOf("u1" to Pair(5.0, 1.0), "u2" to Pair(-1.0, 3.0), "u3" to Pair(40.0, 40.0))
        val images = (0 until 6).map { n ->
            val a = Math.toRadians(n * 60.0)
            val pos = doubleArrayOf(2.0 + 3.5 * Math.cos(a), 1.0 + 3.5 * Math.sin(a), 1.5)
            ImagePaquet(n, pose(pos, doubleArrayOf(2.0, 1.0, zSol)), true, null, k, "profondeurs/$n.u16", kProf)
        }
        val sols = SolLocal.estimer(arbres, images) { profondeurSol(it, zSol) }
        // u3 is out of reach of every depth map: it takes the median ground of the others.
        for (id in arbres.keys) assertEquals(zSol, sols.getValue(id), 0.01, id)
        assertTrue(SolLocal.estimer(arbres, images) { null }.isEmpty())
    }

    // ---- reading ---------------------------------------------------------------------------------------------

    @Test
    fun choixDuPaquet() {
        val f = listOf("P067.rejeu.groma.zip" to 9L, "P067.groma.zip" to 5L, "P0671.groma.zip" to 8L, "P067.avec-lidar.groma.zip" to 6L, "P067.groma.mp4" to 10L)
        assertEquals("P067.avec-lidar.groma.zip", ChoixPaquet.plusRecent("P067", f))
        assertNull(ChoixPaquet.plusRecent("P068", f))
        assertNull(ChoixPaquet.plusRecent("P067", listOf("P067.rejeu-20261120.groma.zip" to 1L)))
    }

    @Test
    fun jsonMini() {
        val v = JsonMini.lire("""{"a": [1, -2.5e1, "x\"éé", true, null], "b": {}}""") as Map<*, *>
        assertEquals(listOf(1.0, -25.0, "x\"éé", true, null), v["a"])
        assertEquals(emptyMap<String, Any?>(), v["b"])
    }

    @Test
    fun manifesteReelDeP067SansToutDecompresser() {
        val racine = File(System.getProperty("groma.racine"))
        val zip = File(racine, "exemples/marsannay_p067/paquets/P067.groma.zip")
        PaquetZip(zip).use { p ->
            val m = p.manifeste()
            assertEquals("P067", m.plotId)
            assertEquals(525, m.images.size)
            // The reduced copy keeps the whole manifest (525 poses) but only 21 image files; the field package has 525.
            assertEquals(21, m.retenues.size)
            assertEquals(25, m.detectionsCibles.size)
            assertEquals(mapOf("C0" to 3, "C1" to 17, "C3" to 5), m.detectionsCibles.groupingBy { it.idCible }.eachCount())
            assertEquals(3, m.ciblesMesurees.size)
            assertEquals(16, m.images[45].pose.size)
            assertEquals(640, m.images[45].k!!.largeur)
            // The reduced copy only keeps 21 files: one is read alone, the others stay compressed.
            val presente = m.retenues.first()
            assertTrue(p.contient(presente.image!!))
            val octets = assertNotNull(p.octets(presente.image!!))
            assertEquals(0xFF, octets[0].toInt() and 0xFF); assertEquals(0xD8, octets[1].toInt() and 0xFF)
            // C0 (3 views), C1 and C3 are enough to place the plot: the transform exists and is plausible.
            val place = PlacementPaquet.calculer(m)
            val g = assertNotNull(place.georef, place.message)
            assertTrue(abs(g.centreGrille.first - m.centreX) < 1e-9)
            assertTrue(place.residuDeg.isNaN() || place.residuDeg < 20.0)
        }
    }
}
