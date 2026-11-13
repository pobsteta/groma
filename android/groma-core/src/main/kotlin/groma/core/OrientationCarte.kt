package groma.core

import kotlin.math.abs

/**
 * Map orientation while navigating (mission 034): north up, or heading up so that what is in
 * front of the operator is at the top of the screen. Pure, for the map screen.
 */
object OrientationCarte {
    /** Smallest change (degrees) worth turning the map: the compass jitters by about a degree. */
    const val SEUIL_DEG = 2.0

    /** Map rotation (osmdroid `mapOrientation`, degrees in [0, 360)) for a heading [capDeg]. */
    fun rotation(capEnHaut: Boolean, capDeg: Double?): Double =
        if (!capEnHaut || capDeg == null || !capDeg.isFinite()) 0.0 else Azimut.normaliser(-capDeg)

    /** New rotation when it moved by at least [SEUIL_DEG] (shortest way round), else null. */
    fun aTourner(actuelle: Double, voulue: Double): Double? {
        val d = abs(((voulue - actuelle) % 360 + 540) % 360 - 180)
        return if (d >= SEUIL_DEG || (voulue == 0.0 && actuelle != 0.0)) voulue else null
    }
}
