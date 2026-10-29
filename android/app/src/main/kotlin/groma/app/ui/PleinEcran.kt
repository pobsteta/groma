package groma.app.ui

import androidx.compose.foundation.layout.WindowInsets
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
