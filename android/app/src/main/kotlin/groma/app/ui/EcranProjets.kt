package groma.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import groma.app.EntreeProjet
import groma.app.R
import java.text.Normalizer
import java.util.Locale

/**
 * Home screen: the projects on the phone, as Marculus lists its contexts
 * (brief 2026-10-13, § 4-5). In-progress projects by default, the
 * "Terminés" chip switches to finished ones; the search reports what it
 * finds in the other list.
 */
@Composable
fun EcranProjets(
    projets: List<EntreeProjet>,
    racineChoisie: Boolean,
    chargement: Boolean,
    version: String,
    onOuvrir: (EntreeProjet) -> Unit,
    onActualiser: () -> Unit,
    onChoisirRacine: () -> Unit,
    onImporter: () -> Unit,
    onProjetsServeur: () -> Unit,
    serveurConfigure: Boolean,
    onMettreAJour: (EntreeProjet) -> Unit,
    onRetirer: (EntreeProjet) -> Unit,
    onParametres: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var aPropos by remember { mutableStateOf(false) }
    var recherche by rememberSaveable { mutableStateOf("") }
    var voirTermines by rememberSaveable { mutableStateOf(false) }
    var details by remember { mutableStateOf<EntreeProjet?>(null) }
    var aRetirer by remember { mutableStateOf<EntreeProjet?>(null) }
    Scaffold(
        topBar = {
            BandeauCompact(
                titre = stringResource(R.string.app_name),
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_actualiser)) }, onClick = { menu = false; onActualiser() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_dossier_projets)) }, onClick = { menu = false; onChoisirRacine() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_projets_serveur)) }, onClick = { menu = false; onProjetsServeur() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_importer)) }, onClick = { menu = false; onImporter() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_parametres)) }, onClick = { menu = false; onParametres() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_apropos)) }, onClick = { menu = false; aPropos = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (chargement) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (projets.isEmpty()) {
                Vide(racineChoisie, chargement, serveurConfigure, onChoisirRacine, onProjetsServeur)
                return@Column
            }
            val trouves = if (recherche.isBlank()) projets else projets.filter { correspond(it, recherche) }
            val (termines, enCours) = trouves.partition { it.resume.termine }
            val affiches = if (voirTermines) termines else enCours
            val autres = if (voirTermines) enCours else termines
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    label = { Text(stringResource(R.string.projets_recherche)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = voirTermines,
                    onClick = { voirTermines = !voirTermines },
                    label = { Text(stringResource(R.string.projets_termines, projets.count { it.resume.termine })) },
                    leadingIcon = if (voirTermines) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                )
            }
            if (recherche.isNotBlank() && autres.isNotEmpty()) {
                Text(
                    stringResource(if (voirTermines) R.string.projets_autres_en_cours else R.string.projets_autres_termines, autres.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(affiches, key = { it.cle }) { e ->
                    CarteProjet(
                        e,
                        onOuvrir = { onOuvrir(e) },
                        onDetails = { details = e },
                        onMettreAJour = { onMettreAJour(e) },
                        onRetirer = { aRetirer = e },
                    )
                }
            }
        }
    }
    details?.let { e ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(e.titre) },
            text = { Text(texteDetails(e), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { details = null }) { Text(stringResource(R.string.fermer)) } },
        )
    }
    aRetirer?.let { e ->
        AlertDialog(
            onDismissRequest = { aRetirer = null },
            title = { Text(stringResource(R.string.retirer_titre)) },
            text = { Text(stringResource(R.string.retirer_texte, e.titre, e.cle)) },
            confirmButton = { TextButton(onClick = { aRetirer = null; onRetirer(e) }) { Text(stringResource(R.string.retirer)) } },
            dismissButton = { TextButton(onClick = { aRetirer = null }) { Text(stringResource(R.string.annuler)) } },
        )
    }
    if (aPropos) DialogueAPropos(version) { aPropos = false }
}

/** "350 m", "8,2 km", "27 km". */
private fun distanceTexte(m: Double): String = when {
    m < 1000 -> "${(Math.round(m / 10) * 10)} m"
    m < 10_000 -> decimal(m / 1000, 1) + " km"
    else -> "${Math.round(m / 1000)} km"
}

@Composable
private fun CarteProjet(e: EntreeProjet, onOuvrir: () -> Unit, onDetails: () -> Unit, onMettreAJour: () -> Unit, onRetirer: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val r = e.resume
    val i = e.infos
    ElevatedCard(onClick = onOuvrir, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.titre, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                i?.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                listOfNotNull(
                    e.distanceM?.let { stringResource(R.string.projet_distance, distanceTexte(it)) },
                    i?.communes,
                    i?.surfaceZoneHa?.let { stringResource(R.string.projet_surface, decimal(it, 1)) },
                    i?.nbUg?.let { stringResource(R.string.projet_ug, it) },
                ).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium) }
                Text(
                    stringResource(R.string.projet_placettes, r.nbBase + r.nbRempl, r.nbBase, r.nbRempl) +
                        (rayon(e)?.let { " · r = $it m" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { if (r.nbBase == 0) 0f else r.baseFaites.toFloat() / r.nbBase },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.projet_avancement, r.faites, r.envoyees),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    listOfNotNull(
                        stringResource(R.string.projet_inventorie, decimal(r.surfaceInventorieeHa, 2)) +
                            (i?.surfaceZoneHa?.takeIf { it > 0 }?.let { " (${decimal(100 * r.surfaceInventorieeHa / it, 2)} %)" } ?: ""),
                        (r.dernierReleve ?: i?.planGenereLe)?.let(::dateCourte),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (e.majDisponible) {
                    Text(stringResource(R.string.projet_maj_disponible), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.projet_ouvrir)) }, onClick = { menu = false; onOuvrir() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.projet_details)) }, onClick = { menu = false; onDetails() })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projet_mettre_a_jour)) },
                        enabled = e.majDisponible,
                        onClick = { menu = false; onMettreAJour() },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.retirer)) }, onClick = { menu = false; onRetirer() })
                }
            }
        }
    }
}

@Composable
private fun Vide(racineChoisie: Boolean, chargement: Boolean, serveurConfigure: Boolean, onChoisirRacine: () -> Unit, onProjetsServeur: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (chargement) return@Column
        Text(
            stringResource(if (racineChoisie) R.string.projets_aucun else R.string.projets_choisir_racine),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onChoisirRacine) { Text(stringResource(R.string.menu_dossier_projets)) }
        if (racineChoisie && serveurConfigure) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onProjetsServeur) { Text(stringResource(R.string.menu_projets_serveur)) }
        }
    }
}

@Composable
private fun texteDetails(e: EntreeProjet): String {
    val i = e.infos
    val r = e.resume
    return listOfNotNull(
        stringResource(R.string.detail_identifiant, i?.projetId ?: e.cle),
        i?.description,
        i?.proprietaire?.let { stringResource(R.string.detail_proprietaire, it) },
        i?.communes?.let { stringResource(R.string.detail_communes, it) },
        i?.surfaceZoneHa?.let { stringResource(R.string.detail_surface_zone, decimal(it, 2)) },
        i?.surfaceCadastreHa?.let { stringResource(R.string.detail_surface_cadastre, decimal(it, 2)) },
        i?.nbParcelles?.let { stringResource(R.string.detail_parcelles, it) },
        i?.nbUg?.let { stringResource(R.string.detail_ug, it) },
        stringResource(R.string.projet_placettes, r.nbBase + r.nbRempl, r.nbBase, r.nbRempl),
        rayon(e)?.let { stringResource(R.string.detail_rayon, it) },
        stringResource(R.string.detail_avancement, r.baseFaites, r.nbBase, r.faites, r.envoyees, r.nonEnvoyees),
        i?.planGenereLe?.let { stringResource(R.string.detail_plan, dateCourte(it)) },
        i?.prepareLe?.let { stringResource(R.string.detail_prepare, dateCourte(it), i.preparePar ?: "") },
        if (i == null) stringResource(R.string.detail_sans_table) else null,
    ).joinToString("\n")
}

private fun rayon(e: EntreeProjet): String? {
    val (a, b) = (e.resume.rayonMinM ?: return null) to (e.resume.rayonMaxM ?: return null)
    return if (a == b) decimal(a, 1) else "${decimal(a, 1)} à ${decimal(b, 1)}"
}

private fun decimal(x: Double, chiffres: Int): String =
    String.format(Locale.FRANCE, "%.${chiffres}f", x).trimEnd('0').trimEnd(',')

/** "2026-10-12 20:05:07" or "2026-10-12T09:30:00…" → "12/10/2026". */
private fun dateCourte(d: String): String =
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(d)?.destructured?.let { (a, m, j) -> "$j/$m/$a" } ?: d

private fun normaliser(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

private fun correspond(e: EntreeProjet, recherche: String): Boolean {
    val n = normaliser(recherche.trim())
    return listOfNotNull(e.titre, e.cle, e.infos?.description, e.infos?.communes).any { normaliser(it).contains(n) }
}
