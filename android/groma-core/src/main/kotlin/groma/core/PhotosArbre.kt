package groma.core

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Notes and photos of a tree (mission 022). Convention of `arbres.photos`, the one of QField's photo
 * widget (path relative to the project folder, `DCIM/…` by default) extended to several photos:
 * one or several relative paths separated by `;` (fixed by the tests of mission 020). A single photo
 * is exactly what QField writes; QField's widget does not display a list (see docs/terrain.md).
 */
object PhotosArbre {
    const val SEPARATEUR = ";"

    /** Longest note accepted (characters): about a page of field remarks, far below any SQLite limit. */
    const val NOTE_MAX = 2000

    /** Longest side (pixels) of a photo taken by Groma. Provisional value (mission 022), to be judged in the field. */
    const val COTE_MAX_PX = 2048

    const val DOSSIER = "DCIM"

    private val HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** A photo written by Groma: `DCIM/<plot_id>_<tree_id>_<AAAAMMJJ-HHMMSS>.jpg`. */
    private val NOM_GROMA = Regex("^$DOSSIER/[^/\\\\;]+_[0-9]{8}-[0-9]{6}\\.jpg$")

    /** The paths of an `arbres.photos` value, in order, blanks and duplicates left out. */
    fun lister(valeur: String?): List<String> =
        valeur?.split(SEPARATEUR)?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()

    /** The `arbres.photos` value of [chemins] (null when empty: the column stays NULL). */
    fun joindre(chemins: List<String>): String? =
        chemins.map { it.trim() }.filter { it.isNotEmpty() }.distinct().takeIf { it.isNotEmpty() }?.joinToString(SEPARATEUR)

    /** Characters of an identifier kept in a file name; others become `-`. */
    private fun assainir(s: String) = s.trim().replace(Regex("[^A-Za-z0-9.-]"), "-").trim('.', '-').ifEmpty { "x" }

    /** Project-relative path `DCIM/<plot_id>_<tree_id>_<AAAAMMJJ-HHMMSS>.jpg` (identifiers cleaned of anything but letters, digits, `.` and `-`). */
    fun chemin(plotId: String, treeId: String, instant: LocalDateTime): String =
        "$DOSSIER/${assainir(plotId)}_${assainir(treeId)}_${HORODATAGE.format(instant)}.jpg"

    /** True for a path written by Groma (the only ones Groma may delete); QField's photos never match. */
    fun estDeGroma(chemin: String): Boolean = NOM_GROMA.matches(chemin.trim())

    /** Null when [valeur] is a valid note (blank = empty), else a French message. */
    fun controlerNote(valeur: String?): String? =
        if ((valeur?.length ?: 0) > NOTE_MAX) "Note trop longue : au plus $NOTE_MAX caractères." else null

    /** Null when every path of [valeur] is relative, inside the project and without `..`; else a French message. */
    fun controlerPhotos(valeur: String?): String? {
        for (c in lister(valeur)) {
            val segments = c.split('/', '\\')
            if (c.startsWith("/") || c.startsWith("\\") || Regex("^[A-Za-z]:").containsMatchIn(c) || ".." in segments) {
                return "Chemin de photo invalide : « $c » (relatif au dossier du projet, sans « .. »)."
            }
        }
        return null
    }

    /** (width, height) fitting [max] on the longest side, ratio kept; unchanged when already small enough. */
    fun tailleReduite(largeur: Int, hauteur: Int, max: Int = COTE_MAX_PX): Pair<Int, Int> {
        require(largeur > 0 && hauteur > 0 && max > 0) { "Dimensions invalides." }
        val grand = maxOf(largeur, hauteur)
        if (grand <= max) return largeur to hauteur
        val k = max.toDouble() / grand
        return maxOf(1, Math.round(largeur * k).toInt()) to maxOf(1, Math.round(hauteur * k).toInt())
    }

    /** Value of `photos` once [chemin] is added (kept after the existing ones). */
    fun ajouter(valeur: String?, chemin: String): String? = joindre(lister(valeur) + chemin)

    /** Value of `photos` once [chemin] is removed from the list (the others are kept). */
    fun retirer(valeur: String?, chemin: String): String? = joindre(lister(valeur).filter { it != chemin.trim() })

    /**
     * Deletes the file of [chemin] under [dossierProjet] when, and only when, Groma wrote it
     * ([estDeGroma]), it stays inside the project folder and no row of `arbres` or `placettes`
     * still cites it. Returns true when a file was deleted. A QField photo is never touched.
     */
    fun supprimerFichier(db: BaseSqlite, dossierProjet: File, chemin: String): Boolean {
        val c = chemin.trim()
        if (!estDeGroma(c) || controlerPhotos(c) != null) return false
        val cite = (db.requete("SELECT photos FROM arbres") + db.requete("SELECT photos FROM placettes"))
            .any { c in lister(it["photos"] as? String) }
        if (cite) return false
        val racine = dossierProjet.canonicalFile
        val f = File(racine, c).canonicalFile
        if (!f.path.startsWith(racine.path + File.separator) || !f.isFile) return false
        return f.delete()
    }
}
