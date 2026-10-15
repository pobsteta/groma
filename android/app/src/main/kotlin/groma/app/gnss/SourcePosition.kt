package groma.app.gnss

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import groma.core.FixGnss
import groma.core.OrigineFix
import groma.core.Position
import groma.core.QualiteFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The one position of the app (brief § 6): the RTK receiver's fix when it has
 * one, else the phone's own GNSS (flagged by [OrigineFix.TELEPHONE]).
 */
object SourcePosition {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val telephone = MutableStateFlow<FixGnss?>(null)

    val fix: StateFlow<FixGnss?> = combine(ServiceGnssRtk.fix, telephone) { r, t -> r ?: t }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun localisationAutorisee(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Listens to the phone's GNSS every second (only while the map is shown,
     * no background location); close the result to stop. No-op without permission.
     */
    @SuppressLint("MissingPermission")
    fun ecouterTelephone(ctx: Context): AutoCloseable {
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (lm == null || !localisationAutorisee(ctx)) return AutoCloseable {}
        val auditeur = object : LocationListener {
            override fun onLocationChanged(l: Location) { telephone.value = fixDepuis(l) }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) { telephone.value = null }
        }
        runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.takeIf { System.currentTimeMillis() - it.time < 60_000 }
                ?.let { telephone.value = fixDepuis(it) }
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, auditeur, Looper.getMainLooper())
        }
        return AutoCloseable {
            runCatching { lm.removeUpdates(auditeur) }
            telephone.value = null
        }
    }

    /**
     * Rough position for sorting the projects by distance, without listening:
     * the current fix, else the phone's last known location (any provider,
     * less than a day old). Null without the location permission.
     */
    @SuppressLint("MissingPermission")
    fun positionApprochee(ctx: Context): Position? {
        fix.value?.let { return it.position }
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        if (!localisationAutorisee(ctx)) return null
        return runCatching {
            lm.allProviders.mapNotNull { lm.getLastKnownLocation(it) }
                .filter { System.currentTimeMillis() - it.time < 24 * 3600_000L }
                .maxByOrNull { it.time }?.let { Position(it.latitude, it.longitude) }
        }.getOrNull()
    }

    private fun fixDepuis(l: Location): FixGnss {
        val precision = if (l.hasAccuracy()) l.accuracy.toDouble() else null
        return FixGnss(
            position = Position(l.latitude, l.longitude),
            // The phone is never RTK, whatever its accuracy claims: autonomous at best.
            qualite = if (precision == null) QualiteFix.AUCUN else QualiteFix.AUTONOME,
            nbSatellites = l.extras?.getInt("satellites") ?: 0,
            hdop = null,
            altitudeM = if (l.hasAltitude()) l.altitude else null,
            ageCorrectionsS = null,
            precisionHorizontaleM = precision,
            capDeg = if (l.hasBearing()) l.bearing.toDouble() else null,
            vitesseMs = if (l.hasSpeed()) l.speed.toDouble() else null,
            origine = OrigineFix.TELEPHONE,
        )
    }
}
