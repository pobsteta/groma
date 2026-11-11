package groma.core

/**
 * Copy rule of the QField plugin (`qfield/inventaire_video.qml`, function `aRecopier`, which
 * reproduces this one line for line: change both together). Never overwrites an entry made in QField.
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
