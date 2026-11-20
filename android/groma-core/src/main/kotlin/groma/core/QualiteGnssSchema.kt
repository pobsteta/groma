package groma.core

import fr.nemeton.terrain.gnss.OrigineFix
import fr.nemeton.terrain.gnss.QualiteFix

/**
 * The ONE place translating terrain's GNSS types to the `qualite_gnss` values of the GeoPackage
 * (schema domain: "fix", "float", "telephone"; mission 035, voie A). nemeton reads these values, so
 * they never follow the enum names of terrain. Both directions are tested.
 */
object QualiteGnssSchema {
    const val FIX = "fix"
    const val FLOAT = "float"
    const val TELEPHONE = "telephone"

    /** Value written for a centre measured from [origine] with fixes of quality [qualites]. */
    fun versSchema(origine: OrigineFix, qualites: Collection<QualiteFix>): String = when {
        origine == OrigineFix.INTERNE -> TELEPHONE
        qualites.all { it == QualiteFix.RTK_FIXE } -> FIX
        else -> FLOAT
    }

    /** Quality of a value read from a file; null for "telephone" (no RTK quality) and unknown values. */
    fun qualiteDepuisSchema(valeur: String?): QualiteFix? = when (valeur) {
        FIX -> QualiteFix.RTK_FIXE
        FLOAT -> QualiteFix.RTK_FLOAT
        else -> null
    }

    /** Origin of a value read from a file; null when the value is unknown. */
    fun origineDepuisSchema(valeur: String?): OrigineFix? = when (valeur) {
        FIX, FLOAT -> OrigineFix.EXTERNE
        TELEPHONE -> OrigineFix.INTERNE
        else -> null
    }
}
