package groma.core

/**
 * A tree of the plot as the plan (mission 019) shows it: Lambert-93 position from the `arbres`
 * geometry, [numero] = `tree_id` (else `num_provisoire`), diameter, entered attributes.
 */
data class ArbrePlacette(
    val uuid: String,
    val numero: String,
    val x: Double,
    val y: Double,
    val dbhCm: Double?,
    val dbhMesure: Boolean,
    val essence: String?,
    val statut: String,
    val qualite: String?,
    /** Estimate of the video (`detections.dbh_cm`), kept when a tape diameter replaces it in `arbres`. */
    val dbhEstimeCm: Double? = null,
    /** True for a tree forgotten by the video and added by hand (`methode = 'terrain'`, mission 023). */
    val ajoute: Boolean = false,
    /** Free note (`arbres.notes`), from Groma or QField; null when empty (mission 022). */
    val notes: String? = null,
    /** Photo paths of `arbres.photos`, relative to the project folder, in order (mission 022). */
    val photos: List<String> = emptyList(),
) {
    companion object {
        /** Pastille radius bounds (dp) and the diameters they correspond to (Marculus: 7.5 to 80 cm). */
        const val RAYON_MIN_DP = 6f
        const val RAYON_MAX_DP = 15f
        const val DBH_MIN_CM = 7.5
        const val DBH_MAX_CM = 80.0

        /** Radius (dp) of a pastille: linear in the diameter, bounded; a missing diameter counts as the minimum. */
        fun rayonDp(dbhCm: Double?): Float {
            val t = (((dbhCm ?: DBH_MIN_CM) - DBH_MIN_CM) / (DBH_MAX_CM - DBH_MIN_CM)).coerceIn(0.0, 1.0)
            return RAYON_MIN_DP + (RAYON_MAX_DP - RAYON_MIN_DP) * t.toFloat()
        }

        /** Order of the numbers: numeric when both are, numbers before text ("2" before "10"). */
        val ORDRE_NUMEROS: Comparator<String> = Comparator { a, b ->
            val na = a.toLongOrNull(); val nb = b.toLongOrNull()
            when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1
                nb != null -> 1
                else -> a.compareTo(b)
            }
        }

        /** Trees of [plotId] without the false positives, by number; rows with no uuid, number or position are left out. */
        fun lire(db: BaseSqlite, plotId: String): List<ArbrePlacette> =
            lirePlacette(db, plotId)

        private fun lirePlacette(db: BaseSqlite, plotId: String): List<ArbrePlacette> {
            val estimes = db.requete("SELECT uuid, dbh_cm FROM detections WHERE plot_id = ?", listOf(plotId))
                .associate { it["uuid"] as? String to (it["dbh_cm"] as? Number)?.toDouble() }
            return GeoPackage.lirePoints(db, "arbres", plotId).mapNotNull { r ->
                val uuid = r["uuid"] as? String ?: return@mapNotNull null
                if (r["etat_detection"] == "faux_positif") return@mapNotNull null
                val x = r["x"] as? Double ?: return@mapNotNull null
                val y = r["y"] as? Double ?: return@mapNotNull null
                val numero = (r["tree_id"] as? String)?.takeIf { it.isNotBlank() }
                    ?: (r["num_provisoire"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ArbrePlacette(
                    uuid, numero, x, y, (r["dbh_cm"] as? Number)?.toDouble(), r["dbh_source"] == "mesure",
                    (r["essence_arbre"] as? String)?.takeIf { it.isNotBlank() },
                    (r["statut"] as? String)?.takeIf { it.isNotBlank() } ?: "vivant",
                    (r["qualite"] as? String)?.takeIf { it.isNotBlank() },
                    estimes[uuid] ?: (r["dbh_cm"] as? Number)?.toDouble()?.takeIf { r["dbh_source"] != "mesure" },
                    ajoute = r["methode"] == "terrain",
                    notes = (r["notes"] as? String)?.takeIf { it.isNotBlank() },
                    photos = PhotosArbre.lister(r["photos"] as? String),
                )
            }.sortedWith(compareBy(ORDRE_NUMEROS) { it.numero })
        }

        /** The tree after [uuid] in [arbres] (already by number), null for the last one or an unknown uuid. */
        fun suivant(arbres: List<ArbrePlacette>, uuid: String): ArbrePlacette? {
            val i = arbres.indexOfFirst { it.uuid == uuid }
            return if (i < 0) null else arbres.getOrNull(i + 1)
        }
    }
}
