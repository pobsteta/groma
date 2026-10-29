package groma.core

/**
 * The five columns of the project Kanban view, those of Marculus and Surculus (Pascal, 2026-10-29).
 * Declaration order is both display order and comparison order: "at least Planifiée" means
 * `ordinal >= PLANIFIEE.ordinal` (so Abandonnée, last, is above any minimum, as in Marculus).
 */
enum class EtatKanban(val libelle: String) {
    PROPOSEE("Proposée"),
    VALIDEE("Validée"),
    PLANIFIEE("Planifiée"),
    REALISEE("Réalisée"),
    ABANDONNEE("Abandonnée"),
    ;

    companion object {
        /** Status kept on the phone, read back from its name; absent or unknown: Proposée. */
        fun depuisNom(nom: String?): EtatKanban = entries.firstOrNull { it.name == nom } ?: PROPOSEE
    }
}

/**
 * Minimum status imposed by progress: every Base plot done and every plot sent → Réalisée;
 * at least one plot done → Planifiée; otherwise Proposée.
 */
fun statutMinimal(resume: ResumeProjet): EtatKanban = when {
    resume.termine && resume.nonEnvoyees == 0 -> EtatKanban.REALISEE
    resume.faites > 0 -> EtatKanban.PLANIFIEE
    else -> EtatKanban.PROPOSEE
}

/** Column shown: the more advanced of the kept status and the minimum. */
fun colonneAffichee(stocke: EtatKanban, minimal: EtatKanban): EtatKanban =
    if (stocke.ordinal < minimal.ordinal) minimal else stocke

/**
 * Drop on column [cible]: the new status to keep, or `null` when refused (column below the
 * minimum) or without effect (already the kept status). The card then stays where it was.
 */
fun deposer(stocke: EtatKanban, cible: EtatKanban, minimal: EtatKanban): EtatKanban? =
    if (cible != stocke && cible.ordinal >= minimal.ordinal) cible else null

/** Spreads the items over the five columns (all present, possibly empty), keeping the input order. */
fun <T> repartir(lignes: List<T>, colonne: (T) -> EtatKanban): Map<EtatKanban, List<T>> =
    EtatKanban.entries.associateWith { e -> lignes.filter { colonne(it) == e } }

/** What the Kanban settings read and write on the phone (SharedPreferences for real, memory in tests). */
interface MagasinKanban {
    fun lireTexte(cle: String): String?
    fun ecrireTexte(cle: String, valeur: String)
    fun lireBooleen(cle: String, parDefaut: Boolean): Boolean
    fun ecrireBooleen(cle: String, valeur: Boolean)
}

/**
 * Kanban settings, kept **on the phone only** (not in the GeoPackage, not on the server):
 * lost on reinstall, accepted.
 */
class ReglagesKanban(private val magasin: MagasinKanban) {
    fun statut(idProjet: String): EtatKanban = EtatKanban.depuisNom(magasin.lireTexte(PREFIXE_STATUT + idProjet))

    fun definirStatut(idProjet: String, statut: EtatKanban) = magasin.ecrireTexte(PREFIXE_STATUT + idProjet, statut.name)

    var vueColonnes: Boolean
        get() = magasin.lireBooleen(CLE_VUE, false)
        set(v) = magasin.ecrireBooleen(CLE_VUE, v)

    private companion object {
        const val PREFIXE_STATUT = "kanban_statut_"
        const val CLE_VUE = "kanban_vue_colonnes"
    }
}
