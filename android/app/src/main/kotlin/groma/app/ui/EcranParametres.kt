package groma.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import groma.app.R
import groma.core.ChoixTheme
import kotlinx.coroutines.launch

/** Upload settings, saved as they are typed. */
data class Reglages(val serveur: String, val jeton: String, val envoi4g: Boolean,
    val theme: ChoixTheme = ChoixTheme.SYSTEME, val pleinEcran: Boolean = false)

/** Settings screen, reached from the ⋮ menu (same layout as Marculus). */
@Composable
fun EcranParametres(reglages: Reglages, onChange: (Reglages) -> Unit, onRetour: () -> Unit) {
    // The token is long and masked: show it on demand, and paste it whole.
    var jetonVisible by rememberSaveable { mutableStateOf(false) }
    val pressePapiers = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Scaffold(
        contentWindowInsets = margesContenu(),
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
                visualTransformation = if (jetonVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    scope.launch {
                        val texte = pressePapiers.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                        if (!texte.isNullOrBlank()) onChange(reglages.copy(jeton = texte.toString().trim()))
                    }
                }) { Text(stringResource(R.string.jeton_coller)) }
                TextButton(onClick = { jetonVisible = !jetonVisible }) {
                    Text(stringResource(if (jetonVisible) R.string.jeton_masquer else R.string.jeton_afficher))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.envoi_4g), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.param_4g_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = reglages.envoi4g, onCheckedChange = { onChange(reglages.copy(envoi4g = it)) })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.param_plein_titre), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.param_plein_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = reglages.pleinEcran, onCheckedChange = { onChange(reglages.copy(pleinEcran = it)) })
            }
            ChoixThemeReglage(reglages.theme) { onChange(reglages.copy(theme = it)) }
            SectionRtk()
        }
    }
}

/** Theme choice: follow the phone, or force light or dark (drop-down list, as in Marculus). */
@Composable
private fun ChoixThemeReglage(valeur: ChoixTheme, onChange: (ChoixTheme) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    @Composable
    fun libelle(choix: ChoixTheme) = stringResource(
        when (choix) {
            ChoixTheme.SYSTEME -> R.string.param_theme_systeme
            ChoixTheme.CLAIR -> R.string.param_theme_clair
            ChoixTheme.SOMBRE -> R.string.param_theme_sombre
        },
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(stringResource(R.string.param_theme_titre), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.param_theme_desc), style = MaterialTheme.typography.bodySmall)
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.DropdownList) { ouvert = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(libelle(valeur), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
                for (choix in ChoixTheme.entries) {
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(libelle(choix)) },
                        trailingIcon = {
                            if (choix == valeur) {
                                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.param_theme_choix_actuel))
                            }
                        },
                        onClick = {
                            ouvert = false
                            onChange(choix)
                        },
                    )
                }
            }
        }
    }
}
