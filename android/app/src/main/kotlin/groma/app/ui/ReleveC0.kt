package groma.app.ui

import android.Manifest
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.nemeton.terrain.carte.ZoneTuiles
import groma.app.C0Enregistre
import groma.app.R
import groma.app.carte.Boussole
import groma.app.carte.CoucheGuidage
import groma.app.carte.FournisseurTuiles
import groma.app.carte.TuilesHorsLigne
import groma.app.carte.telechargeable
import groma.app.TelechargementTuiles
import groma.app.carte.Forme
import groma.app.carte.coucheParcelles
import groma.app.carte.coucheUgf
import groma.app.carte.CouchePosition
import groma.app.carte.CouchePlacettes
import groma.app.carte.Fond
import groma.app.carte.SOURCE_CADASTRE
import groma.app.carte.ZOOM_MAX
import groma.app.carte.ZOOM_TUILES
import groma.app.gnss.ConfigRtk
import groma.app.gnss.ServiceGnssRtk
import groma.app.gnss.SourcePosition
import groma.core.DonneesCarte
import groma.core.EtatCentre
import groma.core.FixGnss
import groma.core.Guidage
import groma.core.CentreMesure
import groma.core.MesureCentre
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import groma.core.OrigineFix
import groma.core.PlacetteCarte
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import groma.core.EtapeReleveC0
import groma.core.Geodesie
import groma.core.Position
import kotlinx.coroutines.CoroutineScope

/** What the plot already has as centre, for the « déjà relevé » and « remplacer un RTK » steps. */
data class AncienC0(val qualite: String?, val ecartM: Double?)

/**
 * The one « Relever C0 » flow (mission 030), shared by the map (arrival banner, plot sheet) and the
 * « Placette » view: authorisation by the current fix, warning beyond 30 m, confirmations (float, phone,
 * replacing an RTK centre, already surveyed), the survey itself, the quality message. Nothing here is
 * specific to a screen: [ancien] says what the plot already has, [enregistrer] writes the centre and
 * returns its distance to the planned point.
 */
class ReleveC0(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val ancien: (String) -> AncienC0?,
    private val enregistrer: suspend (String, CentreMesure) -> C0Enregistre,
) {
    var etape by mutableStateOf<EtapeCentre?>(null)

    /** Survey step: confirmed in float or when the plot already has a centre (brief § 5.4). */
    fun mesurer(plotId: String, flottant: Boolean, telephone: Boolean = false) {
        val a = ancien(plotId)
        // Replacing an RTK centre by a phone one is confirmed first (mission 025).
        etape = when (EtapeReleveC0.avant(a != null, a?.qualite, telephone)) {
            EtapeReleveC0.Avant.REMPLACER_RTK -> EtapeCentre.RemplacerRtk(plotId)
            EtapeReleveC0.Avant.DEJA -> EtapeCentre.Deja(plotId, a?.ecartM, flottant, telephone)
            EtapeReleveC0.Avant.RELEVER -> EtapeCentre.Mesure(plotId, flottant, telephone)
        }
    }

    /** « Relever C0 » at the current fix (arrival banner: no distance check). */
    fun demander(plotId: String) {
        val f = SourcePosition.fix.value
        etape = when (MesureCentre.autorisation(f, telephoneDisponible = true)) {
            MesureCentre.Autorisation.OUI -> { mesurer(plotId, false); return }
            MesureCentre.Autorisation.CONFIRMER_FLOTTANT -> EtapeCentre.Flottant(plotId, f?.precisionHorizontaleM)
            MesureCentre.Autorisation.TELEPHONE -> EtapeCentre.Message(plotId, ctx.getString(R.string.centre_telephone))
            MesureCentre.Autorisation.CONFIRMER_TELEPHONE -> EtapeCentre.Telephone(plotId, f?.precisionHorizontaleM)
            MesureCentre.Autorisation.SANS_RTK -> EtapeCentre.Message(plotId, ctx.getString(R.string.centre_sans_rtk, f?.qualite?.libelle ?: ""))
            MesureCentre.Autorisation.SANS_POSITION -> EtapeCentre.Message(plotId, ctx.getString(R.string.centre_sans_position))
        }
    }

    /** From a sheet or a view at any distance: beyond 30 m a non blocking warning comes first (mission 029). */
    fun demanderDepuisFiche(plotId: String, prevu: Position?) {
        val f = SourcePosition.fix.value
        val distance = if (f != null && prevu != null) Geodesie.distanceM(f.position, prevu) else null
        if (EtatCentre.eloigne(distance)) etape = EtapeCentre.Eloigne(plotId, distance!!) else demander(plotId)
    }

    fun terminer(plotId: String, c: CentreMesure?, telephone: Boolean = false) {
        if (c == null && telephone) { etape = EtapeCentre.TelephoneInsuffisant(plotId); return }
        if (c == null) { etape = EtapeCentre.Message(plotId, ctx.getString(R.string.centre_aucun)); return }
        scope.launch {
            etape = try {
                val r = enregistrer(plotId, c)
                val ecart = r.ecartM
                val texte = if (c.qualite == "telephone") {
                    ctx.getString(R.string.centre_enregistre_telephone, metres(c.precisionM), ecart?.let(::metres) ?: "?", c.nbFixes)
                } else {
                    val qualite = ctx.getString(if (c.qualite == "fix") R.string.centre_qualite_fix else R.string.centre_qualite_float)
                    ctx.getString(R.string.centre_enregistre, qualite, metres(c.precisionM), ecart?.let(::metres) ?: "?", c.nbFixes)
                }
                // Mission 031: the targets follow the new C0.
                val cibles = if (r.ciblesRecalees.isEmpty()) "" else "\n" + ctx.getString(R.string.centre_cibles_recalees, r.ciblesRecalees.joinToString(", "))
                EtapeCentre.Message(plotId, texte + cibles)
            } catch (e: Exception) {
                EtapeCentre.Message(plotId, ctx.getString(R.string.centre_erreur, e.message ?: e.javaClass.simpleName))
            }
        }
    }
}

@Composable
fun rememberReleveC0(ancien: (String) -> AncienC0?, enregistrer: suspend (String, CentreMesure) -> C0Enregistre): ReleveC0 {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // The lambdas are read at call time: the latest data and writer are always used.
    val ancienCourant by rememberUpdatedState(ancien)
    val enregistrerCourant by rememberUpdatedState(enregistrer)
    return remember { ReleveC0(ctx, scope, { ancienCourant(it) }, { id, c -> enregistrerCourant(id, c) }) }
}

/**
 * Position listening while a screen offers « Relever C0 »: location permission asked once, the RTK
 * receiver started if set, the phone's GNSS listened to every second; released when the screen leaves.
 * Returns whether the location permission is granted, and a launcher asking for it again.
 */
@Composable
fun EcouteGnss(): () -> Unit {
    val ctx = LocalContext.current
    var localisation by remember { mutableStateOf(SourcePosition.localisationAutorisee(ctx)) }
    val demander = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        localisation = SourcePosition.localisationAutorisee(ctx)
    }
    val redemander = { if (!localisation) demander.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    LaunchedEffect(Unit) {
        redemander()
        if (ConfigRtk.lire(ctx).actif && !ServiceGnssRtk.etat.value.enMarche) ServiceGnssRtk.demarrer(ctx)
    }
    DisposableEffect(localisation) {
        val ecoute = SourcePosition.ecouterTelephone(ctx)
        onDispose { ecoute.close() }
    }
    return redemander
}

/** Dialogs of the flow, to be placed once in the screen. */
@Composable
fun DialoguesReleveC0(r: ReleveC0) {
    val ctx = LocalContext.current
    when (val d = r.etape) {
        null -> {}
        is EtapeCentre.Message -> AlertDialog(
            onDismissRequest = { r.etape = null },
            confirmButton = { TextButton(onClick = { r.etape = null }) { Text(stringResource(R.string.fermer)) } },
            title = { Text(stringResource(R.string.centre_titre, d.plotId)) },
            text = { Text(d.texte) },
        )
        is EtapeCentre.Eloigne -> AlertDialog(
            onDismissRequest = { r.etape = null },
            confirmButton = { TextButton(onClick = { r.demander(d.plotId) }) { Text(stringResource(R.string.centre_eloigne_continuer)) } },
            dismissButton = { TextButton(onClick = { r.etape = null }) { Text(stringResource(R.string.annuler)) } },
            title = { Text(stringResource(R.string.centre_titre, d.plotId)) },
            text = { Text(stringResource(R.string.centre_eloigne, distance(d.distanceM))) },
        )
        is EtapeCentre.Flottant -> AlertDialog(
            onDismissRequest = { r.etape = null },
            confirmButton = { TextButton(onClick = { r.mesurer(d.plotId, true) }) { Text(stringResource(R.string.centre_enregistrer_flottant)) } },
            dismissButton = { TextButton(onClick = { r.etape = null }) { Text(stringResource(R.string.annuler)) } },
            title = { Text(stringResource(R.string.centre_titre, d.plotId)) },
            text = { Text(stringResource(R.string.centre_flottant, d.precisionM?.let(::metres) ?: "?")) },
        )
        is EtapeCentre.Telephone -> AlertDialog(
            onDismissRequest = { r.etape = null },
            confirmButton = { TextButton(onClick = { r.mesurer(d.plotId, false, telephone = true) }) { Text(stringResource(R.string.centre_telephone_mesurer)) } },
            dismissButton = { TextButton(onClick = { r.etape = null }) { Text(stringResource(R.string.annuler)) } },
            title = { Text(stringResource(R.string.centre_titre, d.plotId)) },
            text = { Text(stringResource(R.string.centre_telephone_avertissement, d.precisionM?.let(::metres) ?: "?", MesureCentre.DUREE_TELEPHONE_S)) },
        )
        is EtapeCentre.TelephoneInsuffisant -> AlertDialog(
            onDismissRequest = { r.etape = null },
            confirmButton = { TextButton(onClick = { r.etape = EtapeCentre.Mesure(d.plotId, false, true) }) { Text(stringResource(R.string.centre_telephone_recommencer)) } },
            dismissButton = { TextButton(onClick = { r.etape = null }) { Text(stringResource(R.string.fermer)) } },
            title = { Text(stringResource(R.string.centre_titre, d.plotId)) },
            text = { Text(stringResource(R.string.centre_telephone_insuffisant, MesureCentre.MIN_FIXES_TELEPHONE, MesureCentre.PRECISION_MAX_TELEPHONE_M.toInt())) },
        )
        is EtapeCentre.RemplacerRtk -> AlertDialog(
            onDismissRequest = { r.etape = null },
            confirmButton = { TextButton(onClick = { r.etape = EtapeCentre.Mesure(d.plotId, false, true) }) { Text(stringResource(R.string.centre_telephone_remplacer)) } },
            dismissButton = { TextButton(onClick = { r.etape = null }) { Text(stringResource(R.string.annuler)) } },
            title = { Text(stringResource(R.string.centre_titre, d.plotId)) },
            text = { Text(stringResource(R.string.centre_telephone_remplace_rtk)) },
        )
        is EtapeCentre.Deja -> AlertDialog(
            onDismissRequest = { r.etape = null },
            confirmButton = { TextButton(onClick = { r.etape = EtapeCentre.Mesure(d.plotId, d.flottant, d.telephone) }) { Text(stringResource(R.string.centre_mesurer_quand_meme)) } },
            dismissButton = { TextButton(onClick = { r.etape = null }) { Text(stringResource(R.string.annuler)) } },
            title = { Text(stringResource(R.string.centre_titre, d.plotId)) },
            text = { Text(stringResource(R.string.centre_deja, d.plotId, d.ecartM?.let(::metres) ?: "?")) },
        )
        is EtapeCentre.Mesure -> DialogueMesure(
            d.plotId, d.flottant, d.telephone, if (d.telephone) MesureCentre.DUREE_TELEPHONE_S else remember { ConfigRtk.lire(ctx).dureeCentreS },
            onFini = { c -> r.terminer(d.plotId, c, d.telephone) },
            onAnnuler = { r.etape = null },
        )
    }
}

/**
 * Centre measurement in progress: the receiver's fixes are averaged for
 * [dureeS] seconds (RTK fixed only, or float too when confirmed); the pole
 * must stay still. Cancelling writes nothing.
 */
@Composable
private fun DialogueMesure(plotId: String, flottant: Boolean, telephone: Boolean, dureeS: Int, onFini: (CentreMesure?) -> Unit, onAnnuler: () -> Unit) {
    val mesure = remember { MesureCentre(flottant, accepterTelephone = telephone) }
    var ecoule by remember { mutableIntStateOf(0) }
    var retenus by remember { mutableIntStateOf(0) }
    var ecartes by remember { mutableIntStateOf(0) }
    var precision by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        val debut = SystemClock.elapsedRealtime()
        val ecoute = launch {
            // Phone: the value already held when the measure starts may be up to a minute old: ignored.
            (if (telephone) SourcePosition.fix.drop(1) else SourcePosition.fix).collect { f ->
                if (f == null) return@collect
                if (mesure.ajouter(f)) { retenus = mesure.nombre; precision = if (telephone) f.precisionHorizontaleM else mesure.resultat()?.precisionM } else ecartes++
            }
        }
        while (ecoule < dureeS) {
            delay(200)
            ecoule = ((SystemClock.elapsedRealtime() - debut) / 1000).toInt().coerceAtMost(dureeS)
        }
        ecoute.cancel()
        // Phone: 30 s AND at least 20 accepted fixes, else nothing is recorded (resultat() is null).
        onFini(mesure.resultat())
    }
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        dismissButton = { TextButton(onClick = onAnnuler) { Text(stringResource(R.string.annuler)) } },
        title = { Text(stringResource(R.string.centre_titre, plotId)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (telephone) Text(stringResource(R.string.centre_telephone_en_cours, ecoule, dureeS), color = MaterialTheme.colorScheme.error)
                else Text(stringResource(R.string.centre_en_cours, ecoule, dureeS))
                LinearProgressIndicator(progress = { ecoule.toFloat() / dureeS }, modifier = Modifier.fillMaxWidth())
                if (telephone) Text(stringResource(R.string.centre_fixes_telephone, retenus, MesureCentre.MIN_FIXES_TELEPHONE, precision?.let { metres(it) + " m" } ?: "—"))
                else Text(stringResource(R.string.centre_fixes, retenus, precision?.let { metres(it) + " m" } ?: "—"))
                if (ecartes > 0) Text(stringResource(if (telephone) R.string.centre_ignores_telephone else R.string.centre_ignores, ecartes), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/** Steps of « Relever C0 ». */
sealed interface EtapeCentre {
    val plotId: String
    data class Message(override val plotId: String, val texte: String) : EtapeCentre
    data class Flottant(override val plotId: String, val precisionM: Double?) : EtapeCentre
    /** The position is more than 30 m from the planned point: warned, never refused (mission 029). */
    data class Eloigne(override val plotId: String, val distanceM: Double) : EtapeCentre
    data class Deja(override val plotId: String, val ecartM: Double?, val flottant: Boolean, val telephone: Boolean = false) : EtapeCentre
    data class Mesure(override val plotId: String, val flottant: Boolean, val telephone: Boolean = false) : EtapeCentre
    /** Phone GNSS offered, with its warning (mission 025). */
    data class Telephone(override val plotId: String, val precisionM: Double?) : EtapeCentre
    /** Fewer than 20 usable phone fixes in 30 s: nothing recorded, the operator may start again. */
    data class TelephoneInsuffisant(override val plotId: String) : EtapeCentre
    /** The plot has an RTK centre: replacing it by the phone's needs a confirmation. */
    data class RemplacerRtk(override val plotId: String) : EtapeCentre
}
