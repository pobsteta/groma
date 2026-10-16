package groma.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service (type specialUse) keeping Groma's process, hence the
 * local endpoint [ServeurLocal], alive while the operator works in QField:
 * without it Android may kill Groma once it is in the background and the
 * QField plugin finds nobody on 127.0.0.1. Started after a capture or a
 * centre measurement; stops itself after [INACTIF_MS] without any request,
 * or from its notification.
 */
class ServicePartageQfield : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var veille: Job? = null
    private var debutMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CANAL, getString(R.string.partage_canal), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ARRET || !premierPlan()) {
            arreterInterne()
            return START_NOT_STICKY
        }
        ServeurLocal.demarrerUneFois(File(filesDir, "detections"), File(getExternalFilesDir(null), "projets"))
        debutMs = System.currentTimeMillis()
        veille?.cancel()
        veille = scope.launch {
            while (isActive) {
                delay(VERIFICATION_MS)
                val dernier = maxOf(debutMs, ServeurLocal.derniereRequeteMs)
                if (System.currentTimeMillis() - dernier > INACTIF_MS) {
                    arreterInterne()
                    break
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Foreground notification; false if the system refuses it. */
    private fun premierPlan(): Boolean {
        val arret = PendingIntent.getService(
            this, 0, Intent(this, ServicePartageQfield::class.java).setAction(ACTION_ARRET),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = NotificationCompat.Builder(this, CANAL)
            .setContentTitle(getString(R.string.partage_titre))
            .setContentText(getString(R.string.partage_texte))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.partage_arreter), arret)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        return runCatching { ServiceCompat.startForeground(this, ID_NOTIF, notif, type) }.isSuccess
    }

    private fun arreterInterne() {
        veille?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CANAL = "partage_qfield"
        private const val ID_NOTIF = 1002
        private const val ACTION_ARRET = "groma.app.partage.ARRET"
        private const val VERIFICATION_MS = 60_000L
        private const val INACTIF_MS = 30 * 60_000L

        /** Starts (or extends) the sharing; call from a visible activity. */
        fun demarrer(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, ServicePartageQfield::class.java)) }
        }
    }
}
