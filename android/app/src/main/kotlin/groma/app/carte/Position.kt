package groma.app.carte

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import groma.core.FixGnss
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * « My position » (taken from Marculus): blue dot, accuracy circle to scale,
 * direction cone. The cone follows the GNSS course above 0.8 m/s, else the
 * smoothed compass (brief § 5.1).
 */
class CouchePosition(private val densite: Float) : Overlay() {
    var fix: FixGnss? = null
    /** Compass heading (degrees from true north), null without a compass. */
    var boussoleDeg: Float? = null

    private val cercle = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = (0x22 shl 24) or (BLEU and 0xFFFFFF) }
    private val bordCercle = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * densite; color = (0x55 shl 24) or (BLEU and 0xFFFFFF) }
    private val cone = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val anneau = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt() }
    private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = BLEU }
    private val p = Point()

    /** Heading shown: GNSS course when moving, else the compass. */
    val capDeg: Float?
        get() {
            val f = fix ?: return boussoleDeg
            val cap = f.capDeg
            return if (cap != null && (f.vitesseMs ?: 0.0) >= VITESSE_CAP_MS) cap.toFloat() else boussoleDeg
        }

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val f = fix ?: return
        val proj = mapView.projection
        proj.toPixels(GeoPoint(f.position.latitude, f.position.longitude), p)
        val x = p.x.toFloat(); val y = p.y.toFloat()
        f.precisionHorizontaleM?.let { m ->
            val r = proj.metersToPixels(m.toFloat(), f.position.latitude, mapView.zoomLevelDouble)
            if (r > RAYON_POINT * densite) { c.drawCircle(x, y, r, cercle); c.drawCircle(x, y, r, bordCercle) }
        }
        capDeg?.let { cap ->
            val r = RAYON_CONE * densite
            cone.shader = RadialGradient(x, y, r, intArrayOf((0x66 shl 24) or (BLEU and 0xFFFFFF), BLEU and 0xFFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            // Canvas angles: 0° = east, clockwise; heading 0° = north = -90°. The map is never rotated.
            c.drawArc(x - r, y - r, x + r, y + r, cap - 90f - DEMI_ANGLE, DEMI_ANGLE * 2f, true, cone)
        }
        c.drawCircle(x, y, (RAYON_POINT + 2.5f) * densite, anneau)
        c.drawCircle(x, y, RAYON_POINT * densite, point)
    }

    private companion object {
        const val BLEU = 0xFF1976D2.toInt()
        const val RAYON_POINT = 7f
        const val RAYON_CONE = 58f
        const val DEMI_ANGLE = 26f
        const val VITESSE_CAP_MS = 0.8
    }
}

/**
 * Compass from the rotation vector sensor, true north (magnetic declination
 * from the World Magnetic Model at the position), smoothed on the unit circle.
 * Close the result to stop listening.
 */
class Boussole(ctx: Context, private val position: () -> Pair<Double, Double>?, private val onCap: (Float) -> Unit) : AutoCloseable {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val capteur: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private var sx = 0.0
    private var sy = 0.0
    private var initialise = false

    private val auditeur = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotation, e.values)
            SensorManager.getOrientation(rotation, orientation)
            var cap = Math.toDegrees(orientation[0].toDouble())
            position()?.let { (lat, lon) ->
                cap += GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
            }
            val a = Math.toRadians(cap)
            if (!initialise) { sx = sin(a); sy = cos(a); initialise = true } else {
                sx += LISSAGE * (sin(a) - sx); sy += LISSAGE * (cos(a) - sy)
            }
            onCap(((Math.toDegrees(atan2(sx, sy)) + 360) % 360).toFloat())
        }

        override fun onAccuracyChanged(s: Sensor?, precision: Int) {}
    }

    init {
        capteur?.let { sm.registerListener(auditeur, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun close() = sm.unregisterListener(auditeur)

    private companion object { const val LISSAGE = 0.15 }
}
