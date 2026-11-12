package groma.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import groma.app.PhotoArbre
import groma.core.EtatPhotos
import groma.core.PhotosArbre
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import groma.core.Essences
import groma.core.SaisieArbre
import kotlinx.coroutines.launch

private fun nombreAffiche(x: Double) = (Math.round(x * 10) / 10.0).toString().removeSuffix(".0").replace('.', ',')

/**
 * Form of one tree (mission 019): number, estimated diameter; species (search in BD Foret v2 by
 * code or label), tape diameter in cm, status, quality. Values are checked by [SaisieArbre.controler]
 * and the message is shown under the field. [onEnregistrer] receives only the fields that changed
 * (empty text = erase, as in the 018 API) and throws on a refused value; nothing is written then.
 * "Enregistrer et arbre suivant" opens the next tree in number order through [onOuvrir].
 * Mission 023: with [creation] the form is that of a tree being added by hand (placeholder [arbre]),
 * the tape diameter is required and every field is sent; the same requirement holds for a tree
 * already added ([ArbrePlacette.ajoute]), which also gets [onSupprimer] ("Supprimer cet arbre",
 * after a confirmation; null for a tree of the video: it is never deleted).
 * Mission 022: a multi-line "Note" (`arbres.notes`, at most [PhotosArbre.NOTE_MAX] characters) and the photos
 * (`arbres.photos`): thumbnails, "Prendre une photo" ([ActivityResultContracts.TakePicture] into the cache, then
 * [PhotoArbre.enregistrer] in `DCIM/` of [dossierProjet]), removal after a confirmation. Both are shown only for an
 * existing tree (not in [creation]) and when [dossierProjet] is known. What is already there is shown and kept
 * (rule 5). A removed photo is only unlisted until "Enregistrer"; then [onNettoyerPhotos] gets the paths no longer
 * cited (the core deletes the file only if Groma took it). A photo taken then abandoned (form closed without saving)
 * is passed to [onNettoyerPhotos] too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicheArbre(
    arbre: ArbrePlacette,
    suivant: ArbrePlacette?,
    onEnregistrer: suspend (saisie: Map<String, String?>) -> Unit,
    onOuvrir: (ArbrePlacette) -> Unit,
    onFermer: () -> Unit,
    creation: Boolean = false,
    onSupprimer: (suspend () -> Unit)? = null,
    dossierProjet: File? = null,
    plotId: String = "",
    onNettoyerPhotos: (List<String>) -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Set by the form below: what a real dismissal (swipe, back) must clean up. Not run on a re-creation.
    val abandon = remember { arrayOf<(() -> Unit)?>(null) }
    ModalBottomSheet(onDismissRequest = { abandon[0]?.invoke(); onFermer() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Each tree starts from its own state.
        androidx.compose.runtime.key(arbre.uuid) {
            val rubanInitial = if (arbre.dbhMesure) arbre.dbhCm?.let(::nombreAffiche).orEmpty() else ""
            var recherche by rememberSaveable { mutableStateOf(arbre.essence?.let(Essences::affichage).orEmpty()) }
            var essence by rememberSaveable { mutableStateOf(arbre.essence.orEmpty()) }
            var ruban by rememberSaveable { mutableStateOf(rubanInitial) }
            var statut by rememberSaveable { mutableStateOf(arbre.statut) }
            var qualite by rememberSaveable { mutableStateOf(arbre.qualite.orEmpty()) }
            var erreurEssence by remember { mutableStateOf<String?>(null) }
            var erreurGenerale by remember { mutableStateOf<String?>(null) }
            var enCours by remember { mutableStateOf(false) }
            var confirmerSuppression by remember { mutableStateOf(false) }
            val rubanObligatoire = creation || arbre.ajoute
            val avecTerrain = !creation && dossierProjet != null
            var note by rememberSaveable { mutableStateOf(arbre.notes.orEmpty()) }
            // Survives a re-creation (rotation, process death while the camera is open).
            var photosEtat by rememberSaveable(stateSaver = listSaver(save = { it.enTextes() }, restore = { EtatPhotos.depuis(it) })) {
                mutableStateOf(EtatPhotos(arbre.photos))
            }
            val photos = photosEtat.courantes
            var temporaire by rememberSaveable { mutableStateOf<String?>(null) }
            var photoARetirer by remember { mutableStateOf<String?>(null) }
            val erreurNote = PhotosArbre.controlerNote(note)

            fun ajouterPrise(prise: File) {
                scope.launch {
                    // Not cancellable: the file must reach the list even if the screen is being re-created.
                    val rel = withContext(Dispatchers.IO + NonCancellable) { PhotoArbre.enregistrer(prise, dossierProjet!!, plotId, arbre.numero) }
                    if (rel == null) erreurGenerale = ctx.getString(R.string.fiche_photo_echec)
                    else photosEtat = photosEtat.ajoutee(rel)
                }
            }
            val prise = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                val f = temporaire?.let(::File); temporaire = null
                if (f != null) { if (ok) ajouterPrise(f) else f.delete() }
            }
            fun lancerPrise() {
                val (f, uri) = PhotoArbre.temporaire(ctx)
                temporaire = f.path
                prise.launch(uri)
            }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                if (ok) lancerPrise() else erreurGenerale = ctx.getString(R.string.fiche_photo_permission)
            }
            // Real dismissal without saving (swipe, back): the photos taken in this form and an unfinished camera file are dropped.
            abandon[0] = {
                temporaire?.let { File(it).delete() }
                if (photosEtat.abandonnees.isNotEmpty()) onNettoyerPhotos(photosEtat.abandonnees)
            }

            val propositions = if (recherche.isNotBlank() && Essences.resoudre(recherche, arbre.essence, essence) == null) Essences.rechercher(recherche, 6) else emptyList()
            val erreurRuban = SaisieArbre.controler("dbh_cm", ruban) ?: if (rubanObligatoire && ruban.isBlank()) ctx.getString(R.string.fiche_ruban_obligatoire) else null

            fun resoudreEssence(): String? = Essences.resoudre(recherche, arbre.essence, essence)

            fun enregistrer(apres: () -> Unit) {
                erreurGenerale = null
                val code = resoudreEssence()
                erreurEssence = if (code == null) ctx.getString(R.string.fiche_essence_a_choisir) else SaisieArbre.controler("essence_arbre", code)
                if (code == null || erreurEssence != null || erreurRuban != null || (avecTerrain && erreurNote != null)) return
                val saisie = linkedMapOf<String, String?>()
                if (creation || code != arbre.essence.orEmpty()) saisie["essence_arbre"] = code
                if (creation || ruban.trim() != rubanInitial) saisie["dbh_cm"] = ruban.trim()
                if (creation || statut != arbre.statut) saisie["statut"] = statut
                if (creation || qualite != arbre.qualite.orEmpty()) saisie["qualite"] = qualite
                if (avecTerrain) {
                    if (note.trim() != arbre.notes.orEmpty()) saisie["notes"] = note.trim()
                    if (photosEtat.modifiee) saisie["photos"] = PhotosArbre.joindre(photos).orEmpty()
                }
                enCours = true
                scope.launch {
                    try {
                        onEnregistrer(saisie)
                        val retirees = photosEtat.retireesALEnregistrement
                        photosEtat = photosEtat.enregistree()
                        abandon[0] = null
                        if (retirees.isNotEmpty()) onNettoyerPhotos(retirees)
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

            if (confirmerSuppression && onSupprimer != null) {
                AlertDialog(
                    onDismissRequest = { confirmerSuppression = false },
                    title = { Text(stringResource(R.string.fiche_supprimer_titre, arbre.numero)) },
                    text = { Text(stringResource(R.string.fiche_supprimer_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmerSuppression = false
                            enCours = true
                            scope.launch {
                                try {
                                    onSupprimer()
                                } catch (e: Exception) {
                                    erreurGenerale = ctx.getString(R.string.fiche_erreur, e.message ?: e.javaClass.simpleName)
                                } finally {
                                    enCours = false
                                }
                            }
                        }) { Text(stringResource(R.string.fiche_supprimer_confirmer), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { confirmerSuppression = false }) { Text(stringResource(R.string.fiche_supprimer_annuler)) } },
                )
            }

            photoARetirer?.let { chemin ->
                AlertDialog(
                    onDismissRequest = { photoARetirer = null },
                    title = { Text(stringResource(R.string.fiche_photo_retirer_titre)) },
                    text = {
                        Text(
                            if (PhotosArbre.estDeGroma(chemin)) stringResource(R.string.fiche_photo_retirer_groma)
                            else stringResource(R.string.fiche_photo_retirer_autre),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            photoARetirer = null
                            // A photo taken here and never saved: nobody else cites it, the file goes now.
                            val (suite, aSupprimer) = photosEtat.retiree(chemin)
                            photosEtat = suite
                            if (aSupprimer.isNotEmpty()) onNettoyerPhotos(aSupprimer)
                        }) { Text(stringResource(R.string.fiche_photo_retirer_confirmer), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { photoARetirer = null }) { Text(stringResource(R.string.fiche_supprimer_annuler)) } },
                )
            }

            // Opens at the top (essence first), keyboard closed.
            val defilement = rememberScrollState()
            val clavierFiche = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            val focusFiche = androidx.compose.ui.platform.LocalFocusManager.current
            androidx.compose.runtime.LaunchedEffect(arbre.uuid) { focusFiche.clearFocus(); clavierFiche?.hide(); defilement.scrollTo(0) }
            Column(
                Modifier.fillMaxWidth().imePadding().verticalScroll(defilement).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (creation) stringResource(R.string.fiche_titre_nouveau) else stringResource(R.string.fiche_titre, arbre.numero),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (!creation) {
                    Text(
                        if (arbre.ajoute) stringResource(R.string.fiche_arbre_ajoute)
                        else arbre.dbhEstimeCm?.let { stringResource(R.string.fiche_diametre_estime, nombreAffiche(it)) } ?: stringResource(R.string.fiche_sans_estimation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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
                        Essences.affichage(e.code),
                        modifier = Modifier.fillMaxWidth().clickable { essence = e.code; recherche = Essences.affichage(e.code); erreurEssence = null }.padding(vertical = 10.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                OutlinedTextField(
                    value = ruban,
                    onValueChange = { ruban = it },
                    label = { Text(stringResource(R.string.fiche_ruban)) },
                    supportingText = { Text(erreurRuban ?: stringResource(if (rubanObligatoire) R.string.fiche_ruban_obligatoire else R.string.fiche_ruban_aide)) },
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

                if (avecTerrain) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.fiche_note)) },
                        supportingText = { Text(erreurNote ?: stringResource(R.string.fiche_note_aide, PhotosArbre.NOTE_MAX)) },
                        isError = erreurNote != null,
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.fiche_photos, photos.size), style = MaterialTheme.typography.labelLarge)
                    if (photos.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            photos.toList().forEach { chemin ->
                                Column(Modifier.width(112.dp)) {
                                    val vignette by produceState<Bitmap?>(null, chemin) {
                                        value = withContext(Dispatchers.IO) { PhotoArbre.vignette(dossierProjet!!, chemin) }
                                    }
                                    Box(Modifier.size(112.dp)) {
                                        vignette?.let { Image(it.asImageBitmap(), contentDescription = chemin, contentScale = ContentScale.Crop, modifier = Modifier.size(112.dp)) }
                                            ?: Text(stringResource(R.string.fiche_photo_absente), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(enabled = !enCours, onClick = { photoARetirer = chemin }) { Text(stringResource(R.string.fiche_photo_retirer)) }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        enabled = !enCours,
                        onClick = {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) lancerPrise()
                            else permission.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.fiche_photo_prendre)) }
                }

                if (creation) Text(stringResource(R.string.fiche_creation_suite), color = MaterialTheme.colorScheme.onSurfaceVariant)
                erreurGenerale?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !enCours, onClick = { enregistrer(onFermer) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (creation) R.string.fiche_creer else R.string.fiche_enregistrer))
                }
                if (!creation) {
                    OutlinedButton(
                        enabled = !enCours && suivant != null,
                        onClick = { suivant?.let { s -> enregistrer { onOuvrir(s) } } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(suivant?.let { stringResource(R.string.fiche_suivant, it.numero) } ?: stringResource(R.string.fiche_dernier)) }
                }
                if (onSupprimer != null && arbre.ajoute) {
                    TextButton(enabled = !enCours, onClick = { confirmerSuppression = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.fiche_supprimer), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
