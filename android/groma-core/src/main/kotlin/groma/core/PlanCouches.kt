package groma.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor

/** Box in Lambert-93 metres. */
data class Emprise(val xmin: Double, val ymin: Double, val xmax: Double, val ymax: Double) {
    companion object {
        /** Square of half-side [demiM] around [centre] (x, y). */
        fun autour(centre: Pair<Double, Double>, demiM: Double) =
            Emprise(centre.first - demiM, centre.second - demiM, centre.first + demiM, centre.second + demiM)
    }
}

/** One band of bytes (CVAT relief), north-up: (x0, y0) is the top-left corner, [res] the pixel size in metres. */
class GrilleOctets(val largeur: Int, val hauteur: Int, val x0: Double, val y0: Double, val res: Double, val octets: ByteArray) {
    /** Raw value 0 to 255 (0 = nodata). */
    fun valeur(ligne: Int, colonne: Int): Int = octets[ligne * largeur + colonne].toInt() and 0xFF
}

/** What the plan of a plot draws under the trees (mission 042): CVAT relief and LiDAR crowns. */
class CouchesPlan(val relief: GrilleOctets?, val houppiers: List<Houppier>) {
    val aSommets: Boolean get() = houppiers.any { it.sommet != null }
}

/** Pure helpers: Lambert-93 to plan offsets (east, north, in metres from the centre) and relief pixels. */
object PlanCouches {
    /** Offset (east, north) of a Lambert-93 point from the plot centre: the plan is north up. */
    fun decalage(x: Double, y: Double, centre: Pair<Double, Double>): Pair<Double, Double> =
        Pair(x - centre.first, y - centre.second)

    /** Corners of the relief as offsets from the centre: (west, north, east, south). */
    fun rectangleRelief(g: GrilleOctets, centre: Pair<Double, Double>): List<Double> {
        val (o, n) = decalage(g.x0, g.y0, centre)
        return listOf(o, n, o + g.largeur * g.res, n - g.hauteur * g.res)
    }

    /** ARGB pixels in grey levels, 0 (nodata) fully transparent; the opacity is applied when drawing. */
    fun argbRelief(g: GrilleOctets): IntArray = IntArray(g.largeur * g.hauteur) {
        val v = g.octets[it].toInt() and 0xFF
        if (v == 0) 0 else (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
}

/**
 * Window of a one-band Byte GeoTIFF (`rasters/cvat.tif`, the whole forest): only the strips that
 * meet the window are read and inflated, never the whole file. Strips, none or DEFLATE
 * compression, horizontal predictor accepted, north-up like [GeoTiff].
 */
object GeoTiffOctets {
    /** Part of the raster inside [e] (clipped to the raster), or null when they do not meet. */
    fun fenetre(fichier: File, e: Emprise): GrilleOctets? = RandomAccessFile(fichier, "r").use { f ->
        val entete = ByteArray(8).also { f.seek(0); f.readFully(it) }
        val ordre = when (String(entete, 0, 2, Charsets.US_ASCII)) {
            "II" -> ByteOrder.LITTLE_ENDIAN
            "MM" -> ByteOrder.BIG_ENDIAN
            else -> throw IllegalArgumentException("Pas un fichier TIFF.")
        }
        val eb = ByteBuffer.wrap(entete).order(ordre)
        require(eb.getShort(2).toInt() == 42) { "BigTIFF non pris en charge." }
        fun lire(pos: Long, n: Int): ByteBuffer = ByteBuffer.wrap(ByteArray(n).also { f.seek(pos); f.readFully(it) }).order(ordre)
        val ifd = eb.getInt(4).toLong() and 0xFFFFFFFFL
        val n = lire(ifd, 2).getShort(0).toInt() and 0xFFFF
        val entrees = lire(ifd + 2, 12 * n)
        val entiers = HashMap<Int, LongArray>()
        val reels = HashMap<Int, DoubleArray>()
        for (k in 0 until n) {
            val e0 = 12 * k
            val tag = entrees.getShort(e0).toInt() and 0xFFFF
            val type = entrees.getShort(e0 + 2).toInt()
            val count = entrees.getInt(e0 + 4)
            val taille = mapOf(1 to 1, 2 to 1, 3 to 2, 4 to 4, 12 to 8)[type] ?: continue
            val valeurs = if (taille * count <= 4) ByteBuffer.wrap(ByteArray(4).also { entrees.get(e0 + 8, it) }).order(ordre)
            else lire(entrees.getInt(e0 + 8).toLong() and 0xFFFFFFFFL, taille * count)
            when (type) {
                3 -> entiers[tag] = LongArray(count) { (valeurs.getShort(2 * it).toInt() and 0xFFFF).toLong() }
                4 -> entiers[tag] = LongArray(count) { valeurs.getInt(4 * it).toLong() and 0xFFFFFFFFL }
                12 -> reels[tag] = DoubleArray(count) { valeurs.getDouble(8 * it) }
            }
        }
        val largeur = entiers.getValue(256)[0].toInt()
        val hauteur = entiers.getValue(257)[0].toInt()
        require(entiers[322] == null) { "Raster tuilé non pris en charge (écrire en bandes)." }
        require((entiers[258]?.get(0) ?: 8L) == 8L) { "Seul l'octet est pris en charge." }
        require((entiers[277]?.get(0) ?: 1L) == 1L) { "Une seule bande attendue." }
        val compression = entiers[259]?.get(0) ?: 1L
        val predicteur = entiers[317]?.get(0) ?: 1L
        require(predicteur == 1L || predicteur == 2L) { "Prédicteur $predicteur non pris en charge." }
        val debuts = entiers.getValue(273)
        val comptes = entiers.getValue(279)
        val lignesParBande = minOf(entiers[278]?.get(0) ?: hauteur.toLong(), hauteur.toLong()).toInt().coerceAtLeast(1)
        val echelle = reels.getValue(33550)
        val point = reels.getValue(33922)
        require(kotlin.math.abs(echelle[0] - echelle[1]) < 1e-9) { "Pixels carrés attendus." }
        val res = echelle[0]
        val xr = point[3] - point[0] * res
        val yr = point[4] + point[1] * res

        val c0 = floor((e.xmin - xr) / res).toInt().coerceAtLeast(0)
        val c1 = ceil((e.xmax - xr) / res).toInt().coerceAtMost(largeur)
        val l0 = floor((yr - e.ymax) / res).toInt().coerceAtLeast(0)
        val l1 = ceil((yr - e.ymin) / res).toInt().coerceAtMost(hauteur)
        if (c1 <= c0 || l1 <= l0) return@use null

        val lw = c1 - c0
        val sortie = ByteArray(lw * (l1 - l0))
        for (b in (l0 / lignesParBande)..((l1 - 1) / lignesParBande)) {
            val premiere = b * lignesParBande
            val nb = minOf(lignesParBande, hauteur - premiere)
            val bloc = ByteArray(comptes[b].toInt()).also { f.seek(debuts[b]); f.readFully(it) }
            val brut = when (compression) {
                1L -> bloc
                8L, 32946L -> GeoTiff.inflate(bloc)
                else -> throw IllegalArgumentException("Compression $compression non prise en charge.")
            }
            require(brut.size >= nb * largeur) { "Bande de raster tronquée." }
            for (l in maxOf(l0, premiere) until minOf(l1, premiere + nb)) {
                val d = (l - premiere) * largeur
                if (predicteur == 2L) for (c in 1 until largeur) brut[d + c] = (brut[d + c] + brut[d + c - 1]).toByte()
                System.arraycopy(brut, d + c0, sortie, (l - l0) * lw, lw)
            }
        }
        GrilleOctets(lw, l1 - l0, xr + c0 * res, yr - l0 * res, res, sortie)
    }
}
