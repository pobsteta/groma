package groma.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Lambert-93 (EPSG:2154) ↔ WGS 84 (EPSG:4326) and Web Mercator (EPSG:3857),
 * pure Kotlin for the phone map (brief 2026-10-14 carte RTK, § 4).
 *
 * Lambert conformal conic with two standard parallels on GRS80 (IGN
 * algorithms ALG0003 / ALG0004 / ALG0054). RGF93 and WGS 84 are taken as the
 * same datum (gap of a few centimetres, below what the map shows).
 */
object Projections {
    private const val A = 6378137.0
    private const val F = 1 / 298.257222101
    private val E = sqrt(F * (2 - F))
    private const val X0 = 700000.0
    private const val Y0 = 6600000.0
    private val LON0 = Math.toRadians(3.0)

    private fun m(phi: Double) = cos(phi) / sqrt(1 - (E * sin(phi)).pow(2))
    private fun t(phi: Double) = tan(PI / 4 - phi / 2) / ((1 - E * sin(phi)) / (1 + E * sin(phi))).pow(E / 2)

    private val phi1 = Math.toRadians(49.0)
    private val phi2 = Math.toRadians(44.0)
    private val N = (ln(m(phi1)) - ln(m(phi2))) / (ln(t(phi1)) - ln(t(phi2)))
    private val AF = A * m(phi1) / (N * t(phi1).pow(N))
    private val RHO0 = AF * t(Math.toRadians(46.5)).pow(N)

    /** Lambert-93 (m) → (longitude, latitude) in degrees. */
    fun versWgs84(x: Double, y: Double): Pair<Double, Double> {
        val dx = x - X0
        val dy = RHO0 - (y - Y0)
        val rho = sqrt(dx * dx + dy * dy)
        val tt = (rho / AF).pow(1 / N)
        var phi = PI / 2 - 2 * atan(tt)
        repeat(20) {
            val suivant = PI / 2 - 2 * atan(tt * ((1 - E * sin(phi)) / (1 + E * sin(phi))).pow(E / 2))
            if (abs(suivant - phi) < 1e-12) return Pair(Math.toDegrees(atan2(dx, dy) / N + LON0), Math.toDegrees(suivant))
            phi = suivant
        }
        return Pair(Math.toDegrees(atan2(dx, dy) / N + LON0), Math.toDegrees(phi))
    }

    /** (longitude, latitude) in degrees → Lambert-93 (m). */
    fun versLambert93(lon: Double, lat: Double): Pair<Double, Double> {
        val rho = AF * t(Math.toRadians(lat)).pow(N)
        val theta = N * (Math.toRadians(lon) - LON0)
        return Pair(X0 + rho * sin(theta), Y0 + RHO0 - rho * cos(theta))
    }

    /** (longitude, latitude) in degrees → Web Mercator (m). */
    fun versWebMercator(lon: Double, lat: Double): Pair<Double, Double> =
        Pair(A * Math.toRadians(lon), A * ln(tan(PI / 4 + Math.toRadians(lat) / 2)))

    /** Web Mercator (m) → (longitude, latitude) in degrees. */
    fun depuisWebMercator(x: Double, y: Double): Pair<Double, Double> =
        Pair(Math.toDegrees(x / A), Math.toDegrees(2 * atan(exp(y / A)) - PI / 2))
}
