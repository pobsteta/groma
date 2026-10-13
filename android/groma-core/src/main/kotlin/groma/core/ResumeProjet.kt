package groma.core

import kotlin.math.PI

/**
 * What a project card shows beyond the `projet` table, computed from the
 * working copy on the phone (brief 2026-10-13, § 4). Nothing is stored.
 */
data class ResumeProjet(
    val nbBase: Int,
    val nbRempl: Int,
    /** Plots whose state is not `a_faire`. */
    val faites: Int,
    /** Base plots done: the progress bar (replacement plots are only done when needed). */
    val baseFaites: Int,
    /** Sent to the server or processed (`envoye`, `definitif`). */
    val envoyees: Int,
    /** Plots done but not sent yet (`provisoire`): they block removing the project. */
    val nonEnvoyees: Int,
    /** Σ π·r² over the plots done, with each plot's own radius, in hectares. */
    val surfaceInventorieeHa: Double,
    /** Smallest and largest plot radius (m), null without radius. */
    val rayonMinM: Double?,
    val rayonMaxM: Double?,
    /** Last RTK centre measured (ISO date), null before the first one. */
    val dernierReleve: String?,
) {
    /** Every Base plot done; a plan without Base plot is never finished. */
    val termine: Boolean get() = nbBase > 0 && baseFaites >= nbBase

    companion object {
        fun calculer(placettes: List<Map<String, Any?>>, centres: List<Map<String, Any?>> = emptyList()): ResumeProjet {
            fun etat(p: Map<String, Any?>) = p["etat_traitement"] as? String ?: "a_faire"
            fun rempl(p: Map<String, Any?>) = p["type"] == "Over"
            fun rayon(p: Map<String, Any?>) = (p["rayon_m"] as? Number)?.toDouble()
            val faites = placettes.filter { etat(it) != "a_faire" }
            val rayons = placettes.mapNotNull(::rayon)
            return ResumeProjet(
                nbBase = placettes.count { !rempl(it) },
                nbRempl = placettes.count(::rempl),
                faites = faites.size,
                baseFaites = faites.count { !rempl(it) },
                envoyees = placettes.count { etat(it) in setOf("envoye", "definitif") },
                nonEnvoyees = placettes.count { etat(it) == "provisoire" },
                surfaceInventorieeHa = faites.sumOf { p -> rayon(p)?.let { PI * it * it } ?: 0.0 } / 10_000.0,
                rayonMinM = rayons.minOrNull(),
                rayonMaxM = rayons.maxOrNull(),
                dernierReleve = centres.mapNotNull { it["date_mesure"] as? String }.maxOrNull(),
            )
        }

        fun lire(db: BaseSqlite): ResumeProjet = calculer(
            db.requete("SELECT type, etat_traitement, rayon_m FROM placettes"),
            db.requete("SELECT date_mesure FROM centres_mesures"),
        )

        /**
         * Field data in a working copy: measured centres, targets, detections,
         * trees, or a plot already processed. Replacing such a copy with a newer
         * preparation would lose them (rule 5): refused.
         */
        fun aDesDonneesTerrain(db: BaseSqlite): Boolean {
            val tables = db.requete("SELECT name FROM sqlite_master WHERE type = 'table'").map { it["name"] }.toSet()
            val saisies = listOf("centres_mesures", "cibles", "detections", "arbres").filter { it in tables }
            if (saisies.any { db.requete("SELECT 1 AS un FROM $it LIMIT 1").isNotEmpty() }) return true
            return db.requete("SELECT 1 AS un FROM placettes WHERE COALESCE(etat_traitement, 'a_faire') <> 'a_faire' LIMIT 1").isNotEmpty()
        }
    }
}
