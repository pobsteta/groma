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
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Map of one project (brief 2026-10-14 carte RTK, § 3 and § 5.1): IGN or OSM
 * base map, IGN cadastre, parcels, management units (UGF), visiting route,
 * plots by state, measured centres. A tap on a plot opens its sheet. Position (RTK receiver or phone),
 * fix badge and follow mode (§ 5.1, § 6). « Y aller »: distance and azimuth
 * to a plot, line on the map and arrow relative to the phone (§ 5.3);
 * on arrival, « Mesurer le centre » averages the RTK fixes and hands the
 * centre to [onEnregistrerCentre], which returns its distance to the planned point (§ 5.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranCarte(
    titre: String,
    donnees: DonneesCarte?,
    dossierProjet: File?,
    onRetour: () -> Unit,
    onOuvrirPlacette: (String) -> Unit,
    onEnregistrerCentre: suspend (String, CentreMesure) -> Double?,
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("groma", Context.MODE_PRIVATE) }
    var fond by remember { mutableStateOf(prefs.getString(PREF_FOND, null)?.let { n -> Fond.entries.firstOrNull { it.name == n } } ?: Fond.PLAN_IGN) }
    var cadastre by remember { mutableStateOf(prefs.getBoolean(PREF_CADASTRE, false)) }
    var couchePlacettes by rememberSaveable { mutableStateOf(true) }
    var coucheParcours by rememberSaveable { mutableStateOf(true) }
    var coucheCentres by rememberSaveable { mutableStateOf(true) }
    var coucheParcelles by rememberSaveable { mutableStateOf(true) }
    var coucheUgf by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var choisie by remember { mutableStateOf<PlacetteCarte?>(null) }
    val densite = ctx.resources.displayMetrics.density
    var suivi by rememberSaveable { mutableStateOf(Suivi.AUCUN) }
    var etatGnss by remember { mutableStateOf(false) }
    val fix by SourcePosition.fix.collectAsStateWithLifecycle()
    val etatRtk by ServiceGnssRtk.etat.collectAsStateWithLifecycle()
    var cibleId by rememberSaveable { mutableStateOf<String?>(null) }
    val cible = cibleId?.let { id -> donnees?.placettes?.firstOrNull { it.plotId == id } }
    // Heading shown (GNSS course when moving, else the compass), for the guidance arrow.
    var cap by remember { mutableStateOf<Float?>(null) }
    var horsLigne by remember { mutableStateOf(false) }
    val racineTuiles = remember(dossierProjet) { dossierProjet?.let(TuilesHorsLigne::racine) }
    val travailTuiles = remember(dossierProjet) {
        dossierProjet?.let { WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(TelechargementTuiles.nomTravail(it)) }
    }
    val infoTuiles = travailTuiles?.collectAsStateWithLifecycle(emptyList())?.value?.firstOrNull()
    var dialogueCentre by remember { mutableStateOf<EtapeCentre?>(null) }
    val scope = rememberCoroutineScope()

    val placettes = remember { CouchePlacettes(densite) { choisie = it } }
    val position = remember { CouchePosition(densite) }
    val guidage = remember { CoucheGuidage(densite) }
    val parcelles = remember { coucheParcelles(densite) }
    val ugf = remember { coucheUgf(densite) }
    val couchCadastre = remember {
        TilesOverlay(FournisseurTuiles(ctx.applicationContext, SOURCE_CADASTRE, racineTuiles), ctx.applicationContext).apply {
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            loadingLineColor = android.graphics.Color.TRANSPARENT
        }
    }
    val mapView = remember {
        // Tiles downloaded in the project folder first, then cache and network (§ 5.2).
        MapView(ctx, FournisseurTuiles(ctx.applicationContext, fond.source, racineTuiles)).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 5.0
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(46.6, 2.5))
            overlays += couchCadastre
            overlays += parcelles
            overlays += ugf
            overlays += placettes
            overlays += guidage
            overlays += position
            overlays += ScaleBarOverlay(this).apply { setAlignBottom(true) }
            // Moving the map by hand ends the follow mode.
            setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_MOVE && suivi == Suivi.SUIVI) suivi = Suivi.CENTRE; false }
        }
    }

    // Location asked when the map opens (no background location); the phone's GNSS is
    // listened to every second while the map is shown, and the RTK receiver started if set.
    var localisation by remember { mutableStateOf(SourcePosition.localisationAutorisee(ctx)) }
    val demander = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        localisation = SourcePosition.localisationAutorisee(ctx)
    }
    LaunchedEffect(Unit) {
        if (!localisation) demander.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        if (ConfigRtk.lire(ctx).actif && !ServiceGnssRtk.etat.value.enMarche) ServiceGnssRtk.demarrer(ctx)
    }
    DisposableEffect(localisation) {
        val ecoute = SourcePosition.ecouterTelephone(ctx)
        onDispose { ecoute.close() }
    }
    DisposableEffect(Unit) {
        val boussole = Boussole(ctx, { position.fix?.position?.let { it.latitude to it.longitude } }) { capBoussole ->
            position.boussoleDeg = capBoussole
            cap = position.capDeg
            mapView.postInvalidate()
        }
        onDispose { boussole.close() }
    }
    LaunchedEffect(fix) {
        position.fix = fix
        guidage.fix = fix
        cap = position.capDeg
        val f = fix
        if (f != null && suivi == Suivi.SUIVI) mapView.controller.setCenter(GeoPoint(f.position.latitude, f.position.longitude))
        mapView.invalidate()
    }
    LaunchedEffect(cible) {
        guidage.cible = cible
        mapView.invalidate()
    }
    // The screen stays on while guiding (brief § 7).
    val vue = LocalView.current
    DisposableEffect(cible != null) {
        vue.keepScreenOn = cible != null
        onDispose { vue.keepScreenOn = false }
    }
    fun allerA(pl: PlacetteCarte) {
        cibleId = pl.plotId
        choisie = null
        montrer(mapView, fix, pl)
    }
    // « Mesurer le centre »: refused without the RTK receiver, confirmed in float or
    // when the plot already has a centre, then measured (brief § 5.4).
    fun mesurer(plotId: String, flottant: Boolean) {
        val ancien = donnees?.centres?.firstOrNull { it.plotId == plotId }
        dialogueCentre = if (ancien != null) EtapeCentre.Deja(plotId, ancien.ecartM, flottant) else EtapeCentre.Mesure(plotId, flottant)
    }
    fun demanderMesure(plotId: String) {
        val f = fix
        dialogueCentre = when (MesureCentre.autorisation(f)) {
            MesureCentre.Autorisation.OUI -> { mesurer(plotId, false); return }
            MesureCentre.Autorisation.CONFIRMER_FLOTTANT -> EtapeCentre.Flottant(plotId, f?.precisionHorizontaleM)
            MesureCentre.Autorisation.TELEPHONE -> EtapeCentre.Message(plotId, ctx.getString(R.string.centre_telephone))
            MesureCentre.Autorisation.SANS_RTK -> EtapeCentre.Message(plotId, ctx.getString(R.string.centre_sans_rtk, f?.qualite?.libelle ?: ""))
            MesureCentre.Autorisation.SANS_POSITION -> EtapeCentre.Message(plotId, ctx.getString(R.string.centre_sans_position))
        }
    }
    fun terminerMesure(plotId: String, c: CentreMesure?) {
        if (c == null) { dialogueCentre = EtapeCentre.Message(plotId, ctx.getString(R.string.centre_aucun)); return }
        scope.launch {
            dialogueCentre = try {
                val ecart = onEnregistrerCentre(plotId, c)
                val qualite = ctx.getString(if (c.qualite == "fix") R.string.centre_qualite_fix else R.string.centre_qualite_float)
                EtapeCentre.Message(plotId, ctx.getString(R.string.centre_enregistre, qualite, metres(c.precisionM), ecart?.let(::metres) ?: "?", c.nbFixes))
            } catch (e: Exception) {
                EtapeCentre.Message(plotId, ctx.getString(R.string.centre_erreur, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    // Under guidance the banner below the map takes the navigation bar inset.
    val basCarte = if (cible != null) Modifier else Modifier.navigationBarsPadding()

    // Scale bar kept above the attribution and the navigation bar (edge-to-edge).
    // In landscape the navigation bar is on the side: shift the scale bar too.
    val basNav = if (cible != null) 0 else WindowInsets.navigationBars.getBottom(LocalDensity.current)
    val gaucheNav = WindowInsets.navigationBars.getLeft(LocalDensity.current, LocalLayoutDirection.current)
    LaunchedEffect(basNav, gaucheNav) {
        mapView.overlays.filterIsInstance<ScaleBarOverlay>().forEach { it.setScaleBarOffset(gaucheNav + (16 * densite).toInt(), basNav + (36 * densite).toInt()) }
        mapView.invalidate()
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    // Base map and cadastre, remembered for the next opening.
    LaunchedEffect(fond) {
        mapView.setTileSource(fond.source)
        // IGN tiles over-zoomed beyond 19; OSM stops there.
        mapView.maxZoomLevel = if (fond == Fond.OSM) ZOOM_TUILES.toDouble() else ZOOM_MAX
        prefs.edit().putString(PREF_FOND, fond.name).apply()
    }
    LaunchedEffect(cadastre) {
        couchCadastre.isEnabled = cadastre
        prefs.edit().putBoolean(PREF_CADASTRE, cadastre).apply()
        mapView.invalidate()
    }
    LaunchedEffect(donnees, couchePlacettes, coucheParcours, coucheCentres, coucheParcelles, coucheUgf, choisie) {
        placettes.placettes = donnees?.placettes.orEmpty()
        placettes.parcours = donnees?.parcours.orEmpty()
        placettes.centres = donnees?.centres.orEmpty()
        placettes.afficherPlacettes = couchePlacettes
        placettes.afficherParcours = coucheParcours
        placettes.afficherCentres = coucheCentres
        placettes.selection = choisie?.plotId
        parcelles.formes = donnees?.parcelles.orEmpty().map { Forme(it.polygones, it.etiquette, it.centreEtiquette) }
        parcelles.isEnabled = coucheParcelles
        ugf.formes = donnees?.ugf.orEmpty().map { Forme(it.polygones, it.etiquette, it.centreEtiquette) }
        ugf.isEnabled = coucheUgf
        mapView.invalidate()
    }
    // First framing on the project, once the data is there.
    var cadre by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(donnees) { if (donnees != null && !cadre) { cadrer(mapView, donnees, anime = false); cadre = true } }

    Column(Modifier.fillMaxSize()) {
        BandeauCompact(
            titre = titre,
            navigationIcon = {
                IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retour)) }
            },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.carte_couches)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        Text(stringResource(R.string.carte_fond), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        Fond.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.libelle) },
                                leadingIcon = { RadioButton(selected = fond == f, onClick = null) },
                                onClick = { fond = f },
                            )
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.carte_couches), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        val n = donnees
                        Case(stringResource(R.string.carte_placettes, n?.placettes?.size ?: 0), couchePlacettes) { couchePlacettes = it }
                        Case(stringResource(R.string.carte_parcours, n?.parcours?.size ?: 0), coucheParcours) { coucheParcours = it }
                        Case(stringResource(R.string.carte_centres, n?.centres?.size ?: 0), coucheCentres) { coucheCentres = it }
                        Case(stringResource(R.string.carte_parcelles, n?.parcelles?.size ?: 0), coucheParcelles) { coucheParcelles = it }
                        Case(stringResource(R.string.carte_ugf, n?.ugf?.size ?: 0), coucheUgf) { coucheUgf = it }
                        Case(stringResource(R.string.carte_cadastre), cadastre) { cadastre = it }
                        if (dossierProjet != null) {
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(stringResource(R.string.hl_menu)) }, onClick = { menu = false; horsLigne = true })
                        }
                    }
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // osmdroid draws tiles beyond its bounds: clipped, or they cover the title bar.
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().clipToBounds())
            Column(
                Modifier.align(Alignment.BottomEnd).then(basCarte).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // Guidance to the next plot to do on the route.
                if (cible == null && donnees?.prochaine() != null) {
                    SmallFloatingActionButton(onClick = { donnees.prochaine()?.let(::allerA) }) {
                        Icon(IconeYAller, contentDescription = stringResource(R.string.carte_prochaine))
                    }
                }
                SmallFloatingActionButton(onClick = { mapView.controller.zoomIn() }) { Text("+", fontSize = 22.sp) }
                SmallFloatingActionButton(onClick = { mapView.controller.zoomOut() }) { Text("−", fontSize = 22.sp) }
                SmallFloatingActionButton(onClick = { donnees?.let { cadrer(mapView, it, anime = true) } }) {
                    Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.carte_cadrer))
                }
                // First tap centres on the position, second follows it, third stops.
                SmallFloatingActionButton(
                    onClick = {
                        val f = fix
                        if (f == null) {
                            if (!localisation) demander.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        } else {
                            suivi = when (suivi) { Suivi.AUCUN -> Suivi.CENTRE; Suivi.CENTRE -> Suivi.SUIVI; Suivi.SUIVI -> Suivi.AUCUN }
                            if (suivi != Suivi.AUCUN) {
                                if (mapView.zoomLevelDouble < 17) mapView.controller.setZoom(17.0)
                                mapView.controller.animateTo(GeoPoint(f.position.latitude, f.position.longitude))
                            }
                        }
                    },
                    containerColor = if (suivi == Suivi.SUIVI) MaterialTheme.colorScheme.primary else FloatingActionButtonDefaults.containerColor,
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = stringResource(if (suivi == Suivi.SUIVI) R.string.carte_suivi else R.string.carte_ma_position))
                }
            }
            Column(Modifier.align(Alignment.TopCenter).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BadgeFix(fix, etatRtk) { etatGnss = true }
                // Tile download running: progress under the badge, the dialog may be closed.
                avancement(infoTuiles)?.let { (faites, total) ->
                    Text(
                        stringResource(R.string.hl_badge, if (total > 0) (faites * 100 / total).toInt() else 0),
                        fontSize = 11.sp, color = Color.White,
                        modifier = Modifier.padding(top = 4.dp).background(Color(0xCC37474F), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                fond.attribution + if (cadastre && fond == Fond.OSM) " · cadastre © IGN" else "",
                fontSize = 10.sp,
                color = Color(0xFF212121),
                modifier = Modifier.align(Alignment.BottomStart).then(basCarte).padding(6.dp)
                    .background(Color(0xCCFFFFFF), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        cible?.let { pl ->
            BandeauGuidage(
                plotId = pl.plotId,
                guide = fix?.let { Guidage.calculer(it.position, pl) },
                fix = fix,
                capDeg = cap,
                suivante = donnees?.prochaine(pl.plotId),
                onSuivante = ::allerA,
                onArreter = { cibleId = null },
                onMesurer = { demanderMesure(pl.plotId) },
            )
        }
    }

    if (etatGnss) DialogueEtatGnss(fix, etatRtk) { etatGnss = false }
    if (horsLigne && dossierProjet != null && racineTuiles != null) {
        DialogueHorsLigne(dossierProjet, racineTuiles, donnees, fond, cadastre, infoTuiles) { horsLigne = false }
    }
    when (val d = dialogueCentre) {
        null -> {}
        is EtapeCentre.Message -> AlertDialog(
            onDismissRequest = { dialogueCentre = null },
            confirmButton = { TextButton(onClick = { dialogueCentre = null }) { Text(stringResource(R.string.fermer)) } },
            title = { Text(stringResource(R.string.centre_titre, d.plotId)) },
            text = { Text(d.texte) },
        )
        is EtapeCentre.Flottant -> AlertDialog(
            onDismissRequest = { dialogueCentre = null },
            confirmButton = { TextButton(onClick = { mesurer(d.plotId, true) }) { Text(stringResource(R.string.centre_enregistrer_flottant)) } },
            dismissButton = { TextButton(onClick = { dialogueCentre = null }) { Text(stringResource(R.string.annuler)) } },
            title = { Text(stringResource(R.string.centre_titre, d.plotId)) },
            text = { Text(stringResource(R.string.centre_flottant, d.precisionM?.let(::metres) ?: "?")) },
        )
        is EtapeCentre.Deja -> AlertDialog(
            onDismissRequest = { dialogueCentre = null },
            confirmButton = { TextButton(onClick = { dialogueCentre = EtapeCentre.Mesure(d.plotId, d.flottant) }) { Text(stringResource(R.string.centre_mesurer_quand_meme)) } },
            dismissButton = { TextButton(onClick = { dialogueCentre = null }) { Text(stringResource(R.string.annuler)) } },
            title = { Text(stringResource(R.string.centre_titre, d.plotId)) },
            text = { Text(stringResource(R.string.centre_deja, d.plotId, d.ecartM?.let(::metres) ?: "?")) },
        )
        is EtapeCentre.Mesure -> DialogueMesure(
            d.plotId, d.flottant, remember { ConfigRtk.lire(ctx).dureeCentreS },
            onFini = { c -> terminerMesure(d.plotId, c) },
            onAnnuler = { dialogueCentre = null },
        )
    }
    choisie?.let { pl ->
        ModalBottomSheet(onDismissRequest = { choisie = null }) {
            FeuillePlacette(
                pl, donnees, fix?.let { Guidage.calculer(it.position, pl) },
                onYAller = { allerA(pl) },
                onOuvrir = { choisie = null; onOuvrirPlacette(pl.plotId) },
            )
        }
    }
}

@Composable
private fun Case(libelle: String, coche: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(libelle) },
        leadingIcon = { Checkbox(checked = coche, onCheckedChange = null) },
        onClick = { onChange(!coche) },
    )
}

/** Sheet of a tapped plot, with distance and azimuth from the position and « Y aller » (brief § 3, § 5.3). */
@Composable
private fun FeuillePlacette(p: PlacetteCarte, donnees: DonneesCarte?, guide: Guidage?, onYAller: () -> Unit, onOuvrir: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(p.plotId, style = MaterialTheme.typography.headlineSmall)
        val etat = stringResource(
            when (p.etat) {
                "provisoire" -> R.string.etat_provisoire
                "envoye" -> R.string.etat_envoye
                "definitif" -> R.string.etat_definitif
                else -> R.string.etat_a_faire
            },
        )
        val type = if (p.remplacement) stringResource(R.string.carte_type_over) else stringResource(R.string.carte_type_base)
        Text(listOfNotNull(type, etat, p.rayonM?.let { stringResource(R.string.detail_rayon, nombreCarte(it)) }).joinToString(" · "))
        val rang = donnees?.parcours?.indexOfFirst { it.plotId == p.plotId } ?: -1
        if (rang >= 0) Text(stringResource(R.string.carte_rang, rang + 1, donnees!!.parcours.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
        donnees?.centres?.firstOrNull { it.plotId == p.plotId }?.let { c ->
            Text(stringResource(R.string.carte_centre_mesure, nombreCarte(Math.round((c.ecartM ?: 0.0) * 100) / 100.0)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        guide?.let { g ->
            Text(stringResource(R.string.carte_distance, distance(g.distanceM)) + " · " + texteAzimut(g))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            OutlinedButton(onClick = onYAller) { Text(stringResource(R.string.carte_y_aller)) }
            Button(onClick = onOuvrir) { Text(stringResource(R.string.filmer)) }
        }
    }
}

/**
 * Guidance banner under the map (brief § 5.3): arrow relative to the phone,
 * distance, azimuth to sight (magnetic, in the plot's unit) and geographic;
 * « Arrivé » within 5 m. Updated at each fix and compass reading.
 */
@Composable
private fun BandeauGuidage(
    plotId: String, guide: Guidage?, fix: FixGnss?, capDeg: Float?, suivante: PlacetteCarte?,
    onSuivante: (PlacetteCarte) -> Unit, onArreter: () -> Unit, onMesurer: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.navigationBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val relatif = guide?.relatifDeg(capDeg?.toDouble())
            if (guide != null && !guide.arrive) Fleche(relatif)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    guide == null -> {
                        Text(plotId, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.carte_attente_position), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    guide.arrive -> {
                        Text(stringResource(R.string.carte_arrive, plotId), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text(stringResource(R.string.carte_distance, distance(guide.distanceM)) + " · " + texteAzimut(guide))
                        Button(onClick = onMesurer, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.carte_mesurer_centre)) }
                    }
                    else -> {
                        Text("$plotId · " + stringResource(R.string.carte_distance, distance(guide.distanceM)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(texteAzimut(guide))
                    }
                }
                fix?.precisionHorizontaleM?.takeIf { it >= 1.0 }?.let { m ->
                    val texte = if (fix.origine == OrigineFix.TELEPHONE) R.string.carte_precision_telephone else R.string.carte_precision
                    Text(stringResource(texte, nombreCarte(Math.round(m * 10) / 10.0)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (guide != null && !guide.arrive && relatif == null) {
                    Text(stringResource(R.string.carte_sans_boussole), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onArreter) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.carte_arreter_guidage)) }
                suivante?.let { s -> TextButton(onClick = { onSuivante(s) }) { Text(stringResource(R.string.carte_suivante) + " · " + s.plotId) } }
            }
        }
    }
}

/**
 * Centre measurement in progress: the receiver's fixes are averaged for
 * [dureeS] seconds (RTK fixed only, or float too when confirmed); the pole
 * must stay still. Cancelling writes nothing.
 */
@Composable
private fun DialogueMesure(plotId: String, flottant: Boolean, dureeS: Int, onFini: (CentreMesure?) -> Unit, onAnnuler: () -> Unit) {
    val mesure = remember { MesureCentre(flottant) }
    var ecoule by remember { mutableIntStateOf(0) }
    var retenus by remember { mutableIntStateOf(0) }
    var ecartes by remember { mutableIntStateOf(0) }
    var precision by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        val debut = SystemClock.elapsedRealtime()
        val ecoute = launch {
            SourcePosition.fix.collect { f ->
                if (f == null) return@collect
                if (mesure.ajouter(f)) { retenus = mesure.nombre; precision = mesure.resultat()?.precisionM } else ecartes++
            }
        }
        while (ecoule < dureeS) {
            delay(200)
            ecoule = ((SystemClock.elapsedRealtime() - debut) / 1000).toInt().coerceAtMost(dureeS)
        }
        ecoute.cancel()
        onFini(mesure.resultat())
    }
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        dismissButton = { TextButton(onClick = onAnnuler) { Text(stringResource(R.string.annuler)) } },
        title = { Text(stringResource(R.string.centre_titre, plotId)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.centre_en_cours, ecoule, dureeS))
                LinearProgressIndicator(progress = { ecoule.toFloat() / dureeS }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.centre_fixes, retenus, precision?.let { metres(it) + " m" } ?: "—"))
                if (ecartes > 0) Text(stringResource(R.string.centre_ignores, ecartes), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/** (done, total) of a running or queued tile download, else null. */
private fun avancement(info: WorkInfo?): Pair<Long, Long>? {
    if (info == null || info.state.isFinished) return null
    return info.progress.getLong(TelechargementTuiles.CLE_FAITES, 0) to info.progress.getLong(TelechargementTuiles.CLE_TOTAL, 0)
}

/**
 * « Hors ligne… » (brief § 5.2): IGN base map (and cadastre) over the
 * project's extent plus 200 m, zooms 12 to 18; estimate before, progress,
 * stop, delete.
 */
@Composable
private fun DialogueHorsLigne(
    dossierProjet: File, racine: File, donnees: DonneesCarte?, fondAffiche: Fond, cadastreAffiche: Boolean, info: WorkInfo?,
    onFermer: () -> Unit,
) {
    val ctx = LocalContext.current
    var choix by remember { mutableStateOf(if (fondAffiche.telechargeable) fondAffiche else Fond.PLAN_IGN) }
    var avecCadastre by remember { mutableStateOf(cadastreAffiche) }
    val zone = remember(donnees) { donnees?.emprise?.let { ZoneTuiles.projet(it) } }
    val sources = listOfNotNull(choix.source, SOURCE_CADASTRE.takeIf { avecCadastre })
    val nombre = (zone?.nombre ?: 0L) * sources.size
    // Rough tile sizes: IGN plan PNG, orthophoto JPEG, mostly transparent cadastre.
    val octets = (zone?.nombre ?: 0L) * ((if (choix == Fond.ORTHO_IGN) 22_000L else 18_000L) + (if (avecCadastre) 6_000L else 0L))
    var stocke by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var versionStock by remember { mutableIntStateOf(0) }
    LaunchedEffect(choix, avecCadastre, versionStock, info?.state) {
        stocke = withContext(Dispatchers.IO) {
            sources.map { TuilesHorsLigne.volume(racine, it) }.fold(0 to 0L) { a, b -> (a.first + b.first) to (a.second + b.second) }
        }
    }
    val enCours = info != null && !info.state.isFinished
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(stringResource(R.string.hl_titre)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.hl_desc), style = MaterialTheme.typography.bodySmall)
                Fond.entries.filter { it.telechargeable }.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choix == f, onClick = { if (!enCours) choix = f })
                        Text(f.libelle)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = avecCadastre, onCheckedChange = { if (!enCours) avecCadastre = it })
                    Text(stringResource(R.string.hl_cadastre))
                }
                if (fondAffiche == Fond.OSM) Text(stringResource(R.string.hl_osm), style = MaterialTheme.typography.bodySmall)
                if (nombre > ZoneTuiles.NOMBRE_MAX) {
                    Text(stringResource(R.string.hl_trop, milliers(nombre), milliers(ZoneTuiles.NOMBRE_MAX)), color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(R.string.hl_estimation, milliers(nombre), maxOf(1L, octets / 1_000_000)))
                }
                stocke?.takeIf { it.first > 0 }?.let { (n, o) ->
                    Text(stringResource(R.string.hl_stocke, milliers(n.toLong()), maxOf(1L, o / 1_000_000)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    info == null -> {}
                    info.state == WorkInfo.State.ENQUEUED && info.runAttemptCount == 0 -> Text(stringResource(R.string.hl_attente))
                    enCours -> avancement(info)?.let { (faites, total) ->
                        Text(stringResource(R.string.hl_progression, milliers(faites), milliers(total)))
                        LinearProgressIndicator(progress = { if (total > 0) faites.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth())
                    }
                    info.state == WorkInfo.State.SUCCEEDED -> Text(stringResource(R.string.hl_fini), color = Color(0xFF2E7D32))
                    else -> Text(stringResource(R.string.hl_echec), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (enCours) {
                TextButton(onClick = { TelechargementTuiles.annuler(ctx, dossierProjet) }) { Text(stringResource(R.string.hl_arreter)) }
            } else {
                TextButton(
                    enabled = zone != null && nombre in 1..ZoneTuiles.NOMBRE_MAX,
                    onClick = { zone?.let { TelechargementTuiles.planifier(ctx, dossierProjet, sources.map { it.name() }, it) } },
                ) { Text(stringResource(R.string.hl_telecharger)) }
            }
        },
        dismissButton = {
            Row {
                if (!enCours && (stocke?.first ?: 0) > 0) {
                    TextButton(onClick = { sources.forEach { TuilesHorsLigne.supprimer(racine, it) }; versionStock++ }) { Text(stringResource(R.string.hl_supprimer)) }
                }
                TextButton(onClick = onFermer) { Text(stringResource(R.string.fermer)) }
            }
        },
    )
}

/** 1850 → "1 850" (narrow no-break space, as in French). */
private fun milliers(n: Long): String = "%,d".format(java.util.Locale.FRANCE, n)

/** Steps of « Mesurer le centre ». */
private sealed interface EtapeCentre {
    val plotId: String
    data class Message(override val plotId: String, val texte: String) : EtapeCentre
    data class Flottant(override val plotId: String, val precisionM: Double?) : EtapeCentre
    data class Deja(override val plotId: String, val ecartM: Double?, val flottant: Boolean) : EtapeCentre
    data class Mesure(override val plotId: String, val flottant: Boolean) : EtapeCentre
}

/** Metres to the centimetre, French decimal comma. */
private fun metres(m: Double): String = nombreCarte(Math.round(m * 100) / 100.0)

/** Arrow towards the plot, relative to the top of the phone; grey and upright without a heading. */
@Composable
private fun Fleche(relatifDeg: Double?) {
    val couleur = if (relatifDeg == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(52.dp).rotate((relatifDeg ?: 0.0).toFloat())) {
        val w = size.width; val h = size.height
        val fleche = Path().apply {
            moveTo(w / 2, 0f); lineTo(w * 0.9f, h * 0.9f); lineTo(w / 2, h * 0.68f); lineTo(w * 0.1f, h * 0.9f); close()
        }
        drawPath(fleche, couleur)
        drawCircle(couleur.copy(alpha = 0.15f), radius = w / 2, center = Offset(w / 2, h / 2))
    }
}

/** "Azimut 358° magnétique (0° géographique)", in the plot's unit. */
@Composable
private fun texteAzimut(g: Guidage): String {
    fun u(v: Int) = if (g.unite == "gr") "$v gr" else "$v°"
    return if (g.azimutMagDeg != null) stringResource(R.string.carte_azimut_mag, u(g.azimutVise), u(g.azimutGeo))
    else stringResource(R.string.carte_azimut_geo, u(g.azimutGeo))
}

/** Distance rounded to the decimetre under 10 m, to the metre beyond. */
private fun distance(m: Double): String = if (m < 10) nombreCarte(Math.round(m * 10) / 10.0) else Math.round(m).toString()

/** Starting the guidance: the position and the plot both in view, else the plot alone. */
private fun montrer(mapView: MapView, fix: FixGnss?, pl: PlacetteCarte) {
    if (fix == null) {
        if (mapView.zoomLevelDouble < 17) mapView.controller.setZoom(17.0)
        mapView.controller.animateTo(GeoPoint(pl.lat, pl.lon))
        return
    }
    val lat = fix.position.latitude; val lon = fix.position.longitude
    val m = 0.0004
    mapView.zoomToBoundingBox(BoundingBox(maxOf(lat, pl.lat) + m, maxOf(lon, pl.lon) + m, minOf(lat, pl.lat) - m, minOf(lon, pl.lon) - m), true)
}

private fun cadrer(mapView: MapView, d: DonneesCarte, anime: Boolean) {
    val e = d.emprise ?: return
    // Margin of a few percent; a single plot gets a 100 m box.
    val mx = maxOf((e.est - e.ouest) * 0.08, 0.0007)
    val my = maxOf((e.nord - e.sud) * 0.08, 0.0005)
    val boite = BoundingBox(e.nord + my, e.est + mx, e.sud - my, e.ouest - mx)
    // Before the first layout the view has no size: wait for it.
    if (mapView.width == 0) mapView.addOnFirstLayoutListener { _, _, _, _, _ -> mapView.zoomToBoundingBox(boite, false) }
    else mapView.zoomToBoundingBox(boite, anime)
}

private fun nombreCarte(x: Double): String = if (x % 1.0 == 0.0) x.toInt().toString() else x.toString().replace('.', ',')

/** « Ma position » button: off, centred once, following the position. */
private enum class Suivi { AUCUN, CENTRE, SUIVI }

private const val PREF_FOND = "carte_fond"
private const val PREF_CADASTRE = "carte_cadastre"

/** Material "Navigation" arrow, distinct from the position pin (material-icons-extended not needed). */
private val IconeYAller = materialIcon(name = "Groma.YAller") {
    materialPath {
        moveTo(12f, 2f)
        lineTo(4.5f, 20.29f)
        lineToRelative(0.71f, 0.71f)
        lineTo(12f, 18f)
        lineToRelative(6.79f, 3f)
        lineToRelative(0.71f, -0.71f)
        close()
    }
}
