package groma.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import groma.app.R
import groma.app.carte.CoucheParcelles
import groma.app.carte.CouchePlacettes
import groma.app.carte.Fond
import groma.app.carte.SOURCE_CADASTRE
import groma.app.carte.ZOOM_MAX
import groma.app.carte.ZOOM_TUILES
import groma.core.DonneesCarte
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
 * base map, IGN cadastre, parcels, visiting route, plots by state, measured
 * centres. A tap on a plot opens its sheet.
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
    var menu by remember { mutableStateOf(false) }
    var choisie by remember { mutableStateOf<PlacetteCarte?>(null) }
    val densite = ctx.resources.displayMetrics.density

    val placettes = remember { CouchePlacettes(densite) { choisie = it } }
    val parcelles = remember { CoucheParcelles(densite) }
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
            overlays += placettes
            overlays += ScaleBarOverlay(this).apply { setAlignBottom(true) }
        }
    }
    // Scale bar kept above the attribution and the navigation bar (edge-to-edge).
    val basNav = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    LaunchedEffect(basNav) {
        mapView.overlays.filterIsInstance<ScaleBarOverlay>().forEach { it.setScaleBarOffset((16 * densite).toInt(), basNav + (36 * densite).toInt()) }
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
    LaunchedEffect(donnees, couchePlacettes, coucheParcours, coucheCentres, coucheParcelles, choisie) {
        placettes.placettes = donnees?.placettes.orEmpty()
        placettes.parcours = donnees?.parcours.orEmpty()
        placettes.centres = donnees?.centres.orEmpty()
        placettes.afficherPlacettes = couchePlacettes
        placettes.afficherParcours = coucheParcours
        placettes.afficherCentres = coucheCentres
        placettes.selection = choisie?.plotId
        parcelles.parcelles = donnees?.parcelles.orEmpty()
        parcelles.isEnabled = coucheParcelles
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
                        Case(stringResource(R.string.carte_cadastre), cadastre) { cadastre = it }
                    }
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // osmdroid draws tiles beyond its bounds: clipped, or they cover the title bar.
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().clipToBounds())
            Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                SmallFloatingActionButton(onClick = { mapView.controller.zoomIn() }) { Text("+", fontSize = 22.sp) }
                SmallFloatingActionButton(onClick = { mapView.controller.zoomOut() }) { Text("−", fontSize = 22.sp) }
                SmallFloatingActionButton(onClick = { donnees?.let { cadrer(mapView, it, anime = true) } }) {
                    Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.carte_cadrer))
                }
            }
            Text(
                fond.attribution + if (cadastre && fond == Fond.OSM) " · cadastre © IGN" else "",
                fontSize = 10.sp,
                color = Color(0xFF212121),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    .background(Color(0xCCFFFFFF), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }

    choisie?.let { pl ->
        ModalBottomSheet(onDismissRequest = { choisie = null }) {
            FeuillePlacette(pl, donnees, onOuvrir = { choisie = null; onOuvrirPlacette(pl.plotId) })
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

/** Sheet of a tapped plot. Distance, azimuth and "Y aller" come with the GNSS (brief § 5.3). */
@Composable
private fun FeuillePlacette(p: PlacetteCarte, donnees: DonneesCarte?, onOuvrir: () -> Unit) {
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
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Button(onClick = onOuvrir) { Text(stringResource(R.string.filmer)) }
        }
    }
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

private const val PREF_FOND = "carte_fond"
private const val PREF_CADASTRE = "carte_cadastre"
