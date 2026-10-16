package groma.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import groma.app.R

/** ⋮ → « À propos » of the projects and plots screens: installed version and build, licence, source. */
@Composable
fun DialogueAPropos(version: String, onFermer: () -> Unit) {
    val ctx = LocalContext.current
    val build = PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(stringResource(R.string.apropos_titre, version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.apropos_build, version, build), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.apropos_texte))
            }
        },
        confirmButton = { TextButton(onClick = onFermer) { Text(stringResource(R.string.fermer)) } },
    )
}
