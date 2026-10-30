package groma.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

// FROZEN REFERENCE (mission 013): verbatim copy of the map code that Groma had in groma-core
// (ZoneTuiles.kt, and Emprise of Carte.kt) before it was replaced by terrain-carte v0.7.1.
// Only the class names carry the "ReferenceGroma" prefix. Do not edit: it is what
// EquivalenceTerrainCarteTest compares terrain-carte against.

/** Bounding box in WGS 84 degrees. */
data class ReferenceGromaEmprise(val ouest: Double, val sud: Double, val est: Double, val nord: Double)

/** One Web Mercator tile (XYZ scheme, as osmdroid and the IGN `PM_0_19` matrix set). */
data class ReferenceGromaTuile(val z: Int, val x: Int, val y: Int)

/**
 * Tiles to download for working offline (brief 2026-10-14 carte RTK, § 5.2):
 * the project's extent plus a margin, from [zoomMin] to [zoomMax].
 */
class ReferenceGromaZoneTuiles(val emprise: ReferenceGromaEmprise, val zoomMin: Int = ZOOM_MIN, val zoomMax: Int = ZOOM_MAX) {
    init { require(zoomMin in 0..zoomMax) { "Zooms invalides." } }

    /** Column and row ranges at zoom [z]. */
    fun plage(z: Int): Pair<IntRange, IntRange> {
        val (x0, y0) = tuile(emprise.ouest, emprise.nord, z)
        val (x1, y1) = tuile(emprise.est, emprise.sud, z)
        return (x0..x1) to (y0..y1)
    }

    val nombre: Long get() = (zoomMin..zoomMax).sumOf { z -> plage(z).let { (xs, ys) -> xs.count().toLong() * ys.count() } }

    /** Every tile, zoom by zoom (low zooms first: a useful map early). */
    fun tuiles(): Sequence<ReferenceGromaTuile> = sequence {
        for (z in zoomMin..zoomMax) {
            val (xs, ys) = plage(z)
            for (x in xs) for (y in ys) yield(ReferenceGromaTuile(z, x, y))
        }
    }

    companion object {
        const val ZOOM_MIN = 12
        const val ZOOM_MAX = 18
        const val MARGE_M = 200.0

        /** Beyond this, the download is refused (Géoplateforme fair use; a forest, not a département). */
        const val NOMBRE_MAX = 30_000L

        /** Zone of a project: its extent widened by [margeM] metres on each side. */
        fun projet(e: ReferenceGromaEmprise, margeM: Double = MARGE_M): ReferenceGromaZoneTuiles {
            val dLat = margeM / M_PAR_DEGRE
            val dLon = margeM / (M_PAR_DEGRE * cos(Math.toRadians((e.sud + e.nord) / 2)))
            return ReferenceGromaZoneTuiles(ReferenceGromaEmprise(e.ouest - dLon, e.sud - dLat, e.est + dLon, e.nord + dLat))
        }

        /** Tile (x, y) holding (lon, lat) at zoom [z]. */
        fun tuile(lon: Double, lat: Double, z: Int): Pair<Int, Int> {
            val n = 1 shl z
            val phi = Math.toRadians(lat.coerceIn(-LAT_MAX, LAT_MAX))
            val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
            val y = floor((1.0 - ln(tan(phi) + 1.0 / cos(phi)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
            return x to y
        }

        private const val M_PAR_DEGRE = 111_320.0
        private const val LAT_MAX = 85.05112878
    }
}
