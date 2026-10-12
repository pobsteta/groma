package groma.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import kotlin.math.floor

/**
 * Minimal GeoTIFF reader for the Groma rasters (DTM / CHM clipped per plot):
 * one band Float32, strips, no compression or DEFLATE without predictor,
 * north-up (ModelPixelScale + ModelTiepoint). No GDAL on the phone.
 */
class Grille(val largeur: Int, val hauteur: Int, val x0: Double, val y0: Double, val res: Double, val valeurs: FloatArray) {
    fun valeur(ligne: Int, colonne: Int): Double {
        if (ligne !in 0 until hauteur || colonne !in 0 until largeur) return Double.NaN
        return valeurs[ligne * largeur + colonne].toDouble()
    }

    /** Bilinear interpolation, NaN outside or on nodata (same as groma/mnt.py). */
    fun echantillonner(x: Double, y: Double): Double {
        val c = (x - x0) / res - 0.5
        val l = (y0 - y) / res - 0.5
        val c0 = floor(c).toInt(); val l0 = floor(l).toInt()
        if (c0 < 0 || l0 < 0 || c0 + 1 >= largeur || l0 + 1 >= hauteur) return Double.NaN
        val fc = c - c0; val fl = l - l0
        return valeur(l0, c0) * (1 - fc) * (1 - fl) + valeur(l0, c0 + 1) * fc * (1 - fl) +
            valeur(l0 + 1, c0) * (1 - fc) * fl + valeur(l0 + 1, c0 + 1) * fc * fl
    }
}

object GeoTiff {
    fun lire(octets: ByteArray): Grille {
        val buf = ByteBuffer.wrap(octets)
        buf.order(
            when (String(octets, 0, 2, Charsets.US_ASCII)) {
                "II" -> ByteOrder.LITTLE_ENDIAN
                "MM" -> ByteOrder.BIG_ENDIAN
                else -> throw IllegalArgumentException("Pas un fichier TIFF.")
            }
        )
        require(buf.getShort(2).toInt() == 42) { "BigTIFF non pris en charge." }
        val ifd = buf.getInt(4)
        val n = buf.getShort(ifd).toInt() and 0xFFFF
        val tags = HashMap<Int, LongArray>()
        val doubles = HashMap<Int, DoubleArray>()
        var nodata: Double? = null
        for (k in 0 until n) {
            val e = ifd + 2 + 12 * k
            val tag = buf.getShort(e).toInt() and 0xFFFF
            val type = buf.getShort(e + 2).toInt()
            val count = buf.getInt(e + 4)
            val taille = mapOf(1 to 1, 2 to 1, 3 to 2, 4 to 4, 12 to 8, 16 to 8)[type] ?: 4
            val off = if (taille * count <= 4) e + 8 else buf.getInt(e + 8)
            when (type) {
                3 -> tags[tag] = LongArray(count) { (buf.getShort(off + 2 * it).toInt() and 0xFFFF).toLong() }
                4 -> tags[tag] = LongArray(count) { buf.getInt(off + 4 * it).toLong() and 0xFFFFFFFFL }
                12 -> doubles[tag] = DoubleArray(count) { buf.getDouble(off + 8 * it) }
                2 -> if (tag == 42113) nodata = String(octets, off, count, Charsets.US_ASCII).trim('\u0000', ' ').toDoubleOrNull()
            }
        }
        val largeur = tags.getValue(256)[0].toInt()
        val hauteur = tags.getValue(257)[0].toInt()
        require(tags[322] == null) { "Raster tuilé non pris en charge (écrire en bandes)." }
        require(tags[258]?.get(0) == 32L && tags[339]?.get(0) == 3L) { "Seul le Float32 est pris en charge." }
        require((tags[277]?.get(0) ?: 1L) == 1L) { "Une seule bande attendue." }
        require((tags[317]?.get(0) ?: 1L) == 1L) { "Prédicteur non pris en charge." }
        val compression = tags[259]?.get(0) ?: 1L
        val offsets = tags.getValue(273)
        val comptes = tags.getValue(279)
        val brut = ByteBuffer.allocate(largeur * hauteur * 4).order(buf.order())
        for (s in offsets.indices) {
            val bloc = octets.copyOfRange(offsets[s].toInt(), (offsets[s] + comptes[s]).toInt())
            brut.put(
                when (compression) {
                    1L -> bloc
                    8L, 32946L -> inflate(bloc)
                    else -> throw IllegalArgumentException("Compression $compression non prise en charge.")
                }
            )
        }
        brut.flip()
        val valeurs = FloatArray(largeur * hauteur) { brut.getFloat() }
        nodata?.let { nd -> for (i in valeurs.indices) if (valeurs[i].toDouble() == nd) valeurs[i] = Float.NaN }
        val echelle = doubles.getValue(33550)
        val point = doubles.getValue(33922)
        require(kotlin.math.abs(echelle[0] - echelle[1]) < 1e-9) { "Pixels carrés attendus." }
        // Tie point (i, j, k, x, y, z): pixel (i, j) is at (x, y).
        val x0 = point[3] - point[0] * echelle[0]
        val y0 = point[4] + point[1] * echelle[1]
        return Grille(largeur, hauteur, x0, y0, echelle[0], valeurs)
    }

    private fun inflate(bloc: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(bloc)
        val sortie = java.io.ByteArrayOutputStream(bloc.size * 4)
        val tampon = ByteArray(64 * 1024)
        while (!inf.finished()) {
            val n = inf.inflate(tampon)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            sortie.write(tampon, 0, n)
        }
        inf.end()
        return sortie.toByteArray()
    }
}
