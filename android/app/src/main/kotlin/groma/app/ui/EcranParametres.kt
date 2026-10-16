package groma.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import groma.app.R

/** Upload settings, saved as they are typed. */
data class Reglages(val serveur: String, val jeton: String, val envoi4g: Boolean)

/** Settings screen, reached from the ⋮ menu (same layout as Marculus). */
@Composable
fun EcranParametres(reglages: Reglages, onChange: (Reglages) -> Unit, onRetour: () -> Unit) {
    Scaffold(
        topBar = {
            BandeauCompact(
                titre = stringResource(R.string.param_titre),
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.param_retour))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.param_envoi_section), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.param_envoi_desc), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = reglages.serveur,
                onValueChange = { onChange(reglages.copy(serveur = it.trim())) },
                label = { Text(stringResource(R.string.serveur)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = reglages.jeton,
                onValueChange = { onChange(reglages.copy(jeton = it.trim())) },
                label = { Text(stringResource(R.string.jeton)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.envoi_4g), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.param_4g_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = reglages.envoi4g, onCheckedChange = { onChange(reglages.copy(envoi4g = it)) })
            }
            SectionRtk()
            SectionAPropos()
        }
    }
}

/** Version installed (from the package, set by release-please), licence, source. */
@Composable
private fun SectionAPropos() {
    val ctx = LocalContext.current
    val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    Text(stringResource(R.string.apropos_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    Text(stringResource(R.string.apropos_version, info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info)))
    Text(stringResource(R.string.apropos_licence), style = MaterialTheme.typography.bodySmall)
}
