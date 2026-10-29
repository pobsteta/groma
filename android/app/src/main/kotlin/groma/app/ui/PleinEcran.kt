package groma.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * True when the "full screen" setting is on. In that mode the content (lists, screens, title)
 * goes right to the screen edge, ignoring the display cutout (camera hole); only the action
 * buttons of [BandeauCompact] keep the cutout margin so none sits under the camera.
 */
val LocalPleinEcran = compositionLocalOf { false }

/** Insets for the body of a Scaffold: the default ones (system bars + cutout), minus the cutout in full screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun margesContenu(): WindowInsets =
    if (LocalPleinEcran.current) WindowInsets.systemBars else ScaffoldDefaults.contentWindowInsets

/**
 * Keeps a button clear of the camera cutout on the given [cote] (Start or End) in full screen;
 * no-op otherwise (the Scaffold insets already include the cutout then).
 */
@Composable
fun Modifier.margeBouton(cote: WindowInsetsSides): Modifier =
    if (LocalPleinEcran.current) windowInsetsPadding(WindowInsets.displayCutout.only(cote)) else this
