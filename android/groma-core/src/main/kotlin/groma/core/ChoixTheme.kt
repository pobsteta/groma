package groma.core

/** Theme choice: follow the phone, or force light or dark. */
enum class ChoixTheme {
    SYSTEME, CLAIR, SOMBRE;

    /** True if the app must be dark; [systemeSombre] is the phone's state. */
    fun estSombre(systemeSombre: Boolean): Boolean = when (this) {
        SYSTEME -> systemeSombre
        CLAIR -> false
        SOMBRE -> true
    }
}

/** Reads back a saved theme name; absent or unknown (case-sensitive) gives SYSTEME. */
fun choixThemeDepuis(nom: String?): ChoixTheme = ChoixTheme.entries.firstOrNull { it.name == nom } ?: ChoixTheme.SYSTEME
