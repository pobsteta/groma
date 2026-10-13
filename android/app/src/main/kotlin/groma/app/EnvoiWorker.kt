package groma.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import groma.core.CleProjet
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Queued, resumable upload of a plot package (protocol in server/src/groma_server/api.py).
 * Wi-Fi only by default (unmetered), 4G when the user allows it; WorkManager
 * retries with backoff and survives reboots; the server tells where to resume.
 */
class EnvoiWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val paquet = File(inputData.getString(CLE_PAQUET)!!)
        val plotId = inputData.getString(CLE_PLOT)!!
        val projetId = inputData.getString(CLE_PROJET).orEmpty() // empty: queued by a version without projects
        val serveur = inputData.getString(CLE_SERVEUR)!!.trimEnd('/')
        val jeton = inputData.getString(CLE_JETON)
        val etat = File(paquet.path + ".envoi") // keeps the upload id across retries
        try {
            val id = if (etat.exists()) etat.readText() else creer(serveur, jeton, projetId, plotId, paquet).also { etat.writeText(it) }
            var recu = JSONObject(requete("GET", "$serveur/televersements/$id", jeton)).getLong("recu")
            RandomAccessFile(paquet, "r").use { f ->
                val tampon = ByteArray(TAILLE_MORCEAU)
                while (recu < f.length()) {
                    f.seek(recu)
                    val n = f.read(tampon)
                    recu = JSONObject(requete("PUT", "$serveur/televersements/$id?offset=$recu", jeton, tampon.copyOf(n))).getLong("recu")
                    setProgress(workDataOf("recu" to recu, "taille" to f.length()))
                }
            }
            val tache = JSONObject(requete("POST", "$serveur/televersements/$id/terminer", jeton)).getString("tache_id")
            etat.delete()
            Result.success(workDataOf("tache_id" to tache))
        } catch (e: java.io.FileNotFoundException) {
            etat.delete() // upload id unknown on the server: start again
            Result.retry()
        } catch (e: java.io.IOException) {
            Result.retry()
        }
    }

    private fun creer(serveur: String, jeton: String?, projetId: String, plotId: String, paquet: File): String {
        val corps = JSONObject().put("plot_id", plotId)
        if (projetId.isNotEmpty()) corps.put("projet_id", projetId)
        corps.put("taille", paquet.length()).put("sha256", sha256(paquet))
        return JSONObject(requete("POST", "$serveur/televersements", jeton, corps.toString().toByteArray(), "application/json")).getString("id")
    }

    private fun requete(methode: String, url: String, jeton: String?, corps: ByteArray? = null, type: String = "application/octet-stream"): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = methode
        c.connectTimeout = 15_000; c.readTimeout = 60_000
        jeton?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (corps != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", type)
            c.setFixedLengthStreamingMode(corps.size)
            c.outputStream.use { it.write(corps) }
        }
        val code = c.responseCode
        if (code == 409) return c.errorStream.bufferedReader().readText() // carries the offset to resume from
        if (code == 404) throw java.io.FileNotFoundException(url)
        if (code !in 200..299) throw java.io.IOException("HTTP $code sur $url")
        return c.inputStream.bufferedReader().readText()
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(1 shl 16); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val CLE_PAQUET = "paquet"; const val CLE_PLOT = "plot_id"; const val CLE_PROJET = "projet_id"; const val CLE_SERVEUR = "serveur"; const val CLE_JETON = "jeton"
        const val TAILLE_MORCEAU = 4 * 1024 * 1024

        fun planifier(ctx: Context, paquet: File, projetId: String, plotId: String, serveur: String, jeton: String?, autoriser4g: Boolean) {
            val req = OneTimeWorkRequestBuilder<EnvoiWorker>()
                .setInputData(workDataOf(CLE_PAQUET to paquet.path, CLE_PROJET to projetId, CLE_PLOT to plotId, CLE_SERVEUR to serveur, CLE_JETON to jeton))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (autoriser4g) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(CleProjet.travailEnvoi(projetId, plotId), ExistingWorkPolicy.REPLACE, req)
        }
    }
}
