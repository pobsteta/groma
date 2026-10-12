package groma.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Groma numbering rule. Normative description: fixtures/numerotation/README.md.
 * Must give exactly the same result as groma/numerotation.py and the R version.
 */
data class ArbreANumeroter(val id: String, val x: Double, val y: Double, val dbhCm: Double)

data class ParametresNumerotation(
    val rayonM: Double = 20.0,
    val seuilDiamCm: Double = 7.5,
    val uniteAzimut: String = "deg",
    val toleranceDeg: Double = 1.0,
)

data class ResultatNumerotation(
    val numeros: Map<String, String>,
    val exclus: Map<String, String>,
    val visibles: Set<String>,
    val azimuts: Map<String, Double>,
    val distances: Map<String, Double>,
)

object Numerotation {
    private const val EPS = 1e-9

    /** Rotate a Lambert-93 grid offset into the geographic-north frame. */
    fun offsetsGeographiques(dx: Double, dy: Double, convergenceDeg: Double): Pair<Double, Double> {
        val g = Math.toRadians(convergenceDeg)
        val c = cos(g)
        val s = sin(g)
        return Pair(dx * c + dy * s, -dx * s + dy * c)
    }

    private fun distancePointSegment(px: Double, py: Double, bx: Double, by: Double): Double {
        val l2 = bx * bx + by * by
        if (l2 == 0.0) return hypot(px, py)
        val t = ((px * bx + py * by) / l2).coerceIn(0.0, 1.0)
        return hypot(px - t * bx, py - t * by)
    }

    fun estMasque(cible: ArbreANumeroter, autres: List<ArbreANumeroter>): Boolean {
        val dCible = hypot(cible.x, cible.y)
        for (a in autres) {
            if (a.id == cible.id) continue
            val dA = hypot(a.x, a.y)
            val rayonA = a.dbhCm / 200.0
            if (dA >= dCible || dA <= rayonA) continue
            if (distancePointSegment(a.x, a.y, cible.x, cible.y) < rayonA) return true
        }
        return false
    }

    /** Byte-wise UTF-8 comparison, identical to Python str ordering for these ids. */
    private val ordreId = Comparator<String> { a, b ->
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        for (i in 0 until minOf(x.size, y.size)) {
            val c = (x[i].toInt() and 0xFF) - (y[i].toInt() and 0xFF)
            if (c != 0) return@Comparator c
        }
        x.size - y.size
    }

    fun numeroter(arbres: List<ArbreANumeroter>, p: ParametresNumerotation = ParametresNumerotation()): ResultatNumerotation {
        require(arbres.map { it.id }.toSet().size == arbres.size) { "Identifiants d'arbres en double." }
        val az = HashMap<String, Double>()
        val dist = HashMap<String, Double>()
        val exclus = LinkedHashMap<String, String>()
        val eligibles = ArrayList<ArbreANumeroter>()
        for (a in arbres) {
            val d = hypot(a.x, a.y)
            dist[a.id] = d
            az[a.id] = Azimut.azimut(a.x, a.y)
            when {
                d > p.rayonM + EPS -> exclus[a.id] = "hors_rayon"
                a.dbhCm < p.seuilDiamCm - EPS -> exclus[a.id] = "sous_seuil"
                else -> eligibles.add(a)
            }
        }
        val azimuts = arbres.associate { it.id to Azimut.versUnite(az.getValue(it.id), p.uniteAzimut) }
        if (eligibles.isEmpty()) return ResultatNumerotation(emptyMap(), exclus, emptySet(), azimuts, dist)

        val visibles = eligibles.filter { !estMasque(it, eligibles) }
        val tol = p.toleranceDeg
        val azMin = visibles.minOf { az.getValue(it.id) }
        val candidats = visibles.filter { az.getValue(it.id) - azMin < tol }
        val premier = candidats.sortedWith(
            compareBy<ArbreANumeroter>({ dist.getValue(it.id) }, { az.getValue(it.id) }).thenBy(ordreId) { it.id }
        ).first()
        val idsCandidats = candidats.map { it.id }.toSet()
        val az1 = az.getValue(premier.id)
        fun rel(a: ArbreANumeroter) = if (a.id in idsCandidats) 0.0 else Azimut.normaliser(az.getValue(a.id) - az1)

        val autres = eligibles.filter { it.id != premier.id }.sortedWith(
            compareBy<ArbreANumeroter>({ rel(it) }, { dist.getValue(it.id) }).thenBy(ordreId) { it.id }
        )
        val ordre = arrayListOf(premier)
        var i = 0
        while (i < autres.size) {
            val ancre = rel(autres[i])
            var j = i
            while (j < autres.size && rel(autres[j]) - ancre < tol) j++
            ordre += autres.subList(i, j).sortedWith(
                compareBy<ArbreANumeroter>({ dist.getValue(it.id) }, { rel(it) }).thenBy(ordreId) { it.id }
            )
            i = j
        }
        val numeros = LinkedHashMap<String, String>()
        ordre.forEachIndexed { k, a -> numeros[a.id] = (k + 1).toString() }
        return ResultatNumerotation(numeros, exclus, visibles.map { it.id }.toSet(), azimuts, dist)
    }
}
