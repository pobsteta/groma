package groma.core

import kotlin.math.atan2
import kotlin.math.sqrt

/** Azimuth arithmetic, same conventions as groma/azimut.py (x east, y north, clockwise). */
object Azimut {
    const val DEGRES_PAR_GRADE = 0.9
    const val LAMBERT93_N = 0.7256077650532670
    const val LAMBERT93_LON0_DEG = 3.0

    fun normaliser(azDeg: Double): Double {
        var a = azDeg % 360.0
        if (a < 0) a += 360.0
        return if (a >= 360.0) 0.0 else a
    }

    fun ecart(aDeg: Double, bDeg: Double): Double {
        val d = normaliser(aDeg - bDeg)
        return if (d > 180.0) d - 360.0 else d
    }

    fun azimut(dx: Double, dy: Double): Double = normaliser(Math.toDegrees(atan2(dx, dy)))

    fun versUnite(azDeg: Double, unite: String): Double = when (unite) {
        "deg" -> normaliser(azDeg)
        "gr" -> normaliser(azDeg) / DEGRES_PAR_GRADE
        else -> throw IllegalArgumentException("Unité d'azimut inconnue : $unite")
    }

    fun depuisUnite(valeur: Double, unite: String): Double = when (unite) {
        "deg" -> normaliser(valeur)
        "gr" -> normaliser(valeur * DEGRES_PAR_GRADE)
        else -> throw IllegalArgumentException("Unité d'azimut inconnue : $unite")
    }

    /** az_geo = az_grille + convergence (Lambert-93). */
    fun convergenceLambert93(lonDeg: Double): Double = LAMBERT93_N * (lonDeg - LAMBERT93_LON0_DEG)

    fun magnetiqueVersGeographique(azMag: Double, declinaison: Double) = normaliser(azMag + declinaison)
    fun geographiqueVersGrille(azGeo: Double, convergence: Double) = normaliser(azGeo - convergence)

    fun distanceHorizontale(distancePente: Double, denivele: Double): Double {
        require(kotlin.math.abs(denivele) <= distancePente) { "Le dénivelé dépasse la distance mesurée." }
        return sqrt(distancePente * distancePente - denivele * denivele)
    }
}
