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
import fr.nemeton.terrain.carte.Emprise
import fr.nemeton.terrain.carte.ZoneTuiles
import groma.app.carte.TuilesHorsLigne
import groma.app.carte.sourceParNom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Download of a project's tiles for working offline (brief carte RTK, § 5.2).
 * Tiles already on disk are skipped, so a retry or a second run resumes
 * where it stopped. One request at a time, with the app's user agent
 * (Géoplateforme fair use).
 */
class TelechargementTuiles(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val racine = TuilesHorsLigne.racine(File(inputData.getString(CLE_DOSSIER)!!))
        val sources = inputData.getStringArray(CLE_SOURCES).orEmpty().mapNotNull(::sourceParNom)
        val e = inputData.getDoubleArray(CLE_EMPRISE)!!
        val zone = ZoneTuiles(Emprise(e[0], e[1], e[2], e[3]))
        val total = zone.nombre * sources.size
        var faites = 0L
        var echecs = 0
        val agent = Configuration.getInstance().userAgentValue
        for (source in sources) for (t in zone.tuiles()) {
            if (isStopped) return@withContext Result.failure()
            val f = TuilesHorsLigne.fichier(racine, source, t)
            if (!f.isFile) {
                try {
                    telecharger(source.getTileURLString(MapTileIndex.getTileIndex(t.z, t.x, t.y)), f, agent)
                } catch (ex: TuileAbsente) {
                    // Outside the layer's coverage (404, empty tile): nothing to keep.
                } catch (ex: IOException) {
                    // Network lost: WorkManager tries again later and resumes.
                    if (++echecs >= ECHECS_MAX) return@withContext Result.retry()
                    delay(1000)
                    continue
                }
            }
            faites++
            if (faites % 20 == 0L || faites == total) setProgress(workDataOf(CLE_FAITES to faites, CLE_TOTAL to total))
        }
        Result.success(workDataOf(CLE_FAITES to faites, CLE_TOTAL to total))
    }

    private class TuileAbsente : Exception()

    private fun telecharger(url: String, f: File, agent: String) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", agent)
            when (c.responseCode) {
                200 -> {}
                204, 404 -> throw TuileAbsente()
                else -> throw IOException("HTTP ${c.responseCode}")
            }
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            c.inputStream.use { entree -> tmp.outputStream().use { entree.copyTo(it) } }
            if (!tmp.renameTo(f)) throw IOException("Écriture impossible : $f")
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val CLE_DOSSIER = "dossier"
        const val CLE_SOURCES = "sources"
        const val CLE_EMPRISE = "emprise"
        const val CLE_FAITES = "faites"
        const val CLE_TOTAL = "total"
        private const val ECHECS_MAX = 20

        fun nomTravail(dossierProjet: File) = "tuiles_${dossierProjet.name}"

        fun planifier(ctx: Context, dossierProjet: File, sources: List<String>, zone: ZoneTuiles) {
            val e = zone.emprise
            val req = OneTimeWorkRequestBuilder<TelechargementTuiles>()
                .setInputData(
                    workDataOf(
                        CLE_DOSSIER to dossierProjet.path,
                        CLE_SOURCES to sources.toTypedArray(),
                        CLE_EMPRISE to doubleArrayOf(e.ouest, e.sud, e.est, e.nord),
                    ),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(nomTravail(dossierProjet), ExistingWorkPolicy.REPLACE, req)
        }

        fun annuler(ctx: Context, dossierProjet: File) = WorkManager.getInstance(ctx).cancelUniqueWork(nomTravail(dossierProjet))
    }
}
