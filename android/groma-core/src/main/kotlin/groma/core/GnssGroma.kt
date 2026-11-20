package groma.core

import fr.nemeton.terrain.gnss.Ntrip
import fr.nemeton.terrain.gnss.QualiteFix

/** NTRIP requests as Groma sends them: terrain's default agent is "Marculus", the caster must see "Groma". */
object NtripGroma {
    const val AGENT = "Groma"

    fun requete(pointMontage: String, hote: String, port: Int, utilisateur: String, motDePasse: String): String =
        Ntrip.requete(pointMontage, hote, port, utilisateur, motDePasse, agent = AGENT)
}

/** The ONE place for the fix-quality texts shown to the user (identical to those before terrain-gnss; terrain's differ). */
object LibellesGnss {
    fun qualite(q: QualiteFix): String = when (q) {
        QualiteFix.INVALIDE -> "Sans position"
        QualiteFix.AUTONOME -> "Autonome"
        QualiteFix.DGPS -> "DGPS"
        QualiteFix.PPS -> "PPS"
        QualiteFix.RTK_FIXE -> "RTK fixe"
        QualiteFix.RTK_FLOAT -> "RTK flottant"
        QualiteFix.ESTIME -> "Estimé"
        QualiteFix.MANUEL -> "Manuel"
        QualiteFix.SIMULATION -> "Simulation"
    }
}
