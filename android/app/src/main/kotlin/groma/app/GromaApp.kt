package groma.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import groma.core.ChoixTheme

/** Applies the saved theme before any screen exists, so every activity and dialog follows it. */
class GromaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appliquerTheme(Preferences.reglages(this).theme)
    }
}

/**
 * Sets the process-wide night mode (AppCompat recreates the open activities once if it changes).
 * Compose's isSystemInDarkTheme() reads the same configuration, so GromaTheme stays consistent.
 */
fun appliquerTheme(choix: ChoixTheme) {
    AppCompatDelegate.setDefaultNightMode(
        when (choix.nuitForcee) {
            null -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            false -> AppCompatDelegate.MODE_NIGHT_NO
            true -> AppCompatDelegate.MODE_NIGHT_YES
        },
    )
}
