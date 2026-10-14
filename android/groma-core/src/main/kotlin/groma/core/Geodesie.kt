package groma.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distance and geographic azimuth between two nearby WGS 84 points (a
 * position and a plot, up to a few kilometres): local plane on the GRS80
 * ellipsoid, with the meridian and prime vertical radii of curvature at the
 * mid latitude. Error well below a centimetre per 100 m.
 */
object Geodesie {
    private const val A = 6378137.0
    private const val E2 = 0.00669438002290

    /** (east, north) offset in metres from [de] to [vers]. */
    fun decalage(de: Position, vers: Position): Pair<Double, Double> {
        val phi = Math.toRadians((de.latitude + vers.latitude) / 2)
        val w = sqrt(1 - E2 * sin(phi).pow(2))
        val m = A * (1 - E2) / (w * w * w) // meridian radius
        val n = A / w // prime vertical radius
        val est = Math.toRadians(vers.longitude - de.longitude) * n * cos(phi)
        val nord = Math.toRadians(vers.latitude - de.latitude) * m
        return est to nord
    }

    fun distanceM(de: Position, vers: Position): Double = decalage(de, vers).let { (e, n) -> hypot(e, n) }

    /** Geographic azimuth (degrees from true north, clockwise, [0, 360)). */
    fun azimutDeg(de: Position, vers: Position): Double = decalage(de, vers).let { (e, n) -> Azimut.normaliser(Math.toDegrees(atan2(e, n))) }
}
