package groma.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Calibration state of the compass, from the sensor accuracy (SensorManager.SENSOR_STATUS_*). */
enum class EtalonnageBoussole { BON, MOYEN, FAIBLE, A_ETALONNER }

/** One reading: [capDeg] is the **magnetic** azimuth (degrees) of the phone's top edge. */
data class LectureBoussoleMagnetique(val capDeg: Double?, val etalonnage: EtalonnageBoussole)

/**
 * Phone compass for the cibles view, adapted from Surculus' `BoussoleTelephone` (same author, GPL-3.0):
 * rotation vector sensor, **magnetic** north (the declination is added once, in `PlacementCible.position`),
 * smoothed on the unit circle, with the calibration state of the sensor. Close to stop listening.
 */
class BoussoleMagnetique(ctx: Context, private val onLecture: (LectureBoussoleMagnetique) -> Unit) : AutoCloseable {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val capteur: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private var sx = 0.0
    private var sy = 0.0
    private var initialise = false
    private var etalonnage = EtalonnageBoussole.A_ETALONNER

    val disponible: Boolean get() = capteur != null

    private val auditeur = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotation, e.values)
            SensorManager.getOrientation(rotation, orientation)
            val a = orientation[0].toDouble() // radians from magnetic north, clockwise
            if (!initialise) { sx = sin(a); sy = cos(a); initialise = true } else {
                sx += LISSAGE * (sin(a) - sx); sy += LISSAGE * (cos(a) - sy)
            }
            onLecture(LectureBoussoleMagnetique((Math.toDegrees(atan2(sx, sy)) + 360.0) % 360.0, etalonnage))
        }

        override fun onAccuracyChanged(s: Sensor?, precision: Int) {
            etalonnage = when (precision) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> EtalonnageBoussole.BON
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> EtalonnageBoussole.MOYEN
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> EtalonnageBoussole.FAIBLE
                else -> EtalonnageBoussole.A_ETALONNER
            }
            if (initialise) onLecture(LectureBoussoleMagnetique((Math.toDegrees(atan2(sx, sy)) + 360.0) % 360.0, etalonnage))
        }
    }

    init {
        capteur?.let { sm.registerListener(auditeur, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun close() = sm.unregisterListener(auditeur)

    private companion object { const val LISSAGE = 0.15 }
}
