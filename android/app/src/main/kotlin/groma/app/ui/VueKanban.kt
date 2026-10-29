package groma.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import groma.app.EntreeProjet
import groma.app.R
import groma.core.EtatKanban
import groma.core.colonneAffichee
import groma.core.deposer
import groma.core.repartir
import groma.core.statutMinimal
import kotlin.math.roundToInt

/** A Kanban card: the project, the status kept on the phone, and what progress imposes at least. */
data class CarteKanban(val projet: EntreeProjet, val stocke: EtatKanban) {
    val minimal: EtatKanban get() = statutMinimal(projet.resume)
    val colonne: EtatKanban get() = colonneAffichee(stocke, minimal)
}

/**
 * Kanban view of the projects, as in Marculus and Surculus: one column per state, long press then
 * drag to change column, short tap to open the project. [onDeposer] receives the new status to
 * keep; a drop below the minimum imposed by progress is not forwarded.
 */
@Composable
fun VueKanban(
    cartes: List<CarteKanban>,
    onOuvrir: (EntreeProjet) -> Unit,
    onDeposer: (EntreeProjet, EtatKanban) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tiree by remember { mutableStateOf<CarteKanban?>(null) }
    var doigt by remember { mutableStateOf(Offset.Zero) } // root coordinates
    var origine by remember { mutableStateOf(Offset.Zero) } // top-left corner of this view, root coordinates
    val rectsColonnes = remember { mutableStateMapOf<EtatKanban, Rect>() }
    val colonnes = repartir(cartes) { it.colonne }

    Box(modifier.fillMaxSize().onGloballyPositioned { origine = it.boundsInRoot().topLeft }) {
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EtatKanban.entries.forEach { etat ->
                val cartesDeLaColonne = colonnes.getValue(etat)
                Column(
                    Modifier.width(260.dp).fillMaxHeight().onGloballyPositioned { rectsColonnes[etat] = it.boundsInRoot() },
                ) {
                    Text(
                        "${etat.libelle} (${cartesDeLaColonne.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp),
                    )
                    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cartesDeLaColonne, key = { it.projet.cle }) { carte ->
                            CarteDeKanban(
                                carte = carte,
                                estTiree = tiree?.projet?.cle == carte.projet.cle,
                                onOuvrir = { onOuvrir(carte.projet) },
                                onDebut = { p -> tiree = carte; doigt = p },
                                onGlisse = { p -> doigt = p },
                                onFin = {
                                    val cible = rectsColonnes.entries.firstOrNull { it.value.contains(doigt) }?.key
                                    cible?.let { deposer(carte.stocke, it, carte.minimal) }?.let { onDeposer(carte.projet, it) }
                                    tiree = null
                                },
                                onAnnule = { tiree = null },
                            )
                        }
                    }
                }
            }
        }
        tiree?.let { c ->
            ElevatedCard(
                Modifier
                    .offset { IntOffset((doigt.x - origine.x - 110.dp.toPx()).roundToInt(), (doigt.y - origine.y - 24.dp.toPx()).roundToInt()) }
                    .width(220.dp),
            ) {
                Text(
                    c.projet.titre,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CarteDeKanban(
    carte: CarteKanban,
    estTiree: Boolean,
    onOuvrir: () -> Unit,
    onDebut: (Offset) -> Unit,
    onGlisse: (Offset) -> Unit,
    onFin: () -> Unit,
    onAnnule: () -> Unit,
) {
    var coin by remember { mutableStateOf(Offset.Zero) }
    val r = carte.projet.resume
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .onGloballyPositioned { coin = it.boundsInRoot().topLeft }
            .graphicsLayer { alpha = if (estTiree) 0.3f else 1f }
            .pointerInput(carte.projet.cle) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> onDebut(coin + offset) },
                    onDrag = { change, _ -> change.consume(); onGlisse(coin + change.position) },
                    onDragEnd = { onFin() },
                    onDragCancel = { onAnnule() },
                )
            }
            .clickable(onClick = onOuvrir),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(carte.projet.titre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.projet_placettes, r.nbBase + r.nbRempl, r.nbBase, r.nbRempl),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (r.nbBase == 0) 0f else r.baseFaites.toFloat() / r.nbBase },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.projet_avancement, r.faites, r.envoyees),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
