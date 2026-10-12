package groma.app

import groma.core.CibleMesuree
import groma.core.Detection
import groma.core.GeoPackage
import groma.core.Georeferencement
import groma.core.Grille
import groma.core.Houppier
import groma.core.ParametresPlacette
import groma.core.Placette
import groma.core.Rayon
import groma.core.TroncGeoreference
import groma.core.Troncs
import kotlin.math.floor

/**
 * Provisional on-phone computation (part A): ARCore depth -> point cloud ->
 * ground (DTM when present) -> 1.20-1.40 m slice -> circles -> georeferencing
 * on C0-C3 -> numbering, crown heights -> `detections` (etat provisoire).
 * Target: under 2 minutes after the capture.
 */
class CalculEmbarque(private val voxelM: Double = 0.02) {
    private val voxels = HashMap<Long, DoubleArray>() // key -> (sum x, sum y, sum z, n)
    private val rayonsCibles = HashMap<String, MutableList<Rayon>>()

    private fun cle(x: Double, y: Double, z: Double): Long {
        val i = floor(x / voxelM).toLong() and 0x1FFFFF
        val j = floor(y / voxelM).toLong() and 0x1FFFFF
        val k = floor(z / voxelM).toLong() and 0x1FFFFF
        return (i shl 42) or (j shl 21) or k
    }

    /** Points already in the local frame (z up). */
    fun ajouterPoint(x: Double, y: Double, z: Double) {
        val v = voxels.getOrPut(cle(x, y, z)) { DoubleArray(4) }
        v[0] += x; v[1] += y; v[2] += z; v[3] += 1.0
    }

    fun ajouterRayonCible(idCible: String, r: Rayon) { rayonsCibles.getOrPut(idCible) { ArrayList() } += r }

    data class Resultat(val detections: List<Detection>, val message: String?)

    fun calculer(
        p: ParametresPlacette, mesures: List<CibleMesuree>, houppiers: List<Houppier>, mnt: Grille?,
        precedentes: List<Detection>,
    ): Resultat {
        val cibles = rayonsCibles.filterValues { it.size >= 3 }.mapValues { Georeferencement.trianguler(it.value) }
        val c0 = cibles["C0"] ?: return Resultat(emptyList(), "Repère central C0 non vu : impossible de placer les arbres.")
        val autres = cibles.filterKeys { it != "C0" }.mapValues { Pair(it.value[0], it.value[1]) }
        if (autres.isEmpty()) return Resultat(emptyList(), "Aucune cible C1-C3 vue : orientation impossible.")
        val geo = Georeferencement.georeferencer(Pair(c0[0], c0[1]), autres, mesures, Pair(p.centreX, p.centreY),
            p.uniteAzimut, p.declinaisonDeg, p.convergenceDeg)

        val pts = voxels.values.map { doubleArrayOf(it[0] / it[3], it[1] / it[3], it[2] / it[3]) }
        val sol = modeleSol(pts, geo, mnt)
        val (sx, sy) = Troncs.extraireTranche(
            DoubleArray(pts.size) { pts[it][0] }, DoubleArray(pts.size) { pts[it][1] }, DoubleArray(pts.size) { pts[it][2] }, sol)
        // ARCore depth is noisy (1-2 % of the range): looser RANSAC than on the server.
        val troncs = Troncs.detecter(sx, sy, epsM = 0.06, minPoints = 6, seuilRansacM = 0.03).map {
            val (gx, gy) = geo.versGrille(it.x, it.y)
            TroncGeoreference(gx, gy, it.dbhCm, it.score)
        }
        val avecUuid = Placette.reutiliserUuid(precedentes, troncs)
        val det = Placette.construireDetections(avecUuid, p, houppiers, methode = "embarque", etat = "provisoire")
        val rmse = Georeferencement.rmse(geo.residusAzimutDeg.values)
        return Resultat(det, if (rmse > 3.0) "Résidu d'orientation élevé (%.1f°) : vérifier les azimuts des cibles.".format(rmse) else null)
    }

    /** Ground height in the local frame: DTM shifted on the lowest points, else the lowest points themselves. */
    private fun modeleSol(pts: List<DoubleArray>, geo: groma.core.Georef, mnt: Grille?): (Double, Double) -> Double {
        val bas = HashMap<Long, DoubleArray>()
        for (q in pts) {
            val k = (floor(q[0]).toLong() shl 32) or (floor(q[1]).toLong() and 0xffffffffL)
            val b = bas[k]
            if (b == null || q[2] < b[2]) bas[k] = q
        }
        if (mnt != null) {
            val ecarts = bas.values.mapNotNull { q ->
                val (gx, gy) = geo.versGrille(q[0], q[1])
                mnt.echantillonner(gx, gy).takeIf { !it.isNaN() }?.minus(q[2])
            }.sorted()
            if (ecarts.size >= 10) {
                val decalage = ecarts[ecarts.size / 2]
                return { x, y -> val (gx, gy) = geo.versGrille(x, y); mnt.echantillonner(gx, gy) - decalage }
            }
        }
        return { x, y ->
            val k = (floor(x).toLong() shl 32) or (floor(y).toLong() and 0xffffffffL)
            bas[k]?.get(2) ?: Double.NaN
        }
    }

    companion object {
        fun ecrire(projet: Projet, plotId: String, det: List<Detection>) = projet.ouvrir().use { db ->
            val s = SqliteAndroid(db)
            GeoPackage.ecrireDetections(s, det)
            GeoPackage.mettreAJourEtat(s, plotId, "provisoire")
        }
    }
}
