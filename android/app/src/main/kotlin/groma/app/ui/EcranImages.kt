package groma.app.ui

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import groma.app.Projet
import groma.app.R
import groma.core.ArbreLocal
import groma.core.Essences
import groma.core.GroupeEssence
import groma.core.ManifestePaquet
import groma.core.PaquetZip
import groma.core.PlacementPaquet
import groma.core.SolLocal
import groma.core.SurimpressionImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Why there is no tree (or a caveat) under the images; explicit, no magic string. */
private sealed interface Avis {
    data object Aucun : Avis
    data object SansSol : Avis
    class Texte(val texte: String) : Avis
}

/**
 * Trees of the plot (false positives included, flagged) placed in the local frame of the capture, or the reason
 * they are not; [note] is a discreet caveat (targets taken from the package).
 */
private class ArbresSurImages(val arbres: List<ArbreLocal>, val avis: Avis, val note: String? = null)

/**
 * Mission 045: the kept images of a package, one at a time (swipe or slider), with the trees of the plot
 * drawn over them (a vertical line from the foot to 1.30 m, "number · diameter") and the targets C0 to C3
 * detected on the image. Read only: the package is read entry by entry, the GeoPackage opened read-only.
 */
@Composable
fun EcranImages(plotId: String, projet: Projet, paquet: File, onRetour: () -> Unit) {
    val zip by produceState<Result<PaquetZip>?>(null, paquet) {
        value = withContext(Dispatchers.IO) { runCatching { PaquetZip(paquet) } }
        awaitDispose { value?.getOrNull()?.close() }
    }
    val manifeste by produceState<Result<ManifestePaquet>?>(null, zip) {
        val z = zip?.getOrNull() ?: return@produceState
        value = withContext(Dispatchers.Default) { runCatching { z.manifeste() } }
    }
    // Placement: heavy (depth maps read one by one), so after the first image is shown.
    val arbres by produceState<ArbresSurImages?>(null, manifeste) {
        val z = zip?.getOrNull() ?: return@produceState
        val m = manifeste?.getOrNull() ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                // Project first (latest measured targets and centre), the manifest's own only without targets there.
                val (liste, reference) = withContext(Dispatchers.IO) { projet.pourImages(plotId, avecFauxPositifs = true) }
                val place = PlacementPaquet.calculer(m, reference)
                val geo = place.georef ?: return@runCatching ArbresSurImages(emptyList(), place.message?.let { Avis.Texte(it) } ?: Avis.Aucun, place.note)
                val locaux = liste.associate { a -> a.uuid to geo.versLocal(a.x, a.y) }
                val sols = SolLocal.estimer(locaux, m.images) { im -> im.profondeur?.let { z.octets(it) } }
                val avis = if (sols.isEmpty()) Avis.SansSol else place.message?.let { Avis.Texte(it) } ?: Avis.Aucun
                ArbresSurImages(if (sols.isEmpty()) emptyList() else SurimpressionImage.enLocal(liste, geo, sols), avis, place.note)
            }.getOrElse { ArbresSurImages(emptyList(), Avis.Texte(it.message ?: it.javaClass.simpleName)) }
        }
    }
    val couleurs = GroupeEssence.entries.associateWith { it.couleur() }
    var fauxPositifs by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = margesContenu(),
        topBar = {
            BandeauCompact(
                titre = stringResource(R.string.placette_titre, plotId),
                navigationIcon = {
                    IconButton(onClick = onRetour) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retour)) }
                },
                actions = {},
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val erreur = zip?.exceptionOrNull() ?: manifeste?.exceptionOrNull()
            val m = manifeste?.getOrNull()
            val z = zip?.getOrNull()
            when {
                erreur != null -> Text(stringResource(R.string.images_illisible, erreur.message ?: erreur.javaClass.simpleName), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                m == null || z == null -> Text(stringResource(R.string.images_chargement), modifier = Modifier.padding(16.dp))
                m.retenues.isEmpty() -> Text(stringResource(R.string.images_vide), modifier = Modifier.padding(16.dp))
                else -> Images(m, z, arbres, couleurs, fauxPositifs, { fauxPositifs = it }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Images(m: ManifestePaquet, zip: PaquetZip, arbres: ArbresSurImages?, couleurs: Map<GroupeEssence, Color>, fauxPositifs: Boolean, onFauxPositifs: (Boolean) -> Unit, modifier: Modifier) {
    val retenues = m.retenues
    val pager = rememberPagerState { retenues.size }
    val scope = rememberCoroutineScope()
    val dp = LocalDensity.current.density
    val blanc = Color.White; val noir = Color.Black; val gris = Color(0xFFB0B0B0)
    Column(modifier) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().background(noir)) { page ->
            val im = retenues[page]
            val bitmap by produceState<ImageBitmap?>(null, im.id) {
                value = withContext(Dispatchers.IO) {
                    runCatching { zip.octets(im.image!!)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
                }
            }
            val surimpression = remember(im.id, arbres, fauxPositifs) {
                SurimpressionImage.calculer(im, m, arbres?.arbres.orEmpty().filter { fauxPositifs || !it.arbre.fauxPositif })
            }
            val k = im.k!!
            Box(Modifier.fillMaxSize()) {
                bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                Canvas(Modifier.fillMaxSize()) {
                    val e = min(size.width / k.largeur, size.height / k.hauteur)
                    val ox = (size.width - k.largeur * e) / 2; val oy = (size.height - k.hauteur * e) / 2
                    fun pt(u: Double, v: Double) = Offset(ox + u.toFloat() * e, oy + v.toFloat() * e)
                    val texte = Paint().apply { textSize = 14 * dp; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; color = blanc.toArgb() }
                    val contour = Paint(texte).apply { style = Paint.Style.STROKE; strokeWidth = 3 * dp; color = noir.toArgb() }
                    fun etiquette(s: String, o: Offset, couleur: Color = blanc, barre: Boolean = false) = drawIntoCanvas {
                        texte.color = couleur.toArgb()
                        it.nativeCanvas.drawText(s, o.x, o.y, contour); it.nativeCanvas.drawText(s, o.x, o.y, texte)
                        if (barre) {
                            val l = texte.measureText(s); val y = o.y - 5 * dp
                            it.nativeCanvas.drawLine(o.x, y, o.x + l, y, contour); it.nativeCanvas.drawLine(o.x, y, o.x + l, y, texte.apply { strokeWidth = 2 * dp })
                        }
                    }
                    val tirets = PathEffect.dashPathEffect(floatArrayOf(10 * dp, 7 * dp))
                    for (t in surimpression.arbres) {
                        val pied = pt(t.piedU, t.piedV); val tete = pt(t.teteU, t.teteV)
                        val ou = Offset(tete.x + 6 * dp, tete.y - 4 * dp)
                        when {
                            // False positive set aside: grey, crossed out.
                            t.arbre.fauxPositif -> {
                                drawLine(noir, pied, tete, strokeWidth = 5 * dp)
                                drawLine(gris, pied, tete, strokeWidth = 2.5f * dp)
                                val m2 = Offset((pied.x + tete.x) / 2, (pied.y + tete.y) / 2); val r = 9 * dp
                                drawLine(gris, Offset(m2.x - r, m2.y - r), Offset(m2.x + r, m2.y + r), strokeWidth = 2.5f * dp)
                                drawLine(gris, Offset(m2.x - r, m2.y + r), Offset(m2.x + r, m2.y - r), strokeWidth = 2.5f * dp)
                                etiquette(t.etiquette, ou, gris, barre = true)
                            }
                            // Tree added by hand: dashed, provisional.
                            t.arbre.ajoute -> {
                                val c = couleurs.getValue(Essences.groupe(t.arbre.essence))
                                drawLine(noir, pied, tete, strokeWidth = 7 * dp, pathEffect = tirets)
                                drawLine(c, pied, tete, strokeWidth = 4 * dp, pathEffect = tirets)
                                etiquette(t.etiquette, ou)
                            }
                            else -> {
                                val c = couleurs.getValue(Essences.groupe(t.arbre.essence))
                                drawLine(noir, pied, tete, strokeWidth = 7 * dp)
                                drawLine(c, pied, tete, strokeWidth = 4 * dp)
                                etiquette(t.etiquette, ou)
                            }
                        }
                    }
                    // Targets: what placed the trees (12 dp square, name beside it).
                    for (c in surimpression.cibles) {
                        val o = pt(c.u, c.v); val cote = 12 * dp
                        drawRect(noir, Offset(o.x - cote / 2 - dp, o.y - cote / 2 - dp), Size(cote + 2 * dp, cote + 2 * dp), style = Stroke(4 * dp))
                        drawRect(blanc, Offset(o.x - cote / 2, o.y - cote / 2), Size(cote, cote), style = Stroke(2 * dp))
                        etiquette(c.idCible, Offset(o.x + cote, o.y + 5 * dp))
                    }
                }
            }
        }
        // Why there is no tree on the images, when that is the case (one line, no blame).
        val avis = when {
            arbres == null -> stringResource(R.string.images_calcul_arbres)
            arbres.avis is Avis.SansSol -> stringResource(R.string.images_sans_sol)
            arbres.avis is Avis.Texte -> arbres.avis.texte
            else -> null
        }
        // Targets taken from the package (the project has none for the plot): said once, discreetly.
        val lignes = listOfNotNull(avis, arbres?.note)
        if (lignes.isNotEmpty()) Text(lignes.joinToString(" — "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().toggleable(fauxPositifs, role = Role.Checkbox, onValueChange = onFauxPositifs).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(fauxPositifs, onCheckedChange = null)
            Text(stringResource(R.string.images_faux_positifs), modifier = Modifier.padding(start = 8.dp))
        }
        if (retenues.size > 1) {
            Slider(
                value = pager.currentPage.toFloat(),
                onValueChange = { v -> scope.launch { pager.scrollToPage(v.roundToInt()) } },
                valueRange = 0f..(retenues.size - 1).toFloat(),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Text(
            stringResource(R.string.images_numero, pager.currentPage + 1, retenues.size),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
