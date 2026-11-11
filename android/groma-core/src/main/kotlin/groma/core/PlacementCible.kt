package groma.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Origin of a target azimuth (schema domain `source_azimut`). */
enum class SourceAzimut(val code: String) {
    BOUSSOLE_VISEE("boussole_visee"),
    BOUSSOLE_TELEPHONE("boussole_telephone"),
}

enum class TypeAlerte { ECART_ANGULAIRE, TROP_PROCHE_DU_CENTRE, MOINS_DE_TROIS_CIBLES }

/** [cibles] is sorted; empty for a global alert. */
data class AlerteCible(val type: TypeAlerte, val cibles: List<String>)

/** [azimutDeg] is the magnetic azimuth in degrees. */
data class CiblePlacee(
    val id: String,
    val azimutDeg: Double,
    val distanceM: Double,
    val dbhCm: Double,
    val source: SourceAzimut,
)

/** Placement of targets C1-C3 from azimuth and horizontal distance (mission 016). */
object PlacementCible {
    const val DISTANCE_MAX_M = 60.0
    const val DIAMETRE_MAX_CM = 1000.0
    const val ECART_ANGULAIRE_MIN_DEG = 60.0 // provisional (fiche 015)
    const val DISTANCE_MIN_CENTRE_M = 8.0 // provisional (fiche 015)

    val IDENTIFIANTS = listOf("C1", "C2", "C3")

    /** Lambert 93 (x, y) of the target; throws IllegalArgumentException on a refused input. */
    fun position(
        centre: Pair<Double, Double>, azimut: Double, unite: String,
        declinaisonDeg: Double, convergenceDeg: Double, distanceM: Double,
    ): Pair<Double, Double> {
        controlerAzimutDistance(azimut, unite, distanceM)
        val mag = Azimut.depuisUnite(azimut, unite)
        val geo = Azimut.magnetiqueVersGeographique(mag, declinaisonDeg)
        val grille = Math.toRadians(Azimut.geographiqueVersGrille(geo, convergenceDeg))
        return Pair(centre.first + distanceM * sin(grille), centre.second + distanceM * cos(grille))
    }

    fun controler(id: String, azimut: Double, unite: String, distanceM: Double, dbhCm: Double) {
        require(id in IDENTIFIANTS) { "Identifiant de cible inconnu : « $id » (C1, C2 ou C3)." }
        controlerAzimutDistance(azimut, unite, distanceM)
        require(dbhCm.isFinite() && dbhCm > 0.0 && dbhCm <= DIAMETRE_MAX_CM) {
            "Le diamètre doit être compris entre 0 (exclu) et ${DIAMETRE_MAX_CM.toInt()} cm."
        }
    }

    private fun controlerAzimutDistance(azimut: Double, unite: String, distanceM: Double) {
        val max = when (unite) {
            "deg" -> 360.0
            "gr" -> 400.0
            else -> throw IllegalArgumentException("Unité d'azimut inconnue : $unite")
        }
        require(azimut.isFinite() && azimut >= 0.0 && azimut < max) {
            "L'azimut doit être compris entre 0 et $max (exclu) en $unite."
        }
        require(distanceM.isFinite() && distanceM > 0.0 && distanceM <= DISTANCE_MAX_M) {
            "La distance doit être comprise entre 0 (exclu) et ${DISTANCE_MAX_M.toInt()} m."
        }
    }

    /** Sensor azimuth (degrees) to the plot unit, in [0, 360[ or [0, 400[. */
    fun azimutAffiche(azimutTelephoneDeg: Double, unite: String): Double =
        Azimut.versUnite(azimutTelephoneDeg, unite)

    /** Replaces the target with the same id (old one dropped) or adds it; sorted C1, C2, C3. */
    fun placer(existantes: List<CiblePlacee>, nouvelle: CiblePlacee): List<CiblePlacee> =
        (existantes.filter { it.id != nouvelle.id } + nouvelle).sortedBy { it.id }

    /** Distribution warnings; never a refusal. */
    fun alertes(cibles: List<CiblePlacee>): List<AlerteCible> {
        val res = mutableListOf<AlerteCible>()
        val tri = cibles.sortedBy { it.id }
        for (i in tri.indices) for (j in i + 1 until tri.size) {
            if (abs(Azimut.ecart(tri[i].azimutDeg, tri[j].azimutDeg)) < ECART_ANGULAIRE_MIN_DEG) {
                res += AlerteCible(TypeAlerte.ECART_ANGULAIRE, listOf(tri[i].id, tri[j].id).sorted())
            }
        }
        tri.filter { it.distanceM < DISTANCE_MIN_CENTRE_M }.forEach {
            res += AlerteCible(TypeAlerte.TROP_PROCHE_DU_CENTRE, listOf(it.id))
        }
        if (tri.size < 3) res += AlerteCible(TypeAlerte.MOINS_DE_TROIS_CIBLES, emptyList())
        return res
    }
}
