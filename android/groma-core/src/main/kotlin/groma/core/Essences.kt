package groma.core

import java.text.Normalizer

/** Species group of Marculus' map (nemeton's stand types); [INCONNUE] = no species entered yet. */
enum class GroupeEssence { CHENE, HETRE, FEUILLU, SAPIN, EPICEA, RESINEUX, INCONNUE }

/** One line of `essences_bdforet_v2.json`: code stored in `arbres.essence_arbre`, label, BD Foret v2 ESSENCE. */
data class Essence(val code: String, val libelle: String, val bdforet: String)

/**
 * The BD Foret v2 species list and its Marculus group (same rules as `groupe_essence` of
 * `qfield/symbologie.py`: the colour itself lives in the app's resources, not here).
 */
object Essences {
    val TOUTES: List<Essence> by lazy {
        val texte = Essences::class.java.getResourceAsStream("/groma/essences_bdforet_v2.json")?.bufferedReader()?.readText()
            ?: error("Ressource essences_bdforet_v2.json absente.")
        Regex("\\{\\s*\"code\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"libelle\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"bdforet\"\\s*:\\s*\"([^\"]*)\"")
            .findAll(texte).map { Essence(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()
    }

    private val FEUILLUS = setOf("Châtaignier", "Robinier", "Peuplier", "Feuillus")

    fun parCode(code: String?): Essence? = code?.let { c -> TOUTES.firstOrNull { it.code == c } }

    /** Group of a species code; [GroupeEssence.INCONNUE] for null, blank, an unknown code or `NC`. */
    fun groupe(code: String?): GroupeEssence {
        if (code.isNullOrBlank()) return GroupeEssence.INCONNUE
        val e = parCode(code) ?: return GroupeEssence.INCONNUE
        return when {
            e.bdforet.startsWith("Chênes") -> GroupeEssence.CHENE
            e.bdforet == "Hêtre" -> GroupeEssence.HETRE
            e.code == "sapin_pectine" -> GroupeEssence.SAPIN
            e.code == "epicea_commun" -> GroupeEssence.EPICEA
            e.bdforet == "NC" || e.bdforet.isEmpty() -> GroupeEssence.INCONNUE
            e.bdforet in FEUILLUS -> GroupeEssence.FEUILLU
            else -> GroupeEssence.RESINEUX
        }
    }

    private fun simplifier(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().replace('_', ' ').replace('-', ' ')

    /**
     * Species whose code or label contains every word of [texte] (accents and case ignored), list
     * order, at most [max]. Blank text: the start of the list.
     */
    fun rechercher(texte: String, max: Int = 30): List<Essence> {
        val mots = simplifier(texte).split(' ').filter { it.isNotEmpty() }
        return TOUTES.filter { e ->
            val cible = simplifier(e.code) + " " + simplifier(e.libelle)
            mots.all { it in cible }
        }.take(max)
    }

    /** Text shown in the species field: "Label (code)", or "code (hors liste)" for a code outside the list. */
    fun affichage(code: String): String = parCode(code)?.let { "${it.libelle} (${it.code})" } ?: "$code (hors liste)"

    /**
     * Code a species field stands for, null when it stands for none (the form then refuses).
     * [texte] is the field, [existante] the code already in `arbres` (possibly outside the list),
     * [choisie] the code last picked in the list. Blank = "" (erase); the untouched text of the
     * existing species (listed or not) = that code, so an out-of-list species never blocks the
     * saving of other fields; otherwise the picked code, or the only match of the search.
     */
    fun resoudre(texte: String, existante: String?, choisie: String?): String? = when {
        texte.isBlank() -> ""
        !existante.isNullOrBlank() && texte == affichage(existante) -> existante
        !choisie.isNullOrBlank() && parCode(choisie) != null && texte == affichage(choisie) -> choisie
        else -> rechercher(texte, 2).singleOrNull()?.code
    }
}
