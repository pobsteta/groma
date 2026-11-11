package groma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import groma.app.BoussoleMagnetique
import groma.app.EtalonnageBoussole
import groma.app.LectureBoussoleMagnetique
import groma.app.R
import groma.core.ArbrePlacette
import groma.core.CiblePlacee
import groma.core.PlacementCible
import groma.core.SourceAzimut
import groma.core.TypeAlerte
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Vue « Placette » (missions 016 and 019). After the centre is measured (C0, on the pole), C1 to
 * C3 are placed on a plan of the plot (north up) from the azimuth (typed from the sighting compass
 * by default, or read live on the phone compass), the horizontal distance and the diameter.
 * [onPlacer] validates and writes the target to the project; it throws on a refused input.
 * The plan also shows the trees of the plot ([arbresInitiaux]); touching one opens its form
 * ([FicheArbre]), whose "Enregistrer" calls [onEnregistrerArbre] (writes through the 018 API and
 * returns the trees read back).
 */
@Composable
fun EcranPlacette(
    plotId: String,
    unite: String,
    rayonM: Double,
    declinaisonDeg: Double,
    convergenceDeg: Double,
    centreMesure: Boolean,
    initiales: List<CiblePlacee>,
    centre: Pair<Double, Double>,
    arbresInitiaux: List<ArbrePlacette>,
    onEnregistrerArbre: suspend (uuid: String, saisie: Map<String, String?>) -> List<ArbrePlacette>,
    onPlacer: suspend (id: String, azimut: Double, distanceM: Double, dbhCm: Double, source: SourceAzimut) -> CiblePlacee,
    onFilmer: () -> Unit,
    onRetour: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cibles by remember { mutableStateOf(initiales) }
    var arbres by remember { mutableStateOf(arbresInitiaux) }
    var fiche by remember { mutableStateOf<String?>(null) }
    var choisie by remember { mutableStateOf("C1") }
    var azimutTexte by remember { mutableStateOf("") }
    var distanceTexte by remember { mutableStateOf("") }
    var dbhTexte by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(SourceAzimut.BOUSSOLE_VISEE) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var telephone by remember { mutableStateOf(false) }
    var lecture by remember { mutableStateOf(LectureBoussoleMagnetique(null, EtalonnageBoussole.A_ETALONNER)) }
    var sansCapteur by remember { mutableStateOf(false) }

    DisposableEffect(telephone) {
        val b = if (telephone) BoussoleMagnetique(ctx) { lecture = it } else null
        sansCapteur = telephone && b?.disponible != true
        onDispose { b?.close() }
    }

    fun nombre(t: String) = t.trim().replace(',', '.').toDoubleOrNull()
    fun formater(x: Double) = (Math.round(x * 10) / 10.0).toString().replace('.', ',')
    val uniteTexte = if (unite == "gr") "gr" else "°"

    // Pre-fill the fields with the target already placed under the chosen name.
    fun remplir(id: String) {
        choisie = id; erreur = null
        val c = cibles.firstOrNull { it.id == id }
        azimutTexte = c?.let { formater(PlacementCible.azimutAffiche(it.azimutDeg, unite)) } ?: ""
        distanceTexte = c?.let { formater(it.distanceM) } ?: ""
        dbhTexte = c?.let { formater(it.dbhCm) } ?: ""
        source = c?.source ?: SourceAzimut.BOUSSOLE_VISEE
    }

    LaunchedEffect(Unit) { remplir("C1") }

    Scaffold(
        contentWindowInsets = margesContenu(),
        topBar = {
            BandeauCompact(
                titre = stringResource(R.string.placette_titre, plotId),
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retour))
                    }
                },
                actions = {},
            )
        },
    ) { padding ->
        fiche?.let { uuid ->
            arbres.firstOrNull { it.uuid == uuid }?.let { a ->
                FicheArbre(
                    arbre = a,
                    suivant = ArbrePlacette.suivant(arbres, uuid),
                    onEnregistrer = { saisie -> if (saisie.isNotEmpty()) arbres = onEnregistrerArbre(uuid, saisie) },
                    onOuvrir = { fiche = it.uuid },
                    onFermer = { fiche = null },
                )
            }
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!centreMesure) {
                Text(stringResource(R.string.cibles_centre_prevu), color = MaterialTheme.colorScheme.error)
            }
            Plan(rayonM, unite, declinaisonDeg, convergenceDeg, cibles, choisie, arbres, centre, fiche) { fiche = it.uuid }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlacementCible.IDENTIFIANTS.forEach { id ->
                    FilterChip(
                        selected = choisie == id,
                        onClick = { remplir(id) },
                        label = { Text(if (cibles.any { it.id == id }) "$id ✓" else id) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !telephone, onClick = { telephone = false },
                    label = { Text(stringResource(R.string.cibles_boussole_visee)) })
                FilterChip(selected = telephone, onClick = { telephone = true },
                    label = { Text(stringResource(R.string.cibles_boussole_telephone)) })
            }
            if (telephone) {
                if (sansCapteur) {
                    Text(stringResource(R.string.cibles_sans_capteur), color = MaterialTheme.colorScheme.error)
                } else {
                    val cap = lecture.capDeg
                    Text(
                        stringResource(R.string.cibles_lecture, cap?.let { formater(PlacementCible.azimutAffiche(it, unite)) } ?: "…", uniteTexte),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(
                            when (lecture.etalonnage) {
                                EtalonnageBoussole.BON -> R.string.cibles_etalonnage_bon
                                EtalonnageBoussole.MOYEN -> R.string.cibles_etalonnage_moyen
                                EtalonnageBoussole.FAIBLE -> R.string.cibles_etalonnage_faible
                                EtalonnageBoussole.A_ETALONNER -> R.string.cibles_etalonnage_a_faire
                            },
                        ),
                        color = if (lecture.etalonnage == EtalonnageBoussole.BON) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        enabled = cap != null,
                        onClick = {
                            cap?.let {
                                azimutTexte = formater(PlacementCible.azimutAffiche(it, unite))
                                source = SourceAzimut.BOUSSOLE_TELEPHONE
                            }
                        },
                    ) { Text(stringResource(R.string.cibles_prendre_cap)) }
                }
            }

            OutlinedTextField(
                value = azimutTexte,
                onValueChange = { azimutTexte = it; source = SourceAzimut.BOUSSOLE_VISEE },
                label = { Text(stringResource(R.string.cibles_azimut, if (unite == "gr") "gr" else "°")) },
                supportingText = { Text(stringResource(if (source == SourceAzimut.BOUSSOLE_TELEPHONE) R.string.cibles_source_telephone else R.string.cibles_source_visee)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = distanceTexte, onValueChange = { distanceTexte = it },
                    label = { Text(stringResource(R.string.cibles_distance)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = dbhTexte, onValueChange = { dbhTexte = it },
                    label = { Text(stringResource(R.string.cibles_diametre)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val az = nombre(azimutTexte); val d = nombre(distanceTexte); val dbh = nombre(dbhTexte)
                    if (az == null || d == null || dbh == null) { erreur = ctx.getString(R.string.cibles_saisie_incomplete); return@Button }
                    scope.launch {
                        try {
                            val c = onPlacer(choisie, az, d, dbh, source)
                            cibles = PlacementCible.placer(cibles, c)
                            erreur = null
                        } catch (e: IllegalArgumentException) {
                            erreur = e.message
                        } catch (e: Exception) {
                            erreur = ctx.getString(R.string.cibles_erreur, e.message ?: e.javaClass.simpleName)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.cibles_placer, choisie)) }

            PlacementCible.alertes(cibles).forEach { a ->
                val noms = a.cibles.joinToString(" et ")
                Text(
                    when (a.type) {
                        TypeAlerte.ECART_ANGULAIRE -> stringResource(R.string.cibles_alerte_ecart, noms, PlacementCible.ECART_ANGULAIRE_MIN_DEG.toInt())
                        TypeAlerte.TROP_PROCHE_DU_CENTRE -> stringResource(R.string.cibles_alerte_proche, noms, PlacementCible.DISTANCE_MIN_CENTRE_M.toInt())
                        TypeAlerte.MOINS_DE_TROIS_CIBLES -> stringResource(R.string.cibles_alerte_moins_de_trois)
                    },
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Button(onClick = onFilmer, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Text(stringResource(R.string.filmer)) }
        }
    }
}
