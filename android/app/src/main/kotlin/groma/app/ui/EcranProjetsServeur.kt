package groma.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import groma.app.R
import groma.app.TelechargementProjet
import groma.core.EtatProjetServeur
import groma.core.ManifesteProjet
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** A project published on the server, with where it stands on the phone. */
data class LigneServeur(val manifeste: ManifesteProjet, val etat: EtatProjetServeur)

/**
 * ⋮ → "Projets du serveur…" (brief envoi projet, § 4): the projects that
 * nemetonshiny published on the Groma server, downloaded in the background
 * and installed in the projects folder.
 */
@Composable
fun EcranProjetsServeur(
    serveurConfigure: Boolean,
    racineChoisie: Boolean,
    charger: suspend () -> List<LigneServeur>,
    onTelecharger: (ManifesteProjet) -> Unit,
    onAnnuler: (ManifesteProjet) -> Unit,
    onInstalle: () -> Unit,
    onParametres: () -> Unit,
    onChoisirRacine: () -> Unit,
    onRetour: () -> Unit,
) {
    var essai by remember { mutableIntStateOf(0) }
    var lignes by remember { mutableStateOf<List<LigneServeur>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var aConfirmer by remember { mutableStateOf<LigneServeur?>(null) }
    // Downloads already reported: a finished WorkInfo stays around for a while.
    val installes = remember { mutableSetOf<UUID>() }
    LaunchedEffect(essai, serveurConfigure) {
        if (!serveurConfigure) return@LaunchedEffect
        erreur = null // the list stays on screen while it reloads
        try { lignes = charger() } catch (e: Exception) { erreur = e.message ?: e.toString() }
    }
    Scaffold(
        contentWindowInsets = margesContenu(),
        topBar = {
            BandeauCompact(
                titre = stringResource(R.string.serveur_titre),
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.param_retour))
                    }
                },
                actions = {
                    if (serveurConfigure) IconButton(onClick = { essai++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.menu_actualiser))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !serveurConfigure -> Message(stringResource(R.string.serveur_non_configure), stringResource(R.string.menu_parametres), onParametres)
                !racineChoisie -> Message(stringResource(R.string.projets_choisir_racine), stringResource(R.string.menu_dossier_projets), onChoisirRacine)
                erreur != null -> Message(stringResource(R.string.serveur_erreur, erreur!!), stringResource(R.string.reessayer)) { essai++ }
                lignes == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                lignes!!.isEmpty() -> Message(stringResource(R.string.serveur_aucun), null) {}
                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(lignes!!, key = { it.manifeste.projetId }) { l ->
                        CarteServeur(
                            l,
                            onTelecharger = {
                                // Replacing a cable drop of unknown age, or going back to an older version: ask first.
                                if (l.etat == EtatProjetServeur.ABSENT) onTelecharger(l.manifeste) else aConfirmer = l
                            },
                            onAnnuler = { onAnnuler(l.manifeste) },
                            onInstalle = { id -> if (installes.add(id)) { onInstalle(); essai++ } },
                        )
                    }
                }
            }
        }
    }
    aConfirmer?.let { l ->
        AlertDialog(
            onDismissRequest = { aConfirmer = null },
            title = { Text(l.manifeste.projetNom) },
            text = { Text(stringResource(R.string.serveur_remplacer_texte)) },
            confirmButton = { TextButton(onClick = { aConfirmer = null; onTelecharger(l.manifeste) }) { Text(stringResource(R.string.serveur_telecharger)) } },
            dismissButton = { TextButton(onClick = { aConfirmer = null }) { Text(stringResource(R.string.annuler)) } },
        )
    }
}

@Composable
private fun CarteServeur(l: LigneServeur, onTelecharger: () -> Unit, onAnnuler: () -> Unit, onInstalle: (UUID) -> Unit) {
    val m = l.manifeste
    val ctx = LocalContext.current
    val travail by remember(m.projetId) {
        WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(TelechargementProjet.nomTravail(m.projetId))
    }.collectAsState(emptyList())
    val info = travail.firstOrNull()
    val enCours = info?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
    LaunchedEffect(info?.id, info?.state) { if (info?.state == WorkInfo.State.SUCCEEDED) onInstalle(info.id) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(m.projetNom, style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(m.publieLe?.let { stringResource(R.string.serveur_publie, dateHeure(it)) }, taille(m.tailleTotale)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    when (l.etat) {
                        EtatProjetServeur.ABSENT -> R.string.serveur_etat_absent
                        EtatProjetServeur.A_JOUR -> R.string.serveur_etat_a_jour
                        EtatProjetServeur.PLUS_RECENT -> R.string.serveur_etat_plus_recent
                        EtatProjetServeur.PLUS_ANCIEN -> R.string.serveur_etat_plus_ancien
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (l.etat == EtatProjetServeur.PLUS_RECENT || l.etat == EtatProjetServeur.ABSENT) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (enCours) {
                val recu = info!!.progress.getLong(TelechargementProjet.CLE_RECU, 0)
                val total = info.progress.getLong(TelechargementProjet.CLE_TOTAL, m.tailleTotale).coerceAtLeast(1)
                if (info.state == WorkInfo.State.RUNNING && recu > 0) LinearProgressIndicator(progress = { recu.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (info.state == WorkInfo.State.RUNNING) "${taille(recu)} / ${taille(total)}" else stringResource(R.string.serveur_en_attente_reseau),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAnnuler) { Text(stringResource(R.string.annuler)) }
                }
            } else {
                if (info?.state == WorkInfo.State.FAILED) {
                    Text(
                        info.outputData.getString(TelechargementProjet.CLE_ERREUR) ?: stringResource(R.string.serveur_echec),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (info?.state == WorkInfo.State.SUCCEEDED && l.etat == EtatProjetServeur.A_JOUR) {
                    Text(stringResource(R.string.serveur_installe), style = MaterialTheme.typography.bodySmall)
                }
                when (l.etat) {
                    EtatProjetServeur.A_JOUR -> {}
                    EtatProjetServeur.ABSENT, EtatProjetServeur.PLUS_RECENT -> Button(onClick = onTelecharger) { Text(stringResource(R.string.serveur_telecharger)) }
                    EtatProjetServeur.PLUS_ANCIEN -> OutlinedButton(onClick = onTelecharger) { Text(stringResource(R.string.serveur_telecharger)) }
                }
            }
        }
    }
}

@Composable
private fun Message(texte: String, bouton: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(texte, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (bouton != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick) { Text(bouton) }
        }
    }
}

/** "12 Mo", "1,4 Go". */
private fun taille(o: Long): String = when {
    o < 1_000_000 -> "${(o + 999) / 1000} ko"
    o < 1_000_000_000 -> "${Math.round(o / 1e6)} Mo"
    else -> String.format(Locale.FRANCE, "%.1f Go", o / 1e9)
}

/** "2026-10-17T08:00:00+00:00" → "17/10/2026 10:00" in the phone's time zone. */
private fun dateHeure(d: String): String = try {
    OffsetDateTime.parse(d).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
} catch (e: Exception) {
    d
}
