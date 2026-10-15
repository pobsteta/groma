package groma.core

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * « Mesurer le centre » (brief 2026-10-14 carte RTK, § 5.4): average of the
 * receiver's RTK fixed fixes during a few seconds; float fixes only when the
 * operator confirmed it. The phone's own GNSS is never accepted.
 */
class MesureCentre(val accepterFlottant: Boolean) {
    private val fixes = mutableListOf<FixGnss>()

    val nombre: Int get() = fixes.size

    /** Adds [f] when its quality is accepted; returns whether it was. */
    fun ajouter(f: FixGnss): Boolean {
        if (!accepte(f, accepterFlottant)) return false
        fixes += f
        return true
    }

    /** Averaged centre, null without any accepted fix. */
    fun resultat(): CentreMesure? {
        if (fixes.isEmpty()) return null
        val lat = fixes.sumOf { it.position.latitude } / fixes.size
        val lon = fixes.sumOf { it.position.longitude } / fixes.size
        val moyenne = Position(lat, lon)
        // Spread of the fixes around their mean (RMS), and announced accuracy (RMS):
        // the larger of the two, so a jumping solution is not reported as 2 cm.
        val dispersion = sqrt(fixes.sumOf { Geodesie.distanceM(moyenne, it.position).let { d -> d * d } } / fixes.size)
        val annoncees = fixes.mapNotNull { it.precisionHorizontaleM }
        val annoncee = if (annoncees.isEmpty()) 0.0 else sqrt(annoncees.sumOf { it * it } / annoncees.size)
        val qualite = if (fixes.all { it.qualite == QualiteFix.RTK_FIXE }) "fix" else "float"
        return CentreMesure(lat, lon, qualite, maxOf(dispersion, annoncee), fixes.size)
    }

    companion object {
        fun accepte(f: FixGnss, accepterFlottant: Boolean): Boolean =
            f.origine == OrigineFix.RECEPTEUR &&
                (f.qualite == QualiteFix.RTK_FIXE || (accepterFlottant && f.qualite == QualiteFix.RTK_FLOTTANT))

        /** What tapping « Mesurer le centre » leads to with the current fix. */
        fun autorisation(f: FixGnss?): Autorisation = when {
            f == null || f.qualite == QualiteFix.AUCUN -> Autorisation.SANS_POSITION
            f.origine == OrigineFix.TELEPHONE -> Autorisation.TELEPHONE
            f.qualite == QualiteFix.RTK_FIXE -> Autorisation.OUI
            f.qualite == QualiteFix.RTK_FLOTTANT -> Autorisation.CONFIRMER_FLOTTANT
            else -> Autorisation.SANS_RTK
        }
    }

    enum class Autorisation { OUI, CONFIRMER_FLOTTANT, SANS_RTK, TELEPHONE, SANS_POSITION }
}

/** Measured centre in WGS 84; [qualite] in the `qualite_gnss` domain ("fix", "float"). */
data class CentreMesure(val latitude: Double, val longitude: Double, val qualite: String, val precisionM: Double, val nbFixes: Int) {
    /** Lambert 93 (x, y). */
    val lambert93: Pair<Double, Double> get() = Projections.versLambert93(longitude, latitude)

    /** Distance (m) to the planned point (x, y) in Lambert 93. */
    fun ecartPrevuM(xPrevu: Double, yPrevu: Double): Double = lambert93.let { (x, y) -> hypot(x - xPrevu, y - yPrevu) }
}
