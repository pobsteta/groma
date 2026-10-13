package groma.core

/**
 * Keys of a plot on the phone and on the server: (projet_id, plot_id), since
 * two sampling plans may both have a P001. Same pattern as the server
 * (server/src/groma_server/api.py): letters, digits, `_`, `.`, `-`, 64 at most.
 */
object CleProjet {
    private val MOTIF = Regex("^[A-Za-z0-9_.-]{1,64}$")

    fun valide(id: String): Boolean = MOTIF.matches(id) && id != "." && id != ".."

    /**
     * Project id: the `projet` table's when valid, otherwise the folder name
     * made safe (GeoPackages prepared before schema 1.1.0).
     */
    fun projetId(infos: InfosProjet?, nomDossier: String): String =
        infos?.projetId?.takeIf { valide(it) }
            ?: nomDossier.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(64).takeIf { valide(it) }
            ?: "projet"

    /** Relative path `<projet_id>/<plot_id><suffixe>` for packages, detection exports, capture folders. */
    fun chemin(projetId: String, plotId: String, suffixe: String = ""): String {
        require(valide(projetId)) { "projet_id invalide : $projetId" }
        require(valide(plotId)) { "plot_id invalide : $plotId" }
        return "$projetId/$plotId$suffixe"
    }

    /** WorkManager unique name of a plot upload: `/` cannot appear in either id. */
    fun travailEnvoi(projetId: String, plotId: String): String = "envoi/" + chemin(projetId, plotId)
}
