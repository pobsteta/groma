package groma.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * State of a plot's centre C0 as shown in the « Placette » view and the plot sheet (mission 029).
 * Pure functions, so the wording and the 30 m threshold are testable on the JVM.
 */
object EtatCentre {
    /** Beyond this distance to the planned point, measuring the centre asks for a (non blocking) confirmation. */
    const val SEUIL_AVERTISSEMENT_M = 30.0

    fun eloigne(distanceM: Double?): Boolean = distanceM != null && distanceM > SEUIL_AVERTISSEMENT_M

    /** Plain-language quality: `fix`, `float`, `telephone`; anything else is shown as is. */
    fun libelleQualite(qualite: String?): String = when (qualite) {
        "fix" -> "RTK fixe"
        "float" -> "RTK flottant"
        "telephone" -> "GNSS du téléphone"
        null, "" -> "qualité inconnue"
        else -> qualite
    }

    /** « 12/11 à 10:05 » in [zone] (the phone's local zone); null when [iso] is missing or unreadable. */
    fun dateLocale(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
        val instant = instantDe(iso) ?: return null
        return DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm", Locale.FRANCE).format(instant.atZone(zone))
    }

    private fun instantDe(iso: String?): Instant? {
        val s = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(s) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
            // No offset (written by another tool): taken as UTC, like the phone's own writes.
            ?: runCatching { LocalDateTime.parse(s.replace(' ', 'T')).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    /** « Centre C0 : RTK fixe, précision 0,02 m, mesuré le 12/11 à 10:05 »; « Centre C0 non mesuré » when [mesure] is false. */
    fun ligne(mesure: Boolean, qualite: String?, precisionM: Double?, dateIso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (!mesure) return "Centre C0 non mesuré"
        val parties = listOfNotNull(
            libelleQualite(qualite),
            precisionM?.let { "précision " + String.format(Locale.FRANCE, "%.2f", it) + " m" },
            dateLocale(dateIso, zone)?.let { "mesuré le $it" },
        )
        return "Centre C0 : " + parties.joinToString(", ")
    }
}
