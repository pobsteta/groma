package groma.core

import java.util.Base64
import java.util.Locale
import kotlin.math.abs

/** `STR` entry of an NTRIP source table: a mount point offered by the caster. */
data class PointMontage(val nom: String, val identifiant: String, val format: String)

/** Caster answer to an NTRIP request. */
enum class StatutNtrip { OK, TABLE_SOURCES, NON_AUTORISE, INCONNU }

/**
 * NTRIP client protocol, pure and tested (taken from Marculus, same author):
 * request, GGA sent back to the caster (VRS / NEAR mount points pick the
 * nearest base from it), source table and answer status. Network I/O lives
 * in the app.
 */
object Ntrip {
    /** NTRIP 2.0 `GET` request with Basic authentication, ended by an empty line. */
    fun requete(pointMontage: String, hote: String, port: Int, utilisateur: String, motDePasse: String, agent: String = "Groma"): String {
        val mp = if (pointMontage.startsWith("/")) pointMontage else "/$pointMontage"
        val auth = Base64.getEncoder().encodeToString("$utilisateur:$motDePasse".toByteArray(Charsets.UTF_8))
        return "GET $mp HTTP/1.1\r\nHost: $hote:$port\r\nNtrip-Version: Ntrip/2.0\r\nUser-Agent: NTRIP $agent\r\n" +
            "Authorization: Basic $auth\r\n\r\n"
    }

    /** `$GPGGA` giving the rover position to the caster; [heureUtc] is `hhmmss.ss`. */
    fun gga(position: Position, heureUtc: String = "000000.00", qualite: Int = 1, nbSatellites: Int = 10, hdop: Double = 1.0, altitudeM: Double = 0.0): String {
        val (lat, ns) = versNmea(position.latitude, latitude = true)
        val (lon, ew) = versNmea(position.longitude, latitude = false)
        val corps = String.format(Locale.ROOT, "GPGGA,%s,%s,%s,%s,%s,%d,%02d,%.1f,%.1f,M,0.0,M,,", heureUtc, lat, ns, lon, ew, qualite, nbSatellites, hdop, altitudeM)
        return "\$$corps*${Nmea.sommeControle(corps)}"
    }

    /** Mount points (`STR;` lines) of a source table. */
    fun tableSources(texte: String): List<PointMontage> = texte.lineSequence().filter { it.startsWith("STR;") }.mapNotNull { l ->
        val f = l.split(';')
        if (f.size < 4) null else PointMontage(f[1], f[2], f[3])
    }.toList()

    /** Status from the first line of the answer (ICY / HTTP 200, 401, source table). */
    fun statut(premiereLigne: String): StatutNtrip {
        val l = premiereLigne.trim()
        return when {
            l.contains("401") -> StatutNtrip.NON_AUTORISE
            l.startsWith("SOURCETABLE") -> StatutNtrip.TABLE_SOURCES
            l.contains("200") -> StatutNtrip.OK
            else -> StatutNtrip.INCONNU
        }
    }

    private fun versNmea(degres: Double, latitude: Boolean): Pair<String, String> {
        val hemisphere = if (latitude) (if (degres >= 0) "N" else "S") else (if (degres >= 0) "E" else "W")
        val a = abs(degres)
        val d = a.toInt()
        return String.format(Locale.ROOT, "%0${if (latitude) 2 else 3}d%07.4f", d, (a - d) * 60.0) to hemisphere
    }
}
