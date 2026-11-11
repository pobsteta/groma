package groma.core

/**
 * State of the photo list of an open tree form (mission 022), kept apart from the UI so that it can
 * be saved across an activity re-creation (see [enTextes]/[depuis]) and tested on the JVM.
 * [originales] = `arbres.photos` when the form opened, [courantes] = what the form shows now,
 * [nouvelles] = photos taken in this form (files already written, not yet saved in the database).
 */
data class EtatPhotos(
    val originales: List<String>,
    val courantes: List<String> = originales,
    val nouvelles: List<String> = emptyList(),
) {
    /** A photo was just taken and stored at [chemin]. */
    fun ajoutee(chemin: String) = copy(courantes = (courantes + chemin).distinct(), nouvelles = (nouvelles + chemin).distinct())

    /** The state once [chemin] is removed from the form; the second value is the file to delete NOW (a photo taken here and never saved), else none. */
    fun retiree(chemin: String): Pair<EtatPhotos, List<String>> =
        copy(courantes = courantes.filter { it != chemin }, nouvelles = nouvelles.filter { it != chemin }) to
            (if (chemin in nouvelles) listOf(chemin) else emptyList())

    val modifiee: Boolean get() = courantes != originales

    /** Paths that were listed when the form opened and are no longer: to pass to [PhotosArbre.supprimerFichier] after the save. */
    val retireesALEnregistrement: List<String> get() = originales.filter { it !in courantes }

    /** Files to delete when the form is really closed without saving (photos taken and kept in the list). */
    val abandonnees: List<String> get() = nouvelles.filter { it in courantes }

    /** After a successful save: what is shown becomes the stored state, nothing is left to abandon. */
    fun enregistree() = EtatPhotos(courantes)

    /** Three plain strings (originales, courantes, nouvelles) for a saved-instance-state bundle. */
    fun enTextes(): List<String> = listOf(originales, courantes, nouvelles).map { PhotosArbre.joindre(it).orEmpty() }

    companion object {
        fun depuis(textes: List<String>): EtatPhotos =
            EtatPhotos(PhotosArbre.lister(textes[0]), PhotosArbre.lister(textes[1]), PhotosArbre.lister(textes[2]))
    }
}
