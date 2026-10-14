package groma.core

import kotlin.math.sqrt

/**
 * NMEA 0183 from an external GNSS receiver (u-blox ZED-F9P…): sentence
 * splitting and decoding of GGA, GST, GSA, RMC and GSV. Taken from Marculus
 * (same author); the talker (GP, GN, GL…) is ignored. Pure, no I/O.
 */

/** Decoded GGA: position, fix quality and quality metadata. */
data class TrameGga(
    val position: Position,
    val qualite: QualiteFix,
    val nbSatellites: Int,
    val hdop: Double?,
    val altitudeM: Double?,
    val ageCorrectionsS: Double?,
    val stationRef: String? = null,
)

/** Decoded GSA: dilutions of precision. */
data class TrameGsa(val pdop: Double?, val hdop: Double?, val vdop: Double?)

/** Decoded RMC: course and speed over ground; course absent when still. */
data class TrameRmc(val capDeg: Double?, val vitesseMs: Double?)

/** One GSV message of a sequence: satellites in view of one system. */
data class TrameGsv(val systeme: String, val nbMessages: Int, val numMessage: Int, val satellitesEnVue: Int, val satellites: List<SatelliteVu>)

/** Decoded GST: position standard deviations (m). */
data class TrameGst(val ecartTypeLatM: Double, val ecartTypeLonM: Double) {
    /** Horizontal accuracy (m): norm of the latitude and longitude standard deviations. */
    val precisionHorizontaleM: Double get() = sqrt(ecartTypeLatM * ecartTypeLatM + ecartTypeLonM * ecartTypeLonM)
}

/**
 * Rebuilds whole NMEA sentences from a stream received in pieces (Bluetooth
 * or TCP reads). A `$` restarts a sentence; a line longer than [tailleMax]
 * without an end of line is dropped (binary or corrupt stream).
 */
class NmeaDecoupeur(private val tailleMax: Int = 1024) {
    private val tampon = StringBuilder()

    /** Adds a piece and returns the sentences it ends, without line ends. */
    fun pousser(morceau: String): List<String> {
        val trames = mutableListOf<String>()
        for (c in morceau) {
            when (c) {
                '\n', '\r' -> if (tampon.isNotEmpty()) { trames += tampon.toString(); tampon.setLength(0) }
                '$' -> { tampon.setLength(0); tampon.append('$') }
                else -> { tampon.append(c); if (tampon.length > tailleMax) tampon.setLength(0) }
            }
        }
        return trames
    }
}

object Nmea {
    private const val NOEUD_EN_MS = 0.514444

    /** NMEA checksum (XOR of the body, without `$` and `*hh`), two hex digits. */
    fun sommeControle(corps: String): String {
        var c = 0
        for (ch in corps) c = c xor ch.code
        return "%02X".format(c)
    }

    fun checksumValide(phrase: String): Boolean {
        val debut = phrase.indexOf('$')
        val etoile = phrase.lastIndexOf('*')
        if (debut < 0 || etoile < debut + 1 || etoile + 3 > phrase.length) return false
        return sommeControle(phrase.substring(debut + 1, etoile)).equals(phrase.substring(etoile + 1, etoile + 3), ignoreCase = true)
    }

    private fun champs(phrase: String, type: String): List<String>? {
        if (!checksumValide(phrase)) return null
        val f = phrase.substring(phrase.indexOf('$') + 1, phrase.lastIndexOf('*')).split(',')
        return f.takeIf { it.isNotEmpty() && it[0].endsWith(type) }
    }

    /** GGA with a position, else null. */
    fun gga(phrase: String): TrameGga? {
        val f = champs(phrase, "GGA") ?: return null
        val lat = coordonnee(f.getOrNull(2), f.getOrNull(3)) ?: return null
        val lon = coordonnee(f.getOrNull(4), f.getOrNull(5)) ?: return null
        return TrameGga(
            Position(lat, lon),
            QualiteFix.depuisCode(f.getOrNull(6)?.toIntOrNull() ?: 0),
            f.getOrNull(7)?.toIntOrNull() ?: 0,
            f.getOrNull(8)?.toDoubleOrNull(),
            f.getOrNull(9)?.toDoubleOrNull(),
            f.getOrNull(13)?.toDoubleOrNull(),
            f.getOrNull(14)?.takeIf { it.isNotBlank() },
        )
    }

    fun gst(phrase: String): TrameGst? {
        val f = champs(phrase, "GST") ?: return null
        return TrameGst(f.getOrNull(6)?.toDoubleOrNull() ?: return null, f.getOrNull(7)?.toDoubleOrNull() ?: return null)
    }

    fun gsa(phrase: String): TrameGsa? {
        val f = champs(phrase, "GSA") ?: return null
        return TrameGsa(f.getOrNull(15)?.toDoubleOrNull(), f.getOrNull(16)?.toDoubleOrNull(), f.getOrNull(17)?.toDoubleOrNull())
    }

    /** RMC: field 7 speed over ground (knots), field 8 course over ground (° true). */
    fun rmc(phrase: String): TrameRmc? {
        val f = champs(phrase, "RMC") ?: return null
        return TrameRmc(f.getOrNull(8)?.toDoubleOrNull(), f.getOrNull(7)?.toDoubleOrNull()?.let { it * NOEUD_EN_MS })
    }

    fun gsv(phrase: String): TrameGsv? {
        val f = champs(phrase, "GSV") ?: return null
        val systeme = f[0].dropLast(3)
        val nb = f.getOrNull(1)?.toIntOrNull() ?: return null
        val num = f.getOrNull(2)?.toIntOrNull() ?: return null
        val sats = mutableListOf<SatelliteVu>()
        var i = 4
        while (i < f.size) { // groups of 4 (PRN, elevation, azimuth, SNR); last SNR sometimes missing
            val prn = f.getOrNull(i)?.toIntOrNull() ?: break
            sats += SatelliteVu(prn, f.getOrNull(i + 1)?.toIntOrNull(), f.getOrNull(i + 2)?.toIntOrNull(), f.getOrNull(i + 3)?.toIntOrNull(), systeme)
            i += 4
        }
        return TrameGsv(systeme, nb, num, f.getOrNull(3)?.toIntOrNull() ?: 0, sats)
    }

    /** Whole fix from a GGA and the latest GST, GSA and RMC. */
    fun fix(gga: TrameGga, gst: TrameGst? = null, gsa: TrameGsa? = null, rmc: TrameRmc? = null, satellites: List<SatelliteVu> = emptyList()) = FixGnss(
        position = gga.position, qualite = gga.qualite, nbSatellites = gga.nbSatellites, hdop = gga.hdop ?: gsa?.hdop,
        altitudeM = gga.altitudeM, ageCorrectionsS = gga.ageCorrectionsS, precisionHorizontaleM = gst?.precisionHorizontaleM,
        pdop = gsa?.pdop, vdop = gsa?.vdop, stationRef = gga.stationRef, capDeg = rmc?.capDeg, vitesseMs = rmc?.vitesseMs,
        satellites = satellites,
    )

    /** NMEA `ddmm.mmmm` + hemisphere → signed decimal degrees. */
    private fun coordonnee(valeur: String?, hemisphere: String?): Double? {
        val v = valeur?.toDoubleOrNull() ?: return null
        if (hemisphere.isNullOrBlank()) return null
        val deg = (v / 100).toInt()
        val dec = deg + (v - deg * 100) / 60.0
        return when (hemisphere.uppercase()) {
            "N", "E" -> dec
            "S", "W" -> -dec
            else -> null
        }
    }
}

/**
 * Turns the NMEA stream of a receiver into fixes: each GGA is completed by
 * the latest GST (accuracy), GSA (dilutions), RMC (course, speed) and the
 * last complete GSV sequences (sky plot).
 */
class LecteurNmea {
    private val decoupeur = NmeaDecoupeur()
    private var gst: TrameGst? = null
    private var gsa: TrameGsa? = null
    private var rmc: TrameRmc? = null
    private val enCours = HashMap<String, MutableList<SatelliteVu>>()
    private val complets = HashMap<String, List<SatelliteVu>>()

    /** Last GGA sentence as received, sent back to the NTRIP caster (VRS / NEAR). */
    var derniereGga: String? = null
        private set

    /** Feeds received text; returns the sentences it completes and the fixes they give. */
    fun pousser(texte: String): Pair<List<String>, List<FixGnss>> {
        val trames = decoupeur.pousser(texte)
        val fixes = mutableListOf<FixGnss>()
        for (t in trames) {
            Nmea.gst(t)?.let { gst = it }
            Nmea.gsa(t)?.let { gsa = it }
            Nmea.rmc(t)?.let { rmc = it }
            Nmea.gsv(t)?.let { g ->
                val tampon = if (g.numMessage <= 1) mutableListOf<SatelliteVu>().also { enCours[g.systeme] = it } else enCours.getOrPut(g.systeme) { mutableListOf() }
                tampon += g.satellites
                if (g.numMessage >= g.nbMessages) complets[g.systeme] = tampon.toList()
            }
            Nmea.gga(t)?.let { g ->
                derniereGga = t
                fixes += Nmea.fix(g, gst, gsa, rmc, complets.values.flatten())
            }
        }
        return trames to fixes
    }
}
