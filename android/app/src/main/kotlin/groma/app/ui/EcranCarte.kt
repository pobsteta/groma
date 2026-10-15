package groma.app.ui

import android.Manifest
import android.content.Context
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
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import groma.app.R
import groma.app.carte.Boussole
import groma.app.carte.CoucheGuidage
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
import groma.core.OrigineFix
import groma.core.PlacetteCarte
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
 * to a plot, line on the map and arrow relative to the phone (§ 5.3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranCarte(
    titre: String,
    donnees: DonneesCarte?,
    onRetour: () -> Unit,
    onOuvrirPlacette: (String) -> Unit,
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

    val placettes = remember { CouchePlacettes(densite) { choisie = it } }
    val position = remember { CouchePosition(densite) }
    val guidage = remember { CoucheGuidage(densite) }
    val parcelles = remember { coucheParcelles(densite) }
    val ugf = remember { coucheUgf(densite) }
    val couchCadastre = remember {
        TilesOverlay(MapTileProviderBasic(ctx.applicationContext, SOURCE_CADASTRE), ctx.applicationContext).apply {
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            loadingLineColor = android.graphics.Color.TRANSPARENT
        }
    }
    val mapView = remember {
        MapView(ctx).apply {
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
                        Icon(Icons.Filled.Place, contentDescription = stringResource(R.string.carte_prochaine))
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
            BadgeFix(fix, etatRtk, Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) { etatGnss = true }
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
            )
        }
    }

    if (etatGnss) DialogueEtatGnss(fix, etatRtk) { etatGnss = false }
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
    onSuivante: (PlacetteCarte) -> Unit, onArreter: () -> Unit,
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
