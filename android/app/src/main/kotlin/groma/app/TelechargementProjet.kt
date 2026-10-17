package groma.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import groma.core.Empreinte
import groma.core.ExtractionQgz
import groma.core.FichierServeur
import groma.core.ManifesteProjet
import groma.core.ProjetsServeur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Projects published on the Groma server by nemetonshiny (`GET /projets`, brief envoi projet). */
object ProjetsDuServeur {
    fun lister(serveur: String, jeton: String?): List<ManifesteProjet> {
        val c = URL("${serveur.trimEnd('/')}/projets").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 30_000
            jeton?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            when (c.responseCode) {
                200 -> {}
                401, 403 -> throw IOException("Jeton refusé par le serveur.")
                404 -> throw IOException("Ce serveur ne publie pas de projets (version trop ancienne ?).")
                else -> throw IOException("HTTP ${c.responseCode}")
            }
            val a = JSONArray(c.inputStream.bufferedReader().readText())
            return (0 until a.length()).mapNotNull { runCatching { manifeste(a.getJSONObject(it)) }.getOrNull() }
                .filter(ProjetsServeur::valide)
        } finally {
            c.disconnect()
        }
    }

    fun manifeste(o: JSONObject): ManifesteProjet {
        val f = o.getJSONArray("fichiers")
        return ManifesteProjet(
            projetId = o.getString("projet_id"),
            projetNom = o.optString("projet_nom").ifBlank { o.getString("projet_id") },
            version = o.getString("version"),
            publieLe = o.optString("publie_le").ifBlank { null },
            fichiers = (0 until f.length()).map { i ->
                f.getJSONObject(i).let { FichierServeur(it.getString("nom"), it.getString("sha256"), it.getLong("taille")) }
            },
        )
    }

    fun json(m: ManifesteProjet): String = JSONObject()
        .put("projet_id", m.projetId).put("projet_nom", m.projetNom).put("version", m.version).put("publie_le", m.publieLe)
        .put("fichiers", JSONArray(m.fichiers.map { JSONObject().put("nom", it.nom).put("sha256", it.sha256).put("taille", it.taille) }))
        .toString()

    /** Version installed from the server, null if the project came by cable or was never downloaded. */
    fun versionInstallee(ctx: Context, projetId: String): String? =
        Preferences.prefs(ctx).getString(CLE_VERSION + projetId, null)

    internal fun memoriserVersion(ctx: Context, projetId: String, version: String) =
        Preferences.prefs(ctx).edit().putString(CLE_VERSION + projetId, version).apply()

    private const val CLE_VERSION = "version_serveur_"
}

/**
 * Downloads the current version of a published project, then installs it in
 * the projects folder like a cable drop (brief envoi projet, § 4):
 * `<projet_id>/inventaire_video.gpkg` and `rasters/` extracted from the
 * `.qgz`, the `.qgz` and the plugin kept in `<projet_id>/qfield/`. The working
 * copy is left alone: the usual "Mettre à jour" (refused over field data)
 * takes over.
 *
 * Each file resumes with `Range` from what is already on disk, and is checked
 * against the manifest's SHA-256 before anything is installed.
 */
class TelechargementProjet(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            travailler()
        } finally {
            applicationContext.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
        }
    }

    private suspend fun travailler(): Result {
        val m = ProjetsDuServeur.manifeste(JSONObject(inputData.getString(CLE_MANIFESTE)!!))
        val serveur = inputData.getString(CLE_SERVEUR)!!.trimEnd('/')
        val jeton = inputData.getString(CLE_JETON)
        val racine = inputData.getString(CLE_RACINE)?.let(Uri::parse)
            ?: return echec("Dossier des projets non choisi.")
        if (!ProjetsServeur.valide(m)) return echec("Manifeste invalide.")
        val dossier = File(applicationContext.getExternalFilesDir(null), "telechargements/${m.projetId}/${m.version}").apply { mkdirs() }
        enAvantPlan(m, 0)
        try {
            var deja = 0L
            for (f in m.fichiers) {
                telecharger("$serveur/projets/${m.projetId}/qfield/${m.version}/${f.nom}", jeton, f, dossier, m, deja)
                deja += f.taille
            }
            installer(m, dossier, racine)
        } catch (e: VersionRemplacee) {
            return echec("Une version plus récente a été publiée entre-temps : actualise la liste.")
        } catch (e: FichierCorrompu) {
            return if (runAttemptCount < 2) Result.retry() else echec(e.message!!)
        } catch (e: IOException) {
            return Result.retry()
        } catch (e: Exception) {
            return echec(e.message ?: e.toString())
        }
        ProjetsDuServeur.memoriserVersion(applicationContext, m.projetId, m.version)
        File(applicationContext.getExternalFilesDir(null), "telechargements/${m.projetId}").deleteRecursively()
        return Result.success()
    }

    private fun echec(message: String) = Result.failure(workDataOf(CLE_ERREUR to message))

    private class VersionRemplacee : Exception()
    private class FichierCorrompu(message: String) : Exception(message)

    private suspend fun telecharger(url: String, jeton: String?, f: FichierServeur, dossier: File, m: ManifesteProjet, deja: Long) {
        val fini = File(dossier, f.nom)
        if (fini.isFile && fini.length() == f.taille) return // checked when it was completed
        val part = File(dossier, f.nom + ".part")
        if (part.length() > f.taille) part.delete()
        if (part.length() < f.taille) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000; c.readTimeout = 60_000
                jeton?.let { c.setRequestProperty("Authorization", "Bearer $it") }
                if (part.length() > 0) c.setRequestProperty("Range", "bytes=${part.length()}-")
                val ajout = when (c.responseCode) {
                    206 -> true
                    200 -> false // Range ignored: start again
                    404 -> throw VersionRemplacee()
                    else -> throw IOException("HTTP ${c.responseCode} sur $url")
                }
                FileOutputStream(part, ajout).use { out ->
                    c.inputStream.use { entree ->
                        val tampon = ByteArray(1 shl 16)
                        var recu = part.length()
                        var signale = 0L
                        while (true) {
                            if (isStopped) throw IOException("Arrêté.")
                            val n = entree.read(tampon)
                            if (n < 0) break
                            out.write(tampon, 0, n)
                            recu += n
                            if (recu - signale >= 1 shl 20) {
                                signale = recu
                                progression(m, deja + recu)
                            }
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
        }
        if (part.length() != f.taille) throw IOException("${f.nom} : téléchargement incomplet.")
        if (part.inputStream().use(Empreinte::sha256) != f.sha256) {
            part.delete()
            throw FichierCorrompu("${f.nom} : somme de contrôle SHA-256 incorrecte.")
        }
        check(part.renameTo(fini)) { "Écriture impossible : $fini" }
    }

    /**
     * Writes the project next to the old one (`.<projet_id>.nouveau`, ignored
     * by the project list), then swaps: the old source folder only holds what
     * nemetonshiny prepared, field work lives in the working copy.
     */
    private fun installer(m: ManifesteProjet, dossier: File, racine: Uri) {
        val ctx = applicationContext
        val arbre = DocumentFile.fromTreeUri(ctx, racine)?.takeIf { it.canWrite() }
            ?: throw IllegalStateException("Dossier des projets inaccessible : choisis-le à nouveau.")
        val nomTmp = ".${m.projetId}.nouveau"
        arbre.findFile(nomTmp)?.delete()
        val tmp = arbre.createDirectory(nomTmp) ?: error("Création de $nomTmp impossible.")
        try {
            val qgz = m.qgz!!
            var rasters: DocumentFile? = null
            File(dossier, qgz.nom).inputStream().use { entree ->
                ExtractionQgz.extraire(entree) { chemin, contenu ->
                    val parent = if (chemin.startsWith("rasters/")) {
                        rasters ?: (tmp.createDirectory("rasters") ?: error("Création de rasters impossible.")).also { rasters = it }
                    } else tmp
                    ecrire(parent, chemin.substringAfterLast('/'), contenu)
                }
            }
            val qfield = tmp.createDirectory("qfield") ?: error("Création de qfield impossible.")
            for (f in m.fichiers) File(dossier, f.nom).inputStream().use { ecrire(qfield, f.nom, it) }
            arbre.findFile(m.projetId)?.let { ancien -> check(ancien.delete()) { "L'ancien dossier ${m.projetId} n'a pas pu être remplacé." } }
            check(tmp.renameTo(m.projetId)) { "Renommage en ${m.projetId} impossible." }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    private fun ecrire(dossier: DocumentFile, nom: String, contenu: java.io.InputStream) {
        val doc = dossier.createFile("application/octet-stream", nom) ?: error("Création de $nom impossible.")
        // Some providers append an extension or a suffix: the name must stay exact.
        check(doc.name == nom) { "Nom modifié par Android : ${doc.name} au lieu de $nom." }
        applicationContext.contentResolver.openOutputStream(doc.uri, "w")!!.use { contenu.copyTo(it) }
    }

    // Notification ------------------------------------------------------------

    private suspend fun enAvantPlan(m: ManifesteProjet, recu: Long) {
        // Refused when the app is in the background (Android 12+): the plain notification still shows.
        try { setForeground(infos(m, recu)) } catch (e: Exception) { notifier(m, recu) }
    }

    private suspend fun progression(m: ManifesteProjet, recu: Long) {
        setProgress(workDataOf(CLE_RECU to recu, CLE_TOTAL to m.tailleTotale))
        notifier(m, recu)
    }

    private fun notifier(m: ManifesteProjet, recu: Long) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm.areNotificationsEnabled()) nm.notify(id.hashCode(), notification(m, recu))
    }

    private fun infos(m: ManifesteProjet, recu: Long): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), notification(m, recu), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id.hashCode(), notification(m, recu))

    private fun notification(m: ManifesteProjet, recu: Long): android.app.Notification {
        val ctx = applicationContext
        ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CANAL, ctx.getString(R.string.serveur_canal), NotificationManager.IMPORTANCE_LOW))
        val total = m.tailleTotale.coerceAtLeast(1)
        return NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(ctx.getString(R.string.serveur_notif_titre, m.projetNom))
            .setProgress(1000, (1000 * recu / total).toInt(), recu == 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val CANAL = "telechargement_projet"
        const val CLE_MANIFESTE = "manifeste"; const val CLE_SERVEUR = "serveur"; const val CLE_JETON = "jeton"; const val CLE_RACINE = "racine"
        const val CLE_RECU = "recu"; const val CLE_TOTAL = "total"; const val CLE_ERREUR = "erreur"

        fun nomTravail(projetId: String) = "projet_serveur_$projetId"

        fun planifier(ctx: Context, m: ManifesteProjet, racine: Uri) {
            val p = Preferences.prefs(ctx)
            val req = OneTimeWorkRequestBuilder<TelechargementProjet>()
                .setInputData(
                    workDataOf(
                        CLE_MANIFESTE to ProjetsDuServeur.json(m),
                        CLE_SERVEUR to p.getString("serveur", "").orEmpty(),
                        CLE_JETON to p.getString("jeton", null)?.ifBlank { null },
                        CLE_RACINE to racine.toString(),
                    ),
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (p.getBoolean("4g", false)) NetworkType.CONNECTED else NetworkType.UNMETERED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(nomTravail(m.projetId), ExistingWorkPolicy.REPLACE, req)
        }

        fun annuler(ctx: Context, projetId: String) = WorkManager.getInstance(ctx).cancelUniqueWork(nomTravail(projetId))
    }
}
