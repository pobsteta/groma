package groma.core

import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Live capture quality control (phone): tracking, blur, speed, coverage of
 * the plot, targets seen; final verdict "suffisante / à refaire" with the
 * reasons. All positions in the ARCore-derived local frame (z up), relative
 * to the plot centre (C0 once seen, else the start position).
 */
data class EchantillonPrise(
    val t: Double, val x: Double, val y: Double, val z: Double,
    val visee: Pair<Double, Double>, // horizontal view direction (unit vector)
    val suivi: Boolean, val nettete: Double, val ciblesVues: Set<String>,
)

data class Verdict(val suffisante: Boolean, val raisons: List<String>, val couverturePct: Double)

class ControleQualite(
    private val rayonM: Double = 20.0,
    private val porteeM: Double = 8.0,
    private val secteurs: Int = 12,
    private val anneauxM: DoubleArray = doubleArrayOf(7.0, 14.0, 20.0),
    val netteteMin: Double = 60.0,
    val vitesseMaxMs: Double = 1.2,
) {
    private val vus = HashSet<Int>()
    private val compteCibles = HashMap<String, Int>()
    private var precedent: EchantillonPrise? = null
    private var nImages = 0
    private var nFloues = 0
    private var perteSuiviS = 0.0
    private var centre = Pair(0.0, 0.0)
    private var centreFixe = false
    val alertes = ArrayList<String>()

    fun fixerCentre(x: Double, y: Double) { centre = Pair(x, y); centreFixe = true }

    /** Returns the live alerts for this frame (empty when everything is fine). */
    fun ajouter(e: EchantillonPrise): List<String> {
        val a = ArrayList<String>()
        val p = precedent
        if (!centreFixe && p == null) centre = Pair(e.x, e.y)
        if (!e.suivi) {
            if (p != null) perteSuiviS += e.t - p.t
            a += "Suivi perdu : revenir lentement vers une zone déjà filmée."
        } else {
            nImages++
            if (e.nettete < netteteMin) { nFloues++; a += "Image floue : ralentir." }
            if (p != null && e.t > p.t) {
                val v = sqrt((e.x - p.x).let { it * it } + (e.y - p.y).let { it * it } + (e.z - p.z).let { it * it }) / (e.t - p.t)
                if (v > vitesseMaxMs) a += "Trop rapide (%.1f m/s).".format(v)
            }
            marquerCouverture(e)
            for (c in e.ciblesVues) compteCibles[c] = (compteCibles[c] ?: 0) + 1
        }
        precedent = e
        alertes += a
        return a
    }

    private fun cellule(x: Double, y: Double): Int? {
        val dx = x - centre.first; val dy = y - centre.second
        val d = hypot(dx, dy)
        if (d > rayonM) return null
        val anneau = anneauxM.indexOfFirst { d <= it }
        val az = (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
        return anneau * secteurs + floor(az / (360.0 / secteurs)).toInt()
    }

    private fun marquerCouverture(e: EchantillonPrise) {
        var d = 0.5
        while (d <= porteeM) {
            cellule(e.x + e.visee.first * d, e.y + e.visee.second * d)?.let { vus += it }
            d += 0.5
        }
    }

    fun couverturePct(): Double = 100.0 * vus.size / (secteurs * anneauxM.size)

    /** Cells of the plot never looked at, as (ring, sector) for the on-screen map. */
    fun zonesManquantes(): List<Pair<Int, Int>> =
        (0 until secteurs * anneauxM.size).filter { it !in vus }.map { Pair(it / secteurs, it % secteurs) }

    fun verdict(couvertureMinPct: Double = 90.0, perteSuiviMaxS: Double = 5.0, vuesCibleMin: Int = 3, partFloueMax: Double = 0.3): Verdict {
        val raisons = ArrayList<String>()
        val couv = couverturePct()
        if (couv < couvertureMinPct) raisons += "Placette couverte à %.0f %% (minimum %.0f %%) : compléter les zones grisées.".format(couv, couvertureMinPct)
        if (perteSuiviS > perteSuiviMaxS) raisons += "Suivi perdu pendant %.0f s.".format(perteSuiviS)
        for (c in listOf("C0", "C1", "C2", "C3")) {
            val n = compteCibles[c] ?: 0
            if (n < vuesCibleMin) raisons += "Cible $c vue $n fois (minimum $vuesCibleMin)."
        }
        if (nImages > 0 && nFloues.toDouble() / nImages > partFloueMax) raisons += "Trop d'images floues (%.0f %%).".format(100.0 * nFloues / nImages)
        if (nImages == 0) raisons += "Aucune image exploitable."
        return Verdict(raisons.isEmpty(), raisons, couv)
    }

    companion object {
        /** Variance of the Laplacian on a grayscale image (sharpness score). */
        fun nettete(gris: ByteArray, largeur: Int, hauteur: Int, pas: Int = 2): Double {
            var somme = 0.0; var somme2 = 0.0; var n = 0
            fun px(x: Int, y: Int) = (gris[y * largeur + x].toInt() and 0xFF).toDouble()
            var y = 1
            while (y < hauteur - 1) {
                var x = 1
                while (x < largeur - 1) {
                    val l = px(x - 1, y) + px(x + 1, y) + px(x, y - 1) + px(x, y + 1) - 4 * px(x, y)
                    somme += l; somme2 += l * l; n++
                    x += pas
                }
                y += pas
            }
            if (n == 0) return 0.0
            val m = somme / n
            return somme2 / n - m * m
        }
    }
}
