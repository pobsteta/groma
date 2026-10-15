package groma.app

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import groma.core.GeoPackage
import groma.core.InfosProjet
import groma.core.ResumeProjet
import org.json.JSONObject
import java.io.File

/** One project of the list: the working copy on the phone, and its source folder if still there. */
data class EntreeProjet(
    /** Folder name, on the phone and in the projects folder: the nemetonshiny project id. */
    val cle: String,
    val projet: Projet,
    val infos: InfosProjet?,
    val resume: ResumeProjet,
    /** The source folder holds a newer preparation than the working copy. */
    val majDisponible: Boolean,
) {
    val titre: String get() = infos?.projetNom ?: cle
}

/**
 * Projects on the phone (brief 2026-10-13, § 2). Every sub-folder of the
 * projects folder (picked once, `Téléchargements/groma/` by default) holding
 * `inventaire_video.gpkg` is a project. SQLite needs a real file path, which
 * a document tree does not give: each project is copied once into the app
 * storage (working copy, where field work is saved) and only replaced on
 * request, never over field data.
 */
class DepotProjets(private val ctx: Context) {
    private val copies = File(ctx.getExternalFilesDir(null), "projets").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("groma", Context.MODE_PRIVATE)

    var racine: Uri?
        get() = prefs.getString(CLE_RACINE, null)?.let(Uri::parse)
        private set(v) { prefs.edit().putString(CLE_RACINE, v?.toString()).apply() }

    var dernierOuvert: String?
        get() = prefs.getString(CLE_DERNIER, null)
        set(v) { prefs.edit().putString(CLE_DERNIER, v).apply() }

    /** Keeps read and write access to the picked folder across reboots. */
    fun choisirRacine(uri: Uri) {
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        racine = uri
    }

    private fun dossierRacine(): DocumentFile? = racine?.let { DocumentFile.fromTreeUri(ctx, it) }?.takeIf { it.canRead() }

    private fun sources(): Map<String, DocumentFile> = dossierRacine()?.listFiles().orEmpty()
        .filter { it.isDirectory && it.name != null && it.findFile(GPKG)?.isFile == true }
        .associateBy { it.name!! }

    /**
     * Copies the new folders of the projects folder, then lists every working
     * copy. Unreadable GeoPackages are reported in [erreurs], not thrown.
     */
    fun actualiser(erreurs: MutableList<String> = mutableListOf()): List<EntreeProjet> {
        val src = sources()
        for ((cle, doc) in src) {
            if (!File(copies, cle).exists()) {
                try { importerDepuis(doc, File(copies, cle)) } catch (e: Exception) { erreurs += "$cle : ${e.message}" }
            }
        }
        return copies.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && File(f, GPKG).isFile }.orEmpty()
            .sortedBy { it.name }
            .mapNotNull { dossier ->
                try {
                    entree(dossier, src[dossier.name])
                } catch (e: Exception) {
                    erreurs += "${dossier.name} : ${e.message}"; null
                }
            }
    }

    private fun entree(dossier: File, source: DocumentFile?): EntreeProjet {
        val projet = Projet(dossier)
        val (infos, resume) = SQLiteDatabase.openDatabase(projet.gpkg.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val s = SqliteAndroid(db)
            GeoPackage.lireProjet(s) to ResumeProjet.lire(s)
        }
        val importe = lireSource(dossier)
        val modifie = source?.findFile(GPKG)?.lastModified() ?: 0L
        return EntreeProjet(dossier.name, projet, infos, resume, majDisponible = importe != null && modifie > importe)
    }

    /** Picked folder outside the projects folder ("Importer un dossier…"): copied the same way. */
    fun importer(arbre: Uri): EntreeProjet {
        val doc = DocumentFile.fromTreeUri(ctx, arbre) ?: error("Dossier illisible.")
        require(doc.findFile(GPKG)?.isFile == true) { "Le dossier ne contient pas $GPKG." }
        val cle = doc.name ?: "projet"
        val cible = File(copies, cle)
        require(!cible.exists()) { "Le projet $cle est déjà sur le téléphone." }
        importerDepuis(doc, cible)
        return entree(cible, null)
    }

    sealed interface Resultat {
        data object Fait : Resultat
        data class Refuse(val raison: String) : Resultat
    }

    /**
     * Replaces the working copy with the newer source folder, only when the
     * copy holds no field data (rule 5: a field entry is never lost).
     */
    fun mettreAJour(e: EntreeProjet): Resultat {
        val source = sources()[e.cle] ?: return Resultat.Refuse("Le dossier ${e.cle} n'est plus dans le dossier des projets.")
        val terrain = SQLiteDatabase.openDatabase(e.projet.gpkg.path, null, SQLiteDatabase.OPEN_READONLY).use {
            ResumeProjet.aDesDonneesTerrain(SqliteAndroid(it))
        }
        if (terrain) return Resultat.Refuse(
            "Ce projet contient déjà des données de terrain : il ne peut pas être remplacé. " +
                "Envoie d'abord les placettes faites, puis prépare un nouveau projet.",
        )
        val tmp = File(copies, ".maj_${e.cle}").apply { deleteRecursively() }
        importerDepuis(source, tmp)
        val ancien = File(copies, e.cle)
        // Downloaded tiles do not depend on the plan: kept across the update.
        val tuiles = File(ancien, "tuiles")
        if (tuiles.isDirectory) tuiles.renameTo(File(tmp, "tuiles"))
        ancien.deleteRecursively()
        check(tmp.renameTo(ancien)) { "Remplacement impossible." }
        return Resultat.Fait
    }

    /**
     * Removes the project from the phone: working copy and source folder (else
     * the next refresh would copy it again). Refused while plots done are not sent.
     */
    fun retirer(e: EntreeProjet): Resultat {
        if (e.resume.nonEnvoyees > 0) return Resultat.Refuse(
            "${e.resume.nonEnvoyees} placette(s) faite(s) pas encore envoyée(s) : le projet reste sur le téléphone.",
        )
        sources()[e.cle]?.let { if (!it.delete()) return Resultat.Refuse("Le dossier source n'a pas pu être supprimé.") }
        TelechargementTuiles.annuler(ctx, File(copies, e.cle))
        File(copies, e.cle).deleteRecursively()
        if (dernierOuvert == e.cle) dernierOuvert = null
        return Resultat.Fait
    }

    fun projet(cle: String): Projet? = File(copies, cle).takeIf { File(it, GPKG).isFile }?.let(::Projet)

    private fun importerDepuis(doc: DocumentFile, cible: File) {
        try {
            copier(doc, cible)
            File(cible, SOURCE).writeText(JSONObject().put("gpkg_modifie", doc.findFile(GPKG)?.lastModified() ?: 0L).toString())
        } catch (e: Exception) {
            cible.deleteRecursively(); throw e
        }
    }

    private fun lireSource(dossier: File): Long? =
        File(dossier, SOURCE).takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()).getLong("gpkg_modifie") }.getOrNull() }

    private fun copier(src: DocumentFile, dst: File) {
        dst.mkdirs()
        for (f in src.listFiles()) {
            val nom = f.name ?: continue
            if (f.isDirectory) copier(f, File(dst, nom))
            else ctx.contentResolver.openInputStream(f.uri)!!.use { i -> File(dst, nom).outputStream().use { i.copyTo(it) } }
        }
    }

    companion object {
        const val GPKG = "inventaire_video.gpkg"
        private const val SOURCE = ".source.json" // last-modified time of the source GeoPackage at import
        private const val CLE_RACINE = "racine_projets"
        private const val CLE_DERNIER = "dernier_projet"
    }
}
