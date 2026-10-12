package groma.core

/**
 * LiDAR crowns: height pre-fill by point-in-polygon (port of groma/houppiers.py).
 * Tallest crown when crowns overlap, nothing outside every crown (no nearest
 * fallback), heights outside 1-70 m ignored, measured heights never overwritten.
 */
data class Houppier(val houppierId: Int, val anneau: List<Pair<Double, Double>>, val hMax: Double?) {
    private val xmin = anneau.minOf { it.first }
    private val xmax = anneau.maxOf { it.first }
    private val ymin = anneau.minOf { it.second }
    private val ymax = anneau.maxOf { it.second }

    /** Inside or on the boundary (same as shapely `covers`). */
    fun couvre(x: Double, y: Double): Boolean {
        if (x < xmin || x > xmax || y < ymin || y > ymax) return false
        var dedans = false
        val n = anneau.size
        var j = n - 1
        for (i in 0 until n) {
            val (xi, yi) = anneau[i]
            val (xj, yj) = anneau[j]
            if (surSegment(x, y, xi, yi, xj, yj)) return true
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) dedans = !dedans
            j = i
        }
        return dedans
    }

    private fun surSegment(x: Double, y: Double, x1: Double, y1: Double, x2: Double, y2: Double): Boolean {
        val croix = (x - x1) * (y2 - y1) - (y - y1) * (x2 - x1)
        if (kotlin.math.abs(croix) > 1e-9) return false
        return x >= minOf(x1, x2) - 1e-9 && x <= maxOf(x1, x2) + 1e-9 && y >= minOf(y1, y2) - 1e-9 && y <= maxOf(y1, y2) + 1e-9
    }
}

data class HauteurAffectee(val hM: Double?, val hSource: String, val houppierId: Int?)

object Houppiers {
    const val H_MIN_M = 1.0
    const val H_MAX_M = 70.0

    fun affecter(x: Double, y: Double, houppiers: List<Houppier>, hM: Double? = null, hSource: String? = null): HauteurAffectee {
        if (hSource == "mesuree" && hM != null) return HauteurAffectee(hM, "mesuree", null)
        val meilleur = houppiers
            .filter { h -> h.hMax != null && h.hMax in H_MIN_M..H_MAX_M && h.couvre(x, y) }
            .maxWithOrNull(compareBy<Houppier>({ it.hMax }).thenByDescending { it.houppierId })
            ?: return HauteurAffectee(null, "aucune", null)
        return HauteurAffectee(meilleur.hMax, "mnh", meilleur.houppierId)
    }

    /** Parse a WKT POLYGON exterior ring (enough for the crowns layer). */
    fun anneauWkt(wkt: String): List<Pair<Double, Double>> {
        val corps = wkt.substringAfter("((").substringBefore(")")
        return corps.split(",").map { p ->
            val (a, b) = p.trim().split(Regex("\\s+")).map(String::toDouble)
            Pair(a, b)
        }
    }
}
