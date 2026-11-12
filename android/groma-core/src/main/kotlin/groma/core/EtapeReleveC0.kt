package groma.core

/**
 * Decision shared by every screen that offers « Relever C0 » (map sheet, arrival banner, « Placette » view,
 * mission 030): what comes before the survey itself, given the centre the plot may already have.
 */
object EtapeReleveC0 {
    enum class Avant {
        /** No earlier centre: start the survey. */
        RELEVER,

        /** An earlier centre exists: say so, it stays in the file and the new one will count. */
        DEJA,

        /** The earlier centre is RTK and the new one comes from the phone: confirm the replacement. */
        REMPLACER_RTK,
    }

    fun avant(ancienExiste: Boolean, ancienneQualite: String?, telephone: Boolean): Avant = when {
        !ancienExiste -> Avant.RELEVER
        telephone && MesureCentre.remplacementAConfirmer(ancienneQualite, "telephone") -> Avant.REMPLACER_RTK
        else -> Avant.DEJA
    }
}
