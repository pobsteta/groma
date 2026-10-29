package groma.app

import android.app.Application
import android.view.Window
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

/**
 * Hides the status and navigation bars (they come back transiently on a swipe from the edge),
 * or shows them again at once. Insets then report 0 for the bars, so the layouts that apply
 * system-bar insets (BandeauCompact, the capture overlay) shrink their margins by themselves;
 * the display cutout inset is unaffected, which is why BandeauCompact and the Scaffold bodies drop it themselves (LocalPleinEcran) apart from the bar buttons.
 */
fun appliquerPleinEcran(window: Window, actif: Boolean) {
    val controleur = WindowCompat.getInsetsController(window, window.decorView)
    if (actif) {
        controleur.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controleur.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controleur.show(WindowInsetsCompat.Type.systemBars())
    }
}
