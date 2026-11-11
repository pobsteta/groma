package groma.core

/**
 * Copy rule of the QField plugin (`qfield/inventaire_video.qml`, function `aRecopier`, which
 * reproduces this one line for line: change both together). Never overwrites an entry made in QField.
 *
 * Not part of this pure rule, handled by the plugin (`sansDiametreSiPasDeSource`): a QField layer
 * without the `dbh_source` column (project generated before schema 1.4.0) gets neither `dbh_cm` nor
 * `dbh_source`, since its source would read as empty and a tape diameter typed in QField be overwritten.
 */
object RecopieQfield {
    val ATTRIBUTS = listOf("essence_arbre", "dbh_cm", "dbh_source", "statut", "qualite")

    private fun vide(v: Any?) = v == null || v.toString().isBlank()

    /** Only the attributes to write in QField (empty map: nothing to do). */
    fun aRecopier(groma: Map<String, Any?>, qfield: Map<String, Any?>): Map<String, Any?> {
        val res = LinkedHashMap<String, Any?>()
        for (k in listOf("essence_arbre", "qualite")) {
            if (!vide(groma[k]) && vide(qfield[k])) res[k] = groma[k]
        }
        val sq = qfield["statut"]
        if (!vide(groma["statut"]) && groma["statut"] != "vivant" && (vide(sq) || sq == "vivant")) res["statut"] = groma["statut"]
        val sourceQ = qfield["dbh_source"]
        if (groma["dbh_source"] == "mesure" && !vide(groma["dbh_cm"]) && (vide(sourceQ) || sourceQ == "estime")) {
            res["dbh_cm"] = groma["dbh_cm"]
            res["dbh_source"] = "mesure"
        }
        return res
    }
}
