package groma.core

/**
 * GNSS model (brief 2026-10-14 carte RTK, § 6). Taken from Marculus (same
 * author), adapted to Groma: pure Kotlin, no I/O.
 */

/** Position in WGS 84 degrees. */
data class Position(val latitude: Double, val longitude: Double)

/**
 * Fix quality from the GGA quality indicator. RTK_FIXE = ambiguities solved
 * (centimetres); RTK_FLOTTANT = float solution (decimetres).
 */
enum class QualiteFix(val codeNmea: Int, val libelle: String) {
    AUCUN(0, "Sans position"),
    AUTONOME(1, "Autonome"),
    DGPS(2, "DGPS"),
    PPS(3, "PPS"),
    RTK_FIXE(4, "RTK fixe"),
    RTK_FLOTTANT(5, "RTK flottant"),
    ESTIME(6, "Estimé"),
    MANUEL(7, "Manuel"),
    SIMULATION(8, "Simulation"),
    ;

    /** RTK-corrected solution (fixed or float). */
    val estRtk: Boolean get() = this == RTK_FIXE || this == RTK_FLOTTANT

    companion object {
        /** Quality of a GGA code; AUCUN for any unknown code. */
        fun depuisCode(code: Int): QualiteFix = entries.firstOrNull { it.codeNmea == code } ?: AUCUN

        /** Approximate quality from the horizontal accuracy (m), for the phone's own GNSS. */
        fun depuisPrecision(precisionM: Double?): QualiteFix = when {
            precisionM == null -> AUCUN
            precisionM <= 0.05 -> RTK_FIXE
            precisionM <= 0.5 -> RTK_FLOTTANT
            precisionM <= 3.0 -> DGPS
            else -> AUTONOME
        }
    }
}

/** Origin of a fix: external receiver (NMEA, RTK possible) or the phone's GNSS (never RTK). */
enum class OrigineFix { TELEPHONE, RECEPTEUR }

/** Satellite in view (GSV): number, elevation and azimuth (°), signal to noise (dB-Hz), talker. */
data class SatelliteVu(val prn: Int, val elevation: Int?, val azimut: Int?, val snr: Int?, val systeme: String)

/** One GNSS fix, whatever its origin; absent fields are null. */
data class FixGnss(
    val position: Position,
    val qualite: QualiteFix,
    val nbSatellites: Int,
    val hdop: Double?,
    val altitudeM: Double?,
    val ageCorrectionsS: Double?,
    val precisionHorizontaleM: Double?,
    val pdop: Double? = null,
    val vdop: Double? = null,
    /** Reference station of the corrections (GGA field 14). */
    val stationRef: String? = null,
    /** Course over ground (°, 0 = north, clockwise), from RMC; absent when still. */
    val capDeg: Double? = null,
    /** Speed over ground (m/s), from RMC. */
    val vitesseMs: Double? = null,
    val satellites: List<SatelliteVu> = emptyList(),
    val origine: OrigineFix = OrigineFix.RECEPTEUR,
)
