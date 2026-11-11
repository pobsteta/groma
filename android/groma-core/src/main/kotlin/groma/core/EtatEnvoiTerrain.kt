package groma.core

/**
 * What the plot list says about the "terrain" archive of a plot (mission 021).
 *
 * Every save of an entry (tree sheet, target, centre) stamps `maj` (any increasing number, e.g. the
 * time in ms); a successful upload records the stamp it started from (`envoyeMaj`) and its time
 * (`envoyeLe`, epoch ms). A save made during an upload therefore stays "to send".
 */
sealed interface EtatEnvoiTerrain {
    /** Nothing entered in Groma for this plot since the app keeps this state. */
    data object Rien : EtatEnvoiTerrain

    /** Something entered has not reached the server yet. */
    data object AEnvoyer : EtatEnvoiTerrain

    /** The server has everything; [envoyeLe] is in epoch milliseconds. */
    data class Envoye(val envoyeLe: Long) : EtatEnvoiTerrain

    companion object {
        fun de(maj: Long?, envoyeMaj: Long?, envoyeLe: Long?): EtatEnvoiTerrain = when {
            maj != null && (envoyeMaj == null || maj > envoyeMaj) -> AEnvoyer
            envoyeLe != null -> Envoye(envoyeLe)
            else -> Rien
        }
    }
}
