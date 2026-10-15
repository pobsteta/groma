package groma.core

import kotlin.math.roundToInt

/**
 * Guidance from the position to a plot (brief 2026-10-14 carte RTK, § 5.3):
 * distance, geographic azimuth and, with the plot's declination, the magnetic
 * azimuth to sight with a compass, both in the plot's unit (degrees or grades).
 */
data class Guidage(
    val plotId: String,
    val distanceM: Double,
    /** Geographic azimuth, degrees from true north. */
    val azimutGeoDeg: Double,
    /** Magnetic azimuth in degrees, null when the plot has no declination. */
    val azimutMagDeg: Double?,
    /** Unit of the plot's azimuths: "deg" or "gr". */
    val unite: String,
) {
    /** Within [SEUIL_ARRIVEE_M] of the planned point. */
    val arrive: Boolean get() = distanceM < SEUIL_ARRIVEE_M

    /** Azimuth to sight with the compass: magnetic when known, else geographic, in the plot's unit, rounded. */
    val azimutVise: Int get() = arrondiUnite(azimutMagDeg ?: azimutGeoDeg)

    /** Geographic azimuth in the plot's unit, rounded. */
    val azimutGeo: Int get() = arrondiUnite(azimutGeoDeg)

    /**
     * Direction of the arrow relative to the phone's heading [capDeg] (true
     * north), degrees in (-180, 180], positive to the right; null without a heading.
     */
    fun relatifDeg(capDeg: Double?): Double? = capDeg?.let { Azimut.ecart(azimutGeoDeg, it) }

    // 400 gr and 360° are north again, shown as 0.
    private fun arrondiUnite(azDeg: Double): Int {
        val pleinTour = if (unite == "gr") 400 else 360
        return Azimut.versUnite(azDeg, unite).roundToInt() % pleinTour
    }

    companion object {
        const val SEUIL_ARRIVEE_M = 5.0

        fun calculer(de: Position, vers: PlacetteCarte): Guidage {
            val cible = Position(vers.lat, vers.lon)
            val geo = Geodesie.azimutDeg(de, cible)
            val unite = vers.uniteAzimut.takeIf { it == "deg" || it == "gr" } ?: "deg"
            return Guidage(
                vers.plotId, Geodesie.distanceM(de, cible), geo,
                vers.declinaisonDeg?.let { Azimut.normaliser(geo - it) }, unite,
            )
        }
    }
}
