package groma.core

import java.util.UUID

/**
 * Mission 036: pure rules of the replayable capture film (ARCore recording, `Pxxx.groma.mp4`).
 * No Android type here, so everything can be tested on the JVM.
 */
object FilmCapture {
    /** Package source written in the manifest: a live capture, or a package computed from a replayed film. */
    const val SOURCE_TERRAIN = "terrain"
    const val SOURCE_REJEU = "rejeu"

    /** Value of the manifest field `source` for a capture, live or replayed. */
    fun source(rejeu: Boolean): String = if (rejeu) SOURCE_REJEU else SOURCE_TERRAIN

    /**
     * Free space required before recording, in bytes. 1 GiB: an estimate to confirm on the S23 (real size per minute
     * of a film is still to measure, mission 036 "Essais"). Below it the film is skipped, never the package.
     */
    const val PLACE_MINIMALE_OCTETS: Long = 1L shl 30

    /** Id of the custom ARCore track holding the identity of the film (fixed, so any reader can find it). */
    val PISTE_IDENTITE: UUID = UUID.fromString("6c3f0a52-5a0c-4a52-9f0e-67726f6d6131")
    const val TYPE_MIME_IDENTITE = "application/json"

    /** The replay is offered only by the smoke build (`fr.nemeton.groma.fumee`), never by the field app. */
    fun rejeuAutorise(nomPaquet: String): Boolean = nomPaquet.endsWith(".fumee")

    sealed interface Decision {
        /** Setting off, or replay in progress: nothing recorded, the capture is exactly as before. */
        data object Aucun : Decision
        data object Enregistrer : Decision
        data class PlaceInsuffisante(val libreOctets: Long, val requisOctets: Long) : Decision
    }

    /** Whether to record. A replay never records (it would film a film). */
    fun decision(reglageActif: Boolean, rejeu: Boolean, placeLibreOctets: Long, requis: Long = PLACE_MINIMALE_OCTETS): Decision = when {
        !reglageActif || rejeu -> Decision.Aucun
        placeLibreOctets < requis -> Decision.PlaceInsuffisante(placeLibreOctets, requis)
        else -> Decision.Enregistrer
    }

    /**
     * File name of the film of a plot: `P065.groma.mp4`, or `P065-<horodatage>.groma.mp4` when it exists, and
     * `-2`, `-3`... after the timestamp if even that one exists. Never an existing name.
     */
    fun nomFilm(plotId: String, existants: Set<String>, horodatage: String): String =
        nomLibre(plotId, ".groma.mp4", existants, horodatage)

    /**
     * File name of a package computed from a replay: `P065.rejeu.groma.zip`, with the same no-overwrite rule.
     * Distinct from `P065.groma.zip`, so a field package is never replaced (rule 5).
     */
    fun nomPaquetRejeu(plotId: String, existants: Set<String>, horodatage: String): String =
        nomLibre("$plotId.rejeu", ".groma.zip", existants, horodatage)

    private fun nomLibre(base: String, suffixe: String, existants: Set<String>, horodatage: String): String {
        val simple = base + suffixe
        if (simple !in existants) return simple
        val date = "$base-$horodatage$suffixe"
        if (date !in existants) return date
        var n = 2
        while ("$base-$horodatage-$n$suffixe" in existants) n++
        return "$base-$horodatage-$n$suffixe"
    }

    /** What the film says about itself, so it can be understood without the package. */
    data class Identite(
        val projetId: String, val plotId: String, val versionAppli: String, val date: String,
        val rayonM: Double, val seuilDiamCm: Double,
    ) {
        /** UTF-8 JSON, written once as a sample of the identity track. */
        fun json(): String =
            """{"format":"groma-film","version":1,"projet_id":${chaine(projetId)},"plot_id":${chaine(plotId)},""" +
                """"version_appli":${chaine(versionAppli)},"date":${chaine(date)},"rayon_m":$rayonM,"seuil_diam_cm":$seuilDiamCm}"""

        private fun chaine(s: String): String = buildString {
            append('"')
            for (c in s) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
            append('"')
        }
    }
}
