package groma.core

import kotlin.math.hypot

/** Plot on the map: planned point in WGS 84 (brief 2026-10-14 carte RTK, § 3). */
data class PlacetteCarte(
    val plotId: String, val type: String?, val etat: String, val lon: Double, val lat: Double,
    val x: Double, val y: Double, val rayonM: Double?, val ordre: Int?,
) {
    /** Replacement plot (Over): drawn smaller, outside the route. */
    val remplacement: Boolean get() = type == "Over"
}

/** RTK centre measured for a plot, with its distance to the planned point (m). */
data class CentreCarte(val plotId: String, val lon: Double, val lat: Double, val ecartM: Double?)

/** Cadastral parcel in WGS 84: polygons → rings (exterior first) → (lon, lat). */
data class ParcelleCarte(
    val idu: String, val section: String?, val numero: String?,
    val polygones: List<List<List<Pair<Double, Double>>>>,
) {
    /** Label: "A 36", else the cadastral id. */
    val etiquette: String get() = listOfNotNull(section, numero).joinToString(" ").ifBlank { idu }

    /** Label position: centre of the bounding box of the largest exterior ring. */
    val centreEtiquette: Pair<Double, Double>
        get() {
            val anneau = polygones.maxBy { p -> p.first().let { r -> (r.maxOf { it.first } - r.minOf { it.first }) * (r.maxOf { it.second } - r.minOf { it.second }) } }.first()
            return Pair((anneau.minOf { it.first } + anneau.maxOf { it.first }) / 2, (anneau.minOf { it.second } + anneau.maxOf { it.second }) / 2)
        }
}

/** Bounding box in WGS 84 degrees. */
data class Emprise(val ouest: Double, val sud: Double, val est: Double, val nord: Double)

/** Everything the map shows of one project, read once from the working copy. */
data class DonneesCarte(
    val placettes: List<PlacetteCarte>,
    val centres: List<CentreCarte>,
    val parcelles: List<ParcelleCarte>,
) {
    /** Visiting route: Base plots in `visit_order` (nemeton's TSP tour); Over plots are not on it. */
    val parcours: List<PlacetteCarte> = placettes.filter { !it.remplacement && it.ordre != null }.sortedBy { it.ordre }

    /** Extent of the plots and parcels, null when there is nothing to show. */
    val emprise: Emprise?
        get() {
            val pts = placettes.map { it.lon to it.lat } + parcelles.flatMap { p -> p.polygones.flatMap { it.first() } }
            if (pts.isEmpty()) return null
            return Emprise(pts.minOf { it.first }, pts.minOf { it.second }, pts.maxOf { it.first }, pts.maxOf { it.second })
        }

    companion object {
        /** Reads plots, measured centres and parcels; absent layers (older GeoPackages) are empty. */
        fun lire(db: BaseSqlite): DonneesCarte {
            val prevus = HashMap<String, Pair<Double, Double>>()
            val placettes = GeoPackage.lirePoints(db, "placettes").mapNotNull { l ->
                val x = l["x"] as? Double ?: return@mapNotNull null
                val y = l["y"] as? Double ?: return@mapNotNull null
                val id = l["plot_id"]?.toString() ?: return@mapNotNull null
                prevus[id] = x to y
                val (lon, lat) = Projections.versWgs84(x, y)
                PlacetteCarte(
                    id, l["type"] as? String, l["etat_traitement"] as? String ?: "a_faire", lon, lat, x, y,
                    (l["rayon_m"] as? Number)?.toDouble(), (l["visit_order"] as? Number)?.toInt(),
                )
            }
            val centres = if (!table(db, "centres_mesures")) emptyList() else GeoPackage.lirePoints(db, "centres_mesures").mapNotNull { l ->
                val x = l["x"] as? Double ?: return@mapNotNull null
                val y = l["y"] as? Double ?: return@mapNotNull null
                val id = l["plot_id"]?.toString() ?: return@mapNotNull null
                val (lon, lat) = Projections.versWgs84(x, y)
                CentreCarte(id, lon, lat, prevus[id]?.let { (px, py) -> hypot(x - px, y - py) })
            }
            val parcelles = GeoPackage.lireParcelles(db).map { p ->
                ParcelleCarte(p.idu, p.section, p.numero, p.polygones.map { anneaux ->
                    anneaux.map { anneau -> anneau.map { (x, y) -> Projections.versWgs84(x, y) } }
                })
            }
            return DonneesCarte(placettes, centres, parcelles)
        }

        private fun table(db: BaseSqlite, nom: String) =
            db.requete("SELECT 1 AS un FROM sqlite_master WHERE type = 'table' AND name = ?", listOf(nom)).isNotEmpty()
    }
}
