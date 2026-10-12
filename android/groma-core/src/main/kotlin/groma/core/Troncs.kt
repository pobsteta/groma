package groma.core

import kotlin.math.floor
import kotlin.math.hypot

/** Trunk detection in the 1.20-1.40 m slice (port of groma/troncs.py). */
data class TroncDetecte(
    val x: Double, val y: Double, val dbhCm: Double, val rmseM: Double,
    val couvertureDeg: Double, val nPoints: Int, val score: Double,
)

object Troncs {
    /** Keep the (x, y) of points whose height above ground is in [hBas, hHaut]. */
    fun extraireTranche(
        x: DoubleArray, y: DoubleArray, z: DoubleArray, sol: (Double, Double) -> Double,
        hBas: Double = 1.20, hHaut: Double = 1.40,
    ): Pair<DoubleArray, DoubleArray> {
        val ox = ArrayList<Double>(); val oy = ArrayList<Double>()
        for (i in x.indices) {
            val h = z[i] - sol(x[i], y[i])
            if (!h.isNaN() && h >= hBas && h <= hHaut) { ox += x[i]; oy += y[i] }
        }
        return Pair(ox.toDoubleArray(), oy.toDoubleArray())
    }

    /** DBSCAN with a hash grid of cell size eps. Labels, -1 = noise. */
    fun dbscan(x: DoubleArray, y: DoubleArray, eps: Double, minPoints: Int): IntArray {
        val n = x.size
        val grille = HashMap<Long, MutableList<Int>>()
        fun cle(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
        for (i in 0 until n) grille.getOrPut(cle(floor(x[i] / eps).toInt(), floor(y[i] / eps).toInt())) { ArrayList() } += i
        fun voisins(i: Int): List<Int> {
            val cx = floor(x[i] / eps).toInt(); val cy = floor(y[i] / eps).toInt()
            val out = ArrayList<Int>()
            for (dx in -1..1) for (dy in -1..1) grille[cle(cx + dx, cy + dy)]?.forEach { j ->
                if (hypot(x[i] - x[j], y[i] - y[j]) <= eps) out += j
            }
            return out
        }
        val labels = IntArray(n) { -1 }
        val vois = Array(n) { voisins(it) }
        val coeur = BooleanArray(n) { vois[it].size >= minPoints }
        var etiquette = 0
        for (i in 0 until n) {
            if (labels[i] != -1 || !coeur[i]) continue
            labels[i] = etiquette
            val pile = ArrayDeque(listOf(i))
            while (pile.isNotEmpty()) {
                val j = pile.removeLast()
                if (!coeur[j]) continue
                for (k in vois[j]) if (labels[k] == -1) { labels[k] = etiquette; pile.addLast(k) }
            }
            etiquette++
        }
        return labels
    }

    fun detecter(
        x: DoubleArray, y: DoubleArray, epsM: Double = 0.05, minPoints: Int = 10, seuilRansacM: Double = 0.015,
        rayonMaxM: Double = 1.5, couvertureMinDeg: Double = 40.0,
    ): List<TroncDetecte> {
        val labels = dbscan(x, y, epsM, minPoints)
        val groupes = labels.indices.filter { labels[it] >= 0 }.groupBy { labels[it] }.values
        val ajustes = ArrayList<Pair<CercleAjuste, List<Int>>>()
        for (g in groupes) {
            if (g.size < minPoints) continue
            val c = Cercle.ransac(DoubleArray(g.size) { x[g[it]] }, DoubleArray(g.size) { y[g[it]] }, seuilRansacM, rayonMaxM = rayonMaxM) ?: continue
            ajustes += Pair(c, g)
        }
        return fusionnerArcs(ajustes, x, y, seuilRansacM, rayonMaxM)
            .filter { it.couvertureDeg >= couvertureMinDeg }
            .map {
                val sCouv = minOf(1.0, it.couvertureDeg / 180.0)
                val sRmse = maxOf(0.0, 1.0 - it.rmseM / maxOf(0.03, 0.1 * it.rayonM))
                TroncDetecte(it.xc, it.yc, Math.round(it.dbhCm * 10) / 10.0, it.rmseM, it.couvertureDeg, it.nInliers,
                    Math.round((0.6 * sCouv + 0.4 * sRmse) * 1000) / 1000.0)
            }
    }

    /** Merge groups lying on the circle of a larger group (large trunks seen as broken arcs). */
    private fun fusionnerArcs(
        ajustes: List<Pair<CercleAjuste, List<Int>>>, x: DoubleArray, y: DoubleArray, seuil: Double, rayonMax: Double,
    ): List<CercleAjuste> {
        val tries = ajustes.sortedByDescending { it.first.rayonM }
        val absorbe = BooleanArray(tries.size)
        val sortie = ArrayList<CercleAjuste>()
        for (i in tries.indices) {
            if (absorbe[i]) continue
            var cercle = tries[i].first
            val pts = ArrayList(tries[i].second)
            for (j in i + 1 until tries.size) {
                if (absorbe[j]) continue
                val (cj, gj) = tries[j]
                if (hypot(cj.xc - cercle.xc, cj.yc - cercle.yc) > cercle.rayonM + cj.rayonM + 0.1) continue
                val proches = gj.count { kotlin.math.abs(hypot(x[it] - cercle.xc, y[it] - cercle.yc) - cercle.rayonM) < 2.5 * seuil }
                if (proches >= 0.8 * gj.size) { absorbe[j] = true; pts += gj }
            }
            if (pts.size > tries[i].second.size) {
                val px = DoubleArray(pts.size) { x[pts[it]] }; val py = DoubleArray(pts.size) { y[pts[it]] }
                val (xc, yc, r) = Cercle.geometrique(px, py, Triple(cercle.xc, cercle.yc, cercle.rayonM))
                if (r <= rayonMax) {
                    var s = 0.0; val ix = ArrayList<Double>(); val iy = ArrayList<Double>()
                    for (k in px.indices) {
                        val res = hypot(px[k] - xc, py[k] - yc) - r
                        if (kotlin.math.abs(res) < 1.5 * seuil) { s += res * res; ix += px[k]; iy += py[k] }
                    }
                    cercle = CercleAjuste(xc, yc, r, if (ix.isEmpty()) Double.POSITIVE_INFINITY else kotlin.math.sqrt(s / ix.size),
                        pts.size, ix.size, Cercle.couvertureDeg(ix.toDoubleArray(), iy.toDoubleArray(), xc, yc))
                }
            }
            sortie += cercle
        }
        return sortie
    }
}
