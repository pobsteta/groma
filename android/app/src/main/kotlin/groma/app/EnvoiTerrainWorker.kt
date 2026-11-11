package groma.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import groma.core.CleProjet
import groma.core.EtatEnvoiTerrain
import groma.core.ExportTerrain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Queued, resumable upload of the "terrain" archive of ONE plot (mission 021): what was entered in
 * Groma (tree sheets, targets, centres) travels to the server as `<plot_id>.terrain.zip`, same server,
 * token and Wi-Fi / 4G setting as [EnvoiWorker], same resumable protocol with `"type": "terrain"`.
 *
 * Triggered after every save with a 30 s delay and REPLACE: a burst of saves makes one upload.
 * Nothing is sent when the content fingerprint equals the last one sent successfully.
 */
class EnvoiTerrainWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dossier = File(inputData.getString(CLE_DOSSIER)!!)
        val plotId = inputData.getString(CLE_PLOT)!!
        val projetId = inputData.getString(CLE_PROJET)!!
        val serveur = inputData.getString(CLE_SERVEUR)!!.trimEnd('/')
        val jeton = inputData.getString(CLE_JETON)
        val cle = CleProjet.chemin(projetId, plotId)
        val prefs = Preferences.prefs(applicationContext)
        val maj = prefs.getLong(PREF_MAJ + cle, 0L)
        val gpkg = File(dossier, "inventaire_video.gpkg")
        if (!gpkg.exists()) return@withContext Result.success() // project removed since

        val dossierEnvoi = File(applicationContext.filesDir, "terrain/$projetId").also { it.mkdirs() }
        val zip = File(dossierEnvoi, "$plotId.terrain.zip")
        val etat = File(dossierEnvoi, "$plotId.terrain.envoi") // "<empreinte>\n<id du téléversement>"
        val export = ExportTerrain { f -> SqliteAndroid(SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READWRITE)) }
        try {
            val empreinte = try { export.empreinte(gpkg, dossier, plotId) } catch (e: IllegalArgumentException) {
                return@withContext Result.success() // plot not in this project: nothing to send
            }
            if (empreinte == prefs.getString(PREF_EMPREINTE + cle, null)) {
                // Content already on the server: only the stamp moves, nothing is sent.
                prefs.edit().putLong(PREF_ENVOYE_MAJ + cle, maj).apply()
                return@withContext Result.success()
            }
            val reprise = etat.takeIf { it.exists() && zip.exists() }?.readLines()?.takeIf { it.size == 2 && it[0] == empreinte }
            var id = reprise?.get(1)
            if (id == null) {
                zip.delete()
                export.archiver(gpkg, dossier, plotId, zip)
                id = creer(serveur, jeton, projetId, plotId, zip)
                etat.writeText("$empreinte\n$id")
            }
            var recu = JSONObject(requete("GET", "$serveur/televersements/$id", jeton)).getLong("recu")
            RandomAccessFile(zip, "r").use { f ->
                val tampon = ByteArray(TAILLE_MORCEAU)
                while (recu < f.length()) {
                    f.seek(recu)
                    val n = f.read(tampon)
                    recu = JSONObject(requete("PUT", "$serveur/televersements/$id?offset=$recu", jeton, tampon.copyOf(n))).getLong("recu")
                }
            }
            requete("POST", "$serveur/televersements/$id/terminer", jeton)
            prefs.edit().putString(PREF_EMPREINTE + cle, empreinte).putLong(PREF_ENVOYE_MAJ + cle, maj)
                .putLong(PREF_ENVOYE_LE + cle, System.currentTimeMillis()).apply()
            etat.delete(); zip.delete()
            Result.success()
        } catch (e: java.io.FileNotFoundException) {
            etat.delete() // upload id unknown on the server: start again
            Result.retry()
        } catch (e: java.io.IOException) {
            Result.retry()
        }
    }

    private fun creer(serveur: String, jeton: String?, projetId: String, plotId: String, zip: File): String {
        val corps = JSONObject().put("plot_id", plotId).put("type", "terrain")
        if (projetId.isNotEmpty()) corps.put("projet_id", projetId)
        corps.put("taille", zip.length()).put("sha256", sha256(zip))
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
        const val CLE_DOSSIER = "dossier"; const val CLE_PLOT = "plot_id"; const val CLE_PROJET = "projet_id"
        const val CLE_SERVEUR = "serveur"; const val CLE_JETON = "jeton"
        const val TAILLE_MORCEAU = 4 * 1024 * 1024

        /** Provisional value, to judge in the field: wait this long after the last save before sending. */
        const val DELAI_REGROUPEMENT_S = 30L

        private const val PREF_MAJ = "terrain_maj_"
        private const val PREF_ENVOYE_MAJ = "terrain_envoye_maj_"
        private const val PREF_ENVOYE_LE = "terrain_envoye_le_"
        private const val PREF_EMPREINTE = "terrain_empreinte_"

        /**
         * Called after each save: stamps the plot "to send" (shown in the plot list) and, when a server
         * is configured, (re)plans the upload [DELAI_REGROUPEMENT_S] seconds ahead, replacing a pending one.
         */
        fun apresEnregistrement(ctx: Context, projet: Projet, plotId: String) {
            val projetId = projet.projetId
            val cle = CleProjet.chemin(projetId, plotId)
            val p = Preferences.prefs(ctx)
            p.edit().putLong(PREF_MAJ + cle, System.currentTimeMillis()).apply()
            val serveur = p.getString("serveur", "").orEmpty()
            if (serveur.isBlank()) return // kept "to send" until a server is configured and a save plans it
            val req = OneTimeWorkRequestBuilder<EnvoiTerrainWorker>()
                .setInputData(workDataOf(
                    CLE_DOSSIER to projet.dossier.path, CLE_PROJET to projetId, CLE_PLOT to plotId,
                    CLE_SERVEUR to serveur, CLE_JETON to p.getString("jeton", null)?.ifBlank { null },
                ))
                .setInitialDelay(DELAI_REGROUPEMENT_S, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (p.getBoolean("4g", false)) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("terrain/$cle", ExistingWorkPolicy.REPLACE, req)
        }

        fun etat(ctx: Context, projetId: String, plotId: String): EtatEnvoiTerrain {
            val p = Preferences.prefs(ctx)
            val cle = CleProjet.chemin(projetId, plotId)
            fun long(nom: String) = if (p.contains(nom + cle)) p.getLong(nom + cle, 0L) else null
            return EtatEnvoiTerrain.de(long(PREF_MAJ), long(PREF_ENVOYE_MAJ), long(PREF_ENVOYE_LE))
        }
    }
}
