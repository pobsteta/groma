package groma.core

/**
 * LiDAR crowns: height pre-fill by point-in-polygon (port of groma/houppiers.py).
 * Tallest crown when crowns overlap, nothing outside every crown (no nearest
 * fallback), heights outside 1-70 m ignored, measured heights never overwritten.
 */
data class Houppier(
    val houppierId: Int, val anneau: List<Pair<Double, Double>>, val hMax: Double?,
    /** Localised top (Lambert-93), schema 1.7.0 only; null for a project prepared before. */
    val sommet: Pair<Double, Double>? = null,
) {
    private val xmin = anneau.minOf { it.first }
    private val xmax = anneau.maxOf { it.first }
    private val ymin = anneau.minOf { it.second }
    private val ymax = anneau.maxOf { it.second }

    /** True when the bounding box of the ring touches [e] (edges included). */
    fun touche(e: Emprise): Boolean = xmax >= e.xmin && xmin <= e.xmax && ymax >= e.ymin && ymin <= e.ymax

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

    /**
     * Crowns that touch [emprise] (mission 042), for the plan. Schema 1.7.0 (`x_sommet`/`y_sommet`
     * present): the B-tree index is used with BETWEEN on the top, the box widened by [margeSommetM]
     * so a crown whose top lies just outside is not lost; crowns of every plot, `plot_id` NULL
     * included (a plot moved by the C0 survey). Schema 1.6.0: no top, the crowns are filtered by
     * the box of their ring. In both cases only crowns whose ring touches [emprise] are returned.
     */
    fun dansEmprise(db: BaseSqlite, emprise: Emprise, margeSommetM: Double = 0.0): List<Houppier> {
        val colonnes = db.requete("PRAGMA table_info(houppiers)").mapNotNull { it["name"] as? String }.toSet()
        val avecSommet = "x_sommet" in colonnes && "y_sommet" in colonnes
        val lignes = if (avecSommet) {
            db.requete(
                "SELECT houppier_id, h_max, x_sommet, y_sommet, geom FROM houppiers WHERE x_sommet BETWEEN ? AND ? AND y_sommet BETWEEN ? AND ?",
                listOf(emprise.xmin - margeSommetM, emprise.xmax + margeSommetM, emprise.ymin - margeSommetM, emprise.ymax + margeSommetM),
            )
        } else db.requete("SELECT houppier_id, h_max, geom FROM houppiers")
        return lignes.mapNotNull { l ->
            val xs = (l["x_sommet"] as? Number)?.toDouble()
            val ys = (l["y_sommet"] as? Number)?.toDouble()
            val h = Houppier(
                (l["houppier_id"] as Number).toInt(), GeoPackage.decoderAnneau(l["geom"] as ByteArray),
                (l["h_max"] as? Number)?.toDouble(), if (xs != null && ys != null) Pair(xs, ys) else null,
            )
            h.takeIf { it.touche(emprise) }
        }
    }
}
