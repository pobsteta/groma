package groma.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import groma.app.R
import groma.core.ArbrePlacette
import groma.core.Essence
import groma.core.Essences
import groma.core.SaisieArbre
import kotlinx.coroutines.launch

private fun nombreAffiche(x: Double) = (Math.round(x * 10) / 10.0).toString().removeSuffix(".0").replace('.', ',')
private fun essenceAffichee(e: Essence) = "${e.libelle} (${e.code})"

/**
 * Form of one tree (mission 019): number, estimated diameter; species (search in BD Foret v2 by
 * code or label), tape diameter in cm, status, quality. Values are checked by [SaisieArbre.controler]
 * and the message is shown under the field. [onEnregistrer] receives only the fields that changed
 * (empty text = erase, as in the 018 API) and throws on a refused value; nothing is written then.
 * "Enregistrer et arbre suivant" opens the next tree in number order through [onOuvrir].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicheArbre(
    arbre: ArbrePlacette,
    suivant: ArbrePlacette?,
    onEnregistrer: suspend (saisie: Map<String, String?>) -> Unit,
    onOuvrir: (ArbrePlacette) -> Unit,
    onFermer: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onFermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Each tree starts from its own state.
        androidx.compose.runtime.key(arbre.uuid) {
            val essenceInitiale = Essences.parCode(arbre.essence)
            val rubanInitial = if (arbre.dbhMesure) arbre.dbhCm?.let(::nombreAffiche).orEmpty() else ""
            var recherche by remember { mutableStateOf(essenceInitiale?.let(::essenceAffichee) ?: arbre.essence.orEmpty()) }
            var essence by remember { mutableStateOf(arbre.essence.orEmpty()) }
            var ruban by remember { mutableStateOf(rubanInitial) }
            var statut by remember { mutableStateOf(arbre.statut) }
            var qualite by remember { mutableStateOf(arbre.qualite.orEmpty()) }
            var erreurEssence by remember { mutableStateOf<String?>(null) }
            var erreurGenerale by remember { mutableStateOf<String?>(null) }
            var enCours by remember { mutableStateOf(false) }

            val choisie = Essences.parCode(essence)
            val propositions = if (recherche.isNotBlank() && (choisie == null || recherche != essenceAffichee(choisie))) Essences.rechercher(recherche, 6) else emptyList()
            val erreurRuban = SaisieArbre.controler("dbh_cm", ruban)

            /** Code the typed text stands for: the chosen one, a unique match, "" when blank, null when ambiguous. */
            fun resoudreEssence(): String? = when {
                recherche.isBlank() -> ""
                choisie != null && recherche == essenceAffichee(choisie) -> choisie.code
                else -> Essences.rechercher(recherche, 2).singleOrNull()?.code
            }

            fun enregistrer(apres: () -> Unit) {
                erreurGenerale = null
                val code = resoudreEssence()
                erreurEssence = if (code == null) ctx.getString(R.string.fiche_essence_a_choisir) else SaisieArbre.controler("essence_arbre", code)
                if (code == null || erreurEssence != null || erreurRuban != null) return
                val saisie = linkedMapOf<String, String?>()
                if (code != arbre.essence.orEmpty()) saisie["essence_arbre"] = code
                if (ruban.trim() != rubanInitial) saisie["dbh_cm"] = ruban.trim()
                if (statut != arbre.statut) saisie["statut"] = statut
                if (qualite != arbre.qualite.orEmpty()) saisie["qualite"] = qualite
                enCours = true
                scope.launch {
                    try {
                        onEnregistrer(saisie)
                        apres()
                    } catch (e: IllegalArgumentException) {
                        erreurGenerale = e.message
                    } catch (e: Exception) {
                        erreurGenerale = ctx.getString(R.string.fiche_erreur, e.message ?: e.javaClass.simpleName)
                    } finally {
                        enCours = false
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.fiche_titre, arbre.numero), style = MaterialTheme.typography.titleLarge)
                Text(
                    arbre.dbhEstimeCm?.let { stringResource(R.string.fiche_diametre_estime, nombreAffiche(it)) } ?: stringResource(R.string.fiche_sans_estimation),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it; erreurEssence = null; if (it.isBlank()) essence = "" },
                    label = { Text(stringResource(R.string.fiche_essence)) },
                    supportingText = { Text(erreurEssence ?: stringResource(R.string.fiche_essence_aide)) },
                    isError = erreurEssence != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                propositions.forEach { e ->
                    Text(
                        essenceAffichee(e),
                        modifier = Modifier.fillMaxWidth().clickable { essence = e.code; recherche = essenceAffichee(e); erreurEssence = null }.padding(vertical = 10.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                OutlinedTextField(
                    value = ruban,
                    onValueChange = { ruban = it },
                    label = { Text(stringResource(R.string.fiche_ruban)) },
                    supportingText = { Text(erreurRuban ?: stringResource(R.string.fiche_ruban_aide)) },
                    isError = erreurRuban != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.fiche_statut), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaisieArbre.STATUTS.forEach { s -> FilterChip(selected = statut == s, onClick = { statut = s }, label = { Text(s) }) }
                }
                Text(stringResource(R.string.fiche_qualite), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaisieArbre.QUALITES.forEach { q ->
                        // A second touch on the chosen quality empties it.
                        FilterChip(selected = qualite == q, onClick = { qualite = if (qualite == q) "" else q }, label = { Text(q) })
                    }
                }

                erreurGenerale?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !enCours, onClick = { enregistrer(onFermer) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.fiche_enregistrer))
                }
                OutlinedButton(
                    enabled = !enCours && suivant != null,
                    onClick = { suivant?.let { s -> enregistrer { onOuvrir(s) } } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(suivant?.let { stringResource(R.string.fiche_suivant, it.numero) } ?: stringResource(R.string.fiche_dernier)) }
            }
        }
    }
}
