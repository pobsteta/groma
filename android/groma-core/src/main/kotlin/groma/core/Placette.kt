package groma.core

import java.util.UUID
import kotlin.math.roundToLong

/** Plot parameters as stored in the `placettes` layer. */
data class ParametresPlacette(
    val plotId: String,
    val centreX: Double,
    val centreY: Double,
    val rayonM: Double = 20.0,
    val seuilDiamCm: Double = 7.5,
    val uniteAzimut: String = "deg",
    val declinaisonDeg: Double = 0.0,
    val convergenceDeg: Double = 0.0,
) {
    val numerotation get() = ParametresNumerotation(rayonM, seuilDiamCm, uniteAzimut)
}

data class TroncGeoreference(val x: Double, val y: Double, val dbhCm: Double, val score: Double? = null, val uuid: String? = null)

/** From georeferenced trunks to `detections` rows (port of groma/placette.py). */
object Placette {
    private fun arrondi(v: Double, n: Int): Double {
        val f = Math.pow(10.0, n.toDouble())
        return (v * f).roundToLong() / f
    }

    fun construireDetections(
        troncs: List<TroncGeoreference>, p: ParametresPlacette, houppiers: List<Houppier> = emptyList(),
        methode: String = "embarque", etat: String = "provisoire",
    ): List<Detection> {
        val avecUuid = troncs.map { it.copy(uuid = it.uuid ?: UUID.randomUUID().toString()) }
        val arbres = avecUuid.map {
            val (dx, dy) = Numerotation.offsetsGeographiques(it.x - p.centreX, it.y - p.centreY, p.convergenceDeg)
            ArbreANumeroter(it.uuid!!, dx, dy, it.dbhCm)
        }
        val num = Numerotation.numeroter(arbres, p.numerotation)
        return avecUuid.filter { it.uuid in num.numeros }.map {
            val h = Houppiers.affecter(it.x, it.y, houppiers)
            Detection(
                uuid = it.uuid!!, plotId = p.plotId, x = it.x, y = it.y, numProvisoire = num.numeros.getValue(it.uuid),
                azimut = arrondi(num.azimuts.getValue(it.uuid), 4), distanceM = arrondi(num.distances.getValue(it.uuid), 3),
                dbhCm = arrondi(it.dbhCm, 1), hM = h.hM, hSource = h.hSource, houppierId = h.houppierId,
                etatDetection = etat, methode = methode, score = it.score,
            )
        }.sortedBy { it.numProvisoire.toInt() }
    }

    /**
     * Keep the uuids of a previous provisional run (re-run on the same plot),
     * so that what was typed in QField stays attached to the same trees.
     */
    fun reutiliserUuid(anciennes: List<Detection>, nouveaux: List<TroncGeoreference>, toleranceM: Double = 0.5): List<TroncGeoreference> {
        val a = Appariement.apparier(anciennes.map { Pair(it.x, it.y) }, nouveaux.map { Pair(it.x, it.y) }, toleranceM)
        return nouveaux.mapIndexed { i, t -> if (a[i] >= 0) t.copy(uuid = anciennes[a[i]].uuid) else t }
    }
}
