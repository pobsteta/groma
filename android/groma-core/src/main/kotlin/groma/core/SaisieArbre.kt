package groma.core

/**
 * Attributes of a tree entered in Groma after the capture (mission 018): species (BD Foret v2 code),
 * tape diameter, status, quality. Same domains as `inventaire_video.schema.json`.
 */
object SaisieArbre {
    val CHAMPS = listOf("essence_arbre", "dbh_cm", "statut", "qualite")

    val STATUTS = listOf("vivant", "mort", "chablis", "coupe")
    val QUALITES = listOf("A", "B", "C", "D")
    const val DBH_MAX_CM = 1000.0

    /** Codes of `essences_bdforet_v2.json` (resource copied from the Python package). */
    val ESSENCES: Set<String> by lazy {
        val texte = SaisieArbre::class.java.getResourceAsStream("/groma/essences_bdforet_v2.json")?.bufferedReader()?.readText()
            ?: error("Ressource essences_bdforet_v2.json absente.")
        Regex("\"code\"\\s*:\\s*\"([^\"]+)\"").findAll(texte).map { it.groupValues[1] }.toSet()
    }

    private val NOMBRE = Regex("^[0-9]+([.,][0-9]+)?$")

    /** Diameter typed by the operator (comma or point), null when blank or not a number. */
    fun diametre(valeur: String?): Double? = valeur?.trim()?.takeIf { NOMBRE.matches(it) }?.replace(',', '.')?.toDoubleOrNull()

    /** Null when [valeur] is valid for [champ] (blank = empty = valid), else a French message. */
    fun controler(champ: String, valeur: String?): String? {
        val v = valeur?.trim().orEmpty()
        return when (champ) {
            "essence_arbre" -> if (v.isEmpty() || v in ESSENCES) null else "Essence inconnue : « $v » n'est pas un code de la BD Forêt v2."
            "dbh_cm" -> {
                val d = diametre(v)
                when {
                    v.isEmpty() -> null
                    d == null -> "Diamètre invalide : « $v » (un nombre en cm, virgule ou point)."
                    d > DBH_MAX_CM -> "Diamètre hors bornes : au plus ${DBH_MAX_CM.toInt()} cm."
                    else -> null
                }
            }
            "statut" -> if (v.isEmpty() || v in STATUTS) null else "Statut inconnu : « $v » (${STATUTS.joinToString(", ")})."
            "qualite" -> if (v.isEmpty() || v in QUALITES) null else "Qualité inconnue : « $v » (${QUALITES.joinToString(", ")})."
            else -> "Champ non saisissable dans Groma : « $champ »."
        }
    }
}
