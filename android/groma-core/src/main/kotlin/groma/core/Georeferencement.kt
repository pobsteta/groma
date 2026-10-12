package groma.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Ray (origin, unit direction) in the local frame, z up. */
data class Rayon(val o: DoubleArray, val d: DoubleArray)

data class CibleMesuree(val idCible: String, val azimutMesure: Double, val distanceMesureeM: Double, val dbhMesureCm: Double?)

/** Local -> Lambert-93 transform with a fixed point (port of groma/georeferencement.py, scale left at 1 on the phone). */
data class Georef(
    val centreLocal: Pair<Double, Double>, val centreGrille: Pair<Double, Double>, val rotationDeg: Double,
    val residusAzimutDeg: Map<String, Double>,
) {
    fun versGrille(x: Double, y: Double): Pair<Double, Double> {
        val dx = x - centreLocal.first; val dy = y - centreLocal.second
        val t = Math.toRadians(rotationDeg)
        return Pair(centreGrille.first + dx * cos(t) + dy * sin(t), centreGrille.second - dx * sin(t) + dy * cos(t))
    }
}

object Georeferencement {
    /** Least-squares point closest to a set of rays. */
    fun trianguler(rayons: List<Rayon>): DoubleArray {
        require(rayons.size >= 2) { "Au moins deux vues sont nécessaires." }
        val a = Array(3) { DoubleArray(3) }
        val b = DoubleArray(3)
        for (r in rayons) {
            for (i in 0..2) for (j in 0..2) {
                val p = (if (i == j) 1.0 else 0.0) - r.d[i] * r.d[j]
                a[i][j] += p
                b[i] += p * r.o[j]
            }
        }
        return Cercle.resoudre3(a, b)
    }

    fun georeferencer(
        c0: Pair<Double, Double>, cibles: Map<String, Pair<Double, Double>>, mesures: List<CibleMesuree>,
        centreGrille: Pair<Double, Double>, unite: String, declinaisonDeg: Double, convergenceDeg: Double,
    ): Georef {
        val communs = mesures.filter { it.idCible in cibles }
        require(communs.isNotEmpty()) { "Aucune cible commune entre la prise de vue et les mesures." }
        var sx = 0.0; var sy = 0.0
        val azLoc = HashMap<String, Double>(); val azMes = HashMap<String, Double>()
        for (c in communs) {
            val (px, py) = cibles.getValue(c.idCible)
            val d = hypot(px - c0.first, py - c0.second)
            azLoc[c.idCible] = Azimut.azimut(px - c0.first, py - c0.second)
            azMes[c.idCible] = Azimut.geographiqueVersGrille(
                Azimut.magnetiqueVersGeographique(Azimut.depuisUnite(c.azimutMesure, unite), declinaisonDeg), convergenceDeg)
            val t = Math.toRadians(Azimut.ecart(azMes.getValue(c.idCible), azLoc.getValue(c.idCible)))
            sx += d * cos(t); sy += d * sin(t)
        }
        val rot = Azimut.normaliser(Math.toDegrees(atan2(sy, sx)))
        val residus = azLoc.keys.associateWith { Azimut.ecart(Azimut.normaliser(azLoc.getValue(it) + rot), azMes.getValue(it)) }
        return Georef(c0, centreGrille, rot, residus)
    }

    fun rmse(v: Collection<Double>) = if (v.isEmpty()) Double.NaN else sqrt(v.sumOf { it * it } / v.size)
}
