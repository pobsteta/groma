package groma.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import groma.app.R

/** One plot as shown in the list. */
data class LignePlacette(val plotId: String, val etat: String, val rayonM: Double?, val seuilCm: Double?)

/** Plots of one project, opened from the project list; back returns to it. */
@Composable
fun EcranPlacettes(
    titre: String,
    placettes: List<LignePlacette>,
    version: String,
    onRetour: () -> Unit,
    onPlacette: (String) -> Unit,
    onCarte: () -> Unit,
    onParametres: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var aPropos by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            BandeauCompact(
                titre = titre,
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retour))
                    }
                },
                actions = {
                    IconButton(onClick = onCarte) {
                        Icon(Icons.Filled.Place, contentDescription = stringResource(R.string.carte))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_parametres)) }, onClick = { menu = false; onParametres() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_apropos)) }, onClick = { menu = false; aPropos = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            placettes.isEmpty() -> Vide(stringResource(R.string.aucune_placette), Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    stringResource(R.string.placettes_nombre, placettes.size, placettes.count { it.etat == "a_faire" }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(placettes, key = { it.plotId }) { p -> CartePlacette(p) { onPlacette(p.plotId) } }
                }
            }
        }
    }
    if (aPropos) {
        AlertDialog(
            onDismissRequest = { aPropos = false },
            title = { Text(stringResource(R.string.apropos_titre, version)) },
            text = { Text(stringResource(R.string.apropos_texte)) },
            confirmButton = { TextButton(onClick = { aPropos = false }) { Text(stringResource(R.string.fermer)) } },
        )
    }
}

@Composable
private fun CartePlacette(p: LignePlacette, onOuvrir: () -> Unit) {
    ElevatedCard(onClick = onOuvrir, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.plotId, style = MaterialTheme.typography.titleLarge)
                if (p.rayonM != null && p.seuilCm != null) {
                    Text(
                        stringResource(R.string.placette_rayon, nombre(p.rayonM), nombre(p.seuilCm)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            EtatTraitement(p.etat)
        }
    }
}

@Composable
private fun EtatTraitement(etat: String) {
    val (libelle, fait) = when (etat) {
        "provisoire" -> R.string.etat_provisoire to true
        "envoye" -> R.string.etat_envoye to true
        "definitif" -> R.string.etat_definitif to true
        else -> R.string.etat_a_faire to false
    }
    AssistChip(
        onClick = {},
        label = { Text(stringResource(libelle)) },
        colors = if (fait) AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) else AssistChipDefaults.assistChipColors(),
    )
}

@Composable
private fun Vide(texte: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(texte, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun nombre(x: Double): String = if (x % 1.0 == 0.0) x.toInt().toString() else x.toString().replace('.', ',')
