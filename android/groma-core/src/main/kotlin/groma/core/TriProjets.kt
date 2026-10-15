package groma.core

/**
 * Order of the project list (brief 2026-10-13 liste des projets, § 5): the
 * last opened project first, then by distance from the phone to the centre
 * of the project's extent when a position is known, else by name. Projects
 * without an extent (prepared before schema 1.1.0) come after, by name.
 */
object TriProjets {
    private val NOMBRE = Regex("""-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?""")

    /** Centre of the bounding box of a WKT geometry in EPSG:4326 (lon lat pairs), null if unreadable. */
    fun centreEmprise(wkt: String?): Position? {
        if (wkt.isNullOrBlank()) return null
        val corps = wkt.substringAfter('(', "").ifEmpty { return null }
        val nombres = NOMBRE.findAll(corps).map { it.value.toDouble() }.toList()
        if (nombres.size < 2 || nombres.size % 2 != 0) return null
        val lons = nombres.filterIndexed { i, _ -> i % 2 == 0 }
        val lats = nombres.filterIndexed { i, _ -> i % 2 == 1 }
        if (lons.any { it !in -180.0..180.0 } || lats.any { it !in -90.0..90.0 }) return null
        return Position((lats.min() + lats.max()) / 2, (lons.min() + lons.max()) / 2)
    }

    /** Distance (m) from [position] to the centre of [emprise], null when either is unknown. */
    fun distanceM(position: Position?, emprise: String?): Double? {
        val c = centreEmprise(emprise) ?: return null
        return position?.let { Geodesie.distanceM(it, c) }
    }

    fun <T> trier(
        liste: List<T>, dernier: String?, position: Position?,
        cle: (T) -> String, emprise: (T) -> String?, parNom: Comparator<T>,
    ): List<T> {
        val distances = liste.associateWith { distanceM(position, emprise(it)) }
        return liste.sortedWith(
            compareBy<T> { cle(it) != dernier }
                .thenBy { distances[it] == null }
                .thenBy { distances[it] ?: 0.0 }
                .then(parNom),
        )
    }
}
