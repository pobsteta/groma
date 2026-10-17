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
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One project of the list: the working copy on the phone, and its source folder if still there. */
data class EntreeProjet(
    /** Folder name, on the phone and in the projects folder: the nemetonshiny project id. */
    val cle: String,
    val projet: Projet,
    val infos: InfosProjet?,
    val resume: ResumeProjet,
    /** The source folder holds a newer preparation than the working copy. */
    val majDisponible: Boolean,
    /** Distance (m) from the phone to the centre of the project's extent, when both are known. */
    val distanceM: Double? = null,
    /** The source folder holds the QField project (a `.qgz` in `qfield/`, from nemetonshiny). */
    val qfield: Boolean = false,
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
        // Dot folders: a download being installed (TelechargementProjet).
        .filter { it.isDirectory && it.name?.startsWith(".") == false && it.findFile(GPKG)?.isFile == true }
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
        val qfield = source?.findFile(QFIELD)?.listFiles().orEmpty().any { it.name?.endsWith(".qgz") == true }
        return EntreeProjet(dossier.name, projet, infos, resume, majDisponible = importe != null && modifie > importe, qfield = qfield)
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

    /** Projects on the phone: working copies and source folders. */
    fun cles(): Set<String> =
        copies.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty().map { it.name }.toSet() + sources().keys

    /**
     * Zip of the QField project for QField's import (brief envoi projet, § 4):
     * `<nom>.qgz` and its plugin `<nom>.qml` at the root, as QField expects
     * them side by side, with `inventaire_video.gpkg` and `rasters/` next to
     * them. The `.qgs` points to `./inventaire_video.gpkg`, and QGIS resolves
     * relative paths from the `.qgz` location, not inside the archive (only
     * `attachment:` paths are read there): without these copies the layers
     * would be missing. Built in the cache, shared through the FileProvider.
     */
    fun archiveQfield(e: EntreeProjet): File {
        val source = sources()[e.cle] ?: error("Le dossier ${e.cle} n'est plus dans le dossier des projets.")
        val (qgz, fichiers) = fichiersQfield(e)
        val base = qgz.name!!.removeSuffix(".qgz")
        val entrees = buildList {
            add(qgz.name!! to qgz)
            fichiers.firstOrNull { it.name == "$base.qml" }?.let { add(it.name!! to it) }
            source.findFile(GPKG)?.takeIf { it.isFile }?.let { add(GPKG to it) }
            source.findFile("rasters")?.listFiles().orEmpty().filter { it.isFile && it.name != null }.forEach { add("rasters/${it.name}" to it) }
        }
        val cache = File(ctx.cacheDir, QFIELD).apply { deleteRecursively(); mkdirs() }
        val zip = File(cache, "${e.cle}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { z ->
            z.setLevel(Deflater.BEST_SPEED)
            for ((chemin, f) in entrees) {
                z.putNextEntry(ZipEntry(chemin))
                ctx.contentResolver.openInputStream(f.uri)!!.use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return zip
    }

    /** Identifies the `.qgz` handed to QField: name, size and modification time. */
    fun empreinteQfield(e: EntreeProjet): String = fichiersQfield(e).first.let { "${it.name}:${it.length()}:${it.lastModified()}" }

    /**
     * What was last handed to QField for this project. QField unzips every
     * archive into a new folder of "Imported Projects" (`<cle>`, `<cle>_1`…):
     * sending again makes a separate copy, without what was entered in the first.
     */
    fun envoiQfield(cle: String): String? = prefs.getString(CLE_QFIELD + cle, null)

    fun memoriserEnvoiQfield(cle: String, empreinte: String) = prefs.edit().putString(CLE_QFIELD + cle, empreinte).apply()

    private fun fichiersQfield(e: EntreeProjet): Pair<DocumentFile, List<DocumentFile>> {
        val dossier = sources()[e.cle]?.findFile(QFIELD) ?: error("Pas de projet QField dans le dossier ${e.cle}.")
        val fichiers = dossier.listFiles().filter { f -> f.isFile && f.name?.let { it.endsWith(".qgz") || it.endsWith(".qml") } == true }
        val qgz = fichiers.singleOrNull { it.name!!.endsWith(".qgz") } ?: error("Le dossier ${e.cle}/$QFIELD doit contenir un seul .qgz.")
        return qgz to fichiers
    }

    fun projet(cle: String): Projet? = File(copies, cle).takeIf { File(it, GPKG).isFile }?.let(::Projet)

    private fun importerDepuis(doc: DocumentFile, cible: File) {
        try {
            copier(doc, cible, racine = true)
            File(cible, SOURCE).writeText(JSONObject().put("gpkg_modifie", doc.findFile(GPKG)?.lastModified() ?: 0L).toString())
        } catch (e: Exception) {
            cible.deleteRecursively(); throw e
        }
    }

    private fun lireSource(dossier: File): Long? =
        File(dossier, SOURCE).takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()).getLong("gpkg_modifie") }.getOrNull() }

    private fun copier(src: DocumentFile, dst: File, racine: Boolean = false) {
        dst.mkdirs()
        for (f in src.listFiles()) {
            val nom = f.name ?: continue
            // qfield/: the .qgz for QField already holds the GeoPackage and rasters, not needed by Groma.
            if (racine && nom == QFIELD) continue
            if (f.isDirectory) copier(f, File(dst, nom))
            else ctx.contentResolver.openInputStream(f.uri)!!.use { i -> File(dst, nom).outputStream().use { i.copyTo(it) } }
        }
    }

    companion object {
        const val GPKG = "inventaire_video.gpkg"
        const val QFIELD = "qfield"
        private const val SOURCE = ".source.json" // last-modified time of the source GeoPackage at import
        private const val CLE_RACINE = "racine_projets"
        private const val CLE_DERNIER = "dernier_projet"
        private const val CLE_QFIELD = "qfield_envoye_"
    }
}
