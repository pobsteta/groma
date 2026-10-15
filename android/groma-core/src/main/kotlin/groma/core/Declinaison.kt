package groma.core

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Magnetic declination from the World Magnetic Model WMM2025, same algorithm
 * as `groma.declinaison` (Python). Coefficients: resource `/groma/WMM2025.COF`,
 * copied at build time from `core/src/groma/donnees/WMM2025.COF` (official
 * NOAA/NCEI file, public domain, epoch 2025.0, valid until 2030.0).
 */
object Declinaison {
    private const val DEGRE = 12
    private const val A_WGS84 = 6378.137
    private const val F_WGS84 = 1 / 298.257223563
    private const val E2 = F_WGS84 * (2 - F_WGS84)
    private const val RAYON_REF = 6371.2

    private class Modele(val epoque: Double, val g: Array<DoubleArray>, val h: Array<DoubleArray>, val gd: Array<DoubleArray>, val hd: Array<DoubleArray>)

    private val modele: Modele by lazy {
        val texte = Declinaison::class.java.getResourceAsStream("/groma/WMM2025.COF")?.bufferedReader()?.readText()
            ?: error("Ressource WMM2025.COF absente")
        val lignes = texte.lines()
        val tab = { Array(DEGRE + 1) { DoubleArray(DEGRE + 1) } }
        val m = Modele(lignes[0].trim().split(Regex("\\s+"))[0].toDouble(), tab(), tab(), tab(), tab())
        for (ligne in lignes.drop(1)) {
            val c = ligne.trim().split(Regex("\\s+"))
            if (c.size != 6) break // trailer line of 9s
            val n = c[0].toInt(); val k = c[1].toInt()
            m.g[n][k] = c[2].toDouble(); m.h[n][k] = c[3].toDouble()
            m.gd[n][k] = c[4].toDouble(); m.hd[n][k] = c[5].toDouble()
        }
        m
    }

    /** Decimal year as in the WMM software: year + (day of year - 1) / days in year. */
    fun anneeDecimale(date: LocalDate): Double = date.year + (date.dayOfYear - 1).toDouble() / date.lengthOfYear()

    /** Validity of the model, as decimal years. */
    val validite: ClosedFloatingPointRange<Double> get() = modele.epoque..(modele.epoque + 5)

    /** Declination at [date], or null outside the model validity. */
    fun ouNull(latDeg: Double, lonDeg: Double, date: LocalDate = LocalDate.now(), altitudeM: Double = 0.0): Double? =
        anneeDecimale(date).takeIf { it in validite }?.let { declinaisonDeg(latDeg, lonDeg, it, altitudeM) }

    /**
     * Magnetic declination (degrees, positive east) at a WGS84 point, at
     * decimal year [annee]; [altitudeM] above the ellipsoid. Throws outside
     * 2025.0 to 2030.0: an extrapolated value would be wrong without warning.
     */
    fun declinaisonDeg(latDeg: Double, lonDeg: Double, annee: Double, altitudeM: Double = 0.0): Double {
        val md = modele
        require(annee in validite) { "Date ${"%.2f".format(annee)} hors de la validité du modèle WMM2025 (${md.epoque} à ${md.epoque + 5})." }
        require(latDeg in -90.0..90.0) { "Latitude $latDeg hors de [-90, 90]." }
        val dt = annee - md.epoque
        val h = altitudeM / 1000

        // Geodetic -> geocentric spherical coordinates.
        val phi = Math.toRadians(latDeg)
        val lam = Math.toRadians(lonDeg)
        val rc = A_WGS84 / sqrt(1 - E2 * sin(phi).pow(2))
        val p = (rc + h) * cos(phi)
        val z = (rc * (1 - E2) + h) * sin(phi)
        val r = hypot(p, z)
        val phiC = asin(z / r)

        // Schmidt semi-normalised Legendre functions of the colatitude and
        // their derivatives (Gauss recursion, then scaling).
        val theta = PI / 2 - phiC
        val ct = cos(theta)
        val st = max(sin(theta), 1e-12) // poles: Y is undefined there
        val pp = Array(DEGRE + 1) { DoubleArray(DEGRE + 1) }
        val dp = Array(DEGRE + 1) { DoubleArray(DEGRE + 1) }
        pp[0][0] = 1.0
        for (n in 1..DEGRE) for (m in 0..n) {
            when {
                n == m -> {
                    pp[n][m] = st * pp[n - 1][m - 1]
                    dp[n][m] = st * dp[n - 1][m - 1] + ct * pp[n - 1][m - 1]
                }
                n == 1 -> {
                    pp[n][m] = ct * pp[n - 1][m]
                    dp[n][m] = ct * dp[n - 1][m] - st * pp[n - 1][m]
                }
                else -> {
                    val k = ((n - 1.0) * (n - 1) - m * m) / ((2.0 * n - 1) * (2 * n - 3))
                    pp[n][m] = ct * pp[n - 1][m] - k * pp[n - 2][m]
                    dp[n][m] = ct * dp[n - 1][m] - st * pp[n - 1][m] - k * dp[n - 2][m]
                }
            }
        }
        val s = Array(DEGRE + 1) { DoubleArray(DEGRE + 1) }
        s[0][0] = 1.0
        for (n in 1..DEGRE) {
            s[n][0] = s[n - 1][0] * (2.0 * n - 1) / n
            for (m in 1..n) s[n][m] = s[n][m - 1] * sqrt((n - m + 1.0) * (if (m == 1) 2 else 1) / (n + m))
        }

        // Field in the geocentric frame (north, east, down); X = -B_theta.
        var xc = 0.0; var yc = 0.0; var zc = 0.0
        for (n in 1..DEGRE) {
            val rapport = (RAYON_REF / r).pow(n + 2)
            for (m in 0..n) {
                val g = md.g[n][m] + dt * md.gd[n][m]
                val hh = md.h[n][m] + dt * md.hd[n][m]
                val cm = cos(m * lam); val sm = sin(m * lam)
                val pnm = s[n][m] * pp[n][m]; val dpnm = s[n][m] * dp[n][m]
                xc += rapport * (g * cm + hh * sm) * dpnm
                yc += rapport * m * (g * sm - hh * cm) * pnm / st
                zc -= (n + 1) * rapport * (g * cm + hh * sm) * pnm
            }
        }
        val psi = phiC - phi
        val x = xc * cos(psi) - zc * sin(psi)
        return Math.toDegrees(atan2(yc, x))
    }
}
