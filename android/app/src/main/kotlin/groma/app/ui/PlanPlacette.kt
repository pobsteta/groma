package groma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt
import groma.core.CouchesPlan
import groma.core.PlanCouches
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import groma.app.R
import groma.core.ArbrePlacette
import groma.core.CiblePlacee
import groma.core.Essences
import groma.core.GroupeEssence
import groma.core.PlacementCible
import kotlin.math.hypot
import kotlin.math.max

/** Mission 042: layers ticked on the plan, kept for the session (not across launches), same in both modes. */
private var voirRelief by mutableStateOf(true)
private var voirHouppiers by mutableStateOf(true)
private var voirSommets by mutableStateOf(true)

/** Opacity of the CVAT relief (provisional value of mission 042, frozen after the S23 trial). */
private const val OPACITE_RELIEF = 0.5f

/** A layer set read in the background, with the relief already turned into a bitmap. */
private class CouchesPretes(val couches: CouchesPlan, val relief: ImageBitmap?)

/**
 * Plan of the plot, north up: circle of the radius, C0 at the centre, targets (C1 to C3) and the
 * trees at their Lambert-93 offsets from [centre]. Pinch or +/- to zoom, drag to move, touch a
 * tree to open its form. A pastille takes the colour of the species group (grey without species),
 * a size growing with the diameter (bounded) and a thick dark outline when the diameter was
 * measured with the tape, a dashed purple outline for a tree added by hand (mission 023), and the
 * preview of the tree being added ([apercu], offset in metres east/north of the centre, dashed circle); the "number – diameter" label is drawn where it does not overlap another.
 * Mission 042: under all that, three layers to tick under the plan: CVAT relief (grey, half transparent), LiDAR
 * crown outlines and crown tops, read by [chargerCouches] (given the half-side in metres of the box around the
 * centre) once per plot, off the main thread; a layer without data has no tick box.
 */
@Composable
internal fun Plan(
    rayonM: Double, unite: String, decl: Double, conv: Double, cibles: List<CiblePlacee>, choisie: String,
    arbres: List<ArbrePlacette>, centre: Pair<Double, Double>, arbreChoisi: String?,
    apercu: Pair<Double, Double>? = null, chargerCouches: ((Double) -> CouchesPlan)? = null, onArbre: (ArbrePlacette) -> Unit,
) {
    val couleurCercle = MaterialTheme.colorScheme.outline
    val couleurTexte = MaterialTheme.colorScheme.onSurface
    val couleurChoisie = MaterialTheme.colorScheme.primary
    val couleurCible = MaterialTheme.colorScheme.tertiary
    val couleursGroupes = GroupeEssence.entries.associateWith { it.couleur() }
    val contourEstime = colorResource(R.color.arbre_contour_estime)
    val contourMesure = colorResource(R.color.arbre_contour_mesure)
    val halo = colorResource(R.color.arbre_choisi)
    val contourAjoute = colorResource(R.color.arbre_contour_ajoute)
    val couleurApercu = colorResource(R.color.arbre_apercu)
    val dp = LocalDensity.current.density
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // A target read from an older project may be out of range: it is left off the plan, not fatal.
    val positions = cibles.mapNotNull { c ->
        runCatching { c to PlacementCible.position(Pair(0.0, 0.0), PlacementCible.azimutAffiche(c.azimutDeg, unite), unite, decl, conv, c.distanceM) }.getOrNull()
    }
    // Trees as offsets (east, north) in metres from the centre.
    val decalages = arbres.map { it to Offset((it.x - centre.first).toFloat(), (it.y - centre.second).toFloat()) }
    val etendue = max(
        rayonM,
        max(max(positions.maxOfOrNull { (c, _) -> c.distanceM } ?: 0.0, apercu?.let { hypot(it.first, it.second) } ?: 0.0), decalages.maxOfOrNull { (_, o) -> hypot(o.x, o.y).toDouble() } ?: 0.0),
    ) * 1.1

    // The box is rounded up to 10 m and wider than the plan, so the relief does not reload while a preview moves.
    val demiCouches = ceil(etendue * 1.5 / 10.0) * 10.0
    val pretes by produceState<CouchesPretes?>(null, centre, demiCouches, chargerCouches) {
        val charger = chargerCouches ?: return@produceState
        val lues = withContext(Dispatchers.IO) {
            runCatching {
                val c = charger(demiCouches)
                val g = c.relief
                val image = g?.let { r ->
                    android.graphics.Bitmap.createBitmap(PlanCouches.argbRelief(r), r.largeur, r.hauteur, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
                }
                CouchesPretes(c, image)
            }.getOrNull()
        }
        if (lues != null) value = lues
    }
    val couleurHouppier = colorResource(R.color.houppier_contour)
    val aRelief = pretes?.relief != null
    val aHouppiers = pretes?.couches?.houppiers?.isNotEmpty() == true
    val aSommets = pretes?.couches?.aSommets == true

    fun echelleDe(taille: Size) = (taille.minDimension / 2 / etendue).toFloat()
    fun versEcran(o: Offset, taille: Size, z: Float, p: Offset): Offset {
        val e = echelleDe(taille) * z
        return Offset(taille.width / 2 + p.x + o.x * e, taille.height / 2 + p.y - o.y * e)
    }

    val courant = rememberUpdatedState(Triple(zoom, pan, decalages))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { c, dpan, z, _ ->
                        val nz = (zoom * z).coerceIn(1f, 12f)
                        val rel = c - Offset(size.width / 2f, size.height / 2f)
                        // Keep the point under the fingers fixed while zooming, then follow the drag.
                        pan = rel - (rel - pan) * (nz / zoom) + dpan
                        zoom = nz
                    }
                }
                .pointerInput(etendue) {
                    detectTapGestures { t ->
                        val (z, p, liste) = courant.value
                        val taille = Size(size.width.toFloat(), size.height.toFloat())
                        liste.map { (a, o) -> a to (versEcran(o, taille, z, p) - t).getDistance() - ArbrePlacette.rayonDp(a.dbhCm) * dp }
                            .filter { it.second < 14 * dp }.minByOrNull { it.second }?.let { onArbre(it.first) }
                    }
                },
        ) {
            val e = echelleDe(size) * zoom
            val c0 = versEcran(Offset.Zero, size, zoom, pan)
            // Mission 042, bottom to top: CVAT relief, crown outlines, crown tops; then the circle, targets and trees.
            pretes?.let { l ->
                val centreXY = centre
                if (voirRelief) l.relief?.let { img ->
                    val g = l.couches.relief!!
                    val (ouest, nord, est, sud) = PlanCouches.rectangleRelief(g, centreXY)
                    val hg = versEcran(Offset(ouest.toFloat(), nord.toFloat()), size, zoom, pan)
                    val bd = versEcran(Offset(est.toFloat(), sud.toFloat()), size, zoom, pan)
                    drawImage(
                        img, IntOffset.Zero, IntSize(img.width, img.height),
                        IntOffset(hg.x.roundToInt(), hg.y.roundToInt()), IntSize((bd.x - hg.x).roundToInt().coerceAtLeast(1), (bd.y - hg.y).roundToInt().coerceAtLeast(1)),
                        OPACITE_RELIEF, filterQuality = FilterQuality.Low,
                    )
                }
                if (voirHouppiers) l.couches.houppiers.forEach { h ->
                    val chemin = Path()
                    h.anneau.forEachIndexed { i, (x, y) ->
                        val (ex, ny) = PlanCouches.decalage(x, y, centreXY)
                        val p = versEcran(Offset(ex.toFloat(), ny.toFloat()), size, zoom, pan)
                        if (i == 0) chemin.moveTo(p.x, p.y) else chemin.lineTo(p.x, p.y)
                    }
                    chemin.close()
                    drawPath(chemin, couleurHouppier, style = Stroke(1.5f * dp))
                }
                if (voirSommets) l.couches.houppiers.forEach { h ->
                    h.sommet?.let { (x, y) ->
                        val (ex, ny) = PlanCouches.decalage(x, y, centreXY)
                        drawCircle(couleurHouppier, 3 * dp, versEcran(Offset(ex.toFloat(), ny.toFloat()), size, zoom, pan))
                    }
                }
            }
            drawCircle(couleurCercle, rayonM.toFloat() * e, c0, style = Stroke(2f))
            drawCircle(couleurTexte, 6f, c0)
            val peinture = android.graphics.Paint().apply { textSize = 36f; color = couleurTexte.toArgb(); isAntiAlias = true }
            val peintureArbre = android.graphics.Paint().apply { textSize = 13 * dp; color = couleurTexte.toArgb(); isAntiAlias = true; isFakeBoldText = true }
            drawIntoCanvas { cv ->
                cv.nativeCanvas.drawText("N", size.width / 2 - 12f, 40f, peinture)
                cv.nativeCanvas.drawText("C0", c0.x + 12f, c0.y - 10f, peinture)
            }
            positions.forEach { (c, p) ->
                val o = versEcran(Offset(p.first.toFloat(), p.second.toFloat()), size, zoom, pan)
                drawLine(couleurCible.copy(alpha = 0.4f), c0, o, 2f)
                drawCircle(if (c.id == choisie) couleurChoisie else couleurCible, 12f, o)
                drawIntoCanvas { cv -> cv.nativeCanvas.drawText(c.id, o.x + 16f, o.y - 8f, peinture) }
            }
            // Big trees first, so a small one next to it stays visible; the selected tree is drawn last.
            val ordre = decalages.sortedWith(compareBy({ it.first.uuid == arbreChoisi }, { -(it.first.dbhCm ?: 0.0) }))
            ordre.forEach { (a, o) ->
                val pt = versEcran(o, size, zoom, pan)
                val r = ArbrePlacette.rayonDp(a.dbhCm) * dp
                drawCircle(couleursGroupes.getValue(Essences.groupe(a.essence)), r, pt)
                if (a.ajoute) {
                    drawCircle(contourAjoute, r, pt, style = Stroke(3f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * dp, 3f * dp))))
                } else if (a.dbhMesure) drawCircle(contourMesure, r, pt, style = Stroke(3f * dp)) else drawCircle(contourEstime, r, pt, style = Stroke(1.5f * dp))
                if (a.uuid == arbreChoisi) drawCircle(halo, r + 5 * dp, pt, style = Stroke(3f * dp))
            }
            apercu?.let { (ex, ny) ->
                val pt = versEcran(Offset(ex.toFloat(), ny.toFloat()), size, zoom, pan)
                drawLine(couleurApercu.copy(alpha = 0.5f), c0, pt, 2f)
                drawCircle(couleurApercu, 10 * dp, pt, style = Stroke(3f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * dp, 3f * dp))))
            }
            // Labels, the selected tree first, skipping any that would overlap one already placed.
            val posees = mutableListOf<android.graphics.RectF>()
            drawIntoCanvas { cv ->
                decalages.sortedBy { it.first.uuid != arbreChoisi }.forEach { (a, o) ->
                    val pt = versEcran(o, size, zoom, pan)
                    val texte = a.dbhCm?.let { "${a.numero} – ${Math.round(it)}" } ?: a.numero
                    val l = peintureArbre.measureText(texte)
                    val h = peintureArbre.textSize
                    val x = pt.x + ArbrePlacette.rayonDp(a.dbhCm) * dp + 3 * dp
                    val rect = android.graphics.RectF(x, pt.y - h / 2, x + l, pt.y + h / 2)
                    val dedans = rect.left >= 0 && rect.right <= size.width && rect.top >= 0 && rect.bottom <= size.height
                    if (dedans && posees.none { android.graphics.RectF.intersects(it, rect) }) {
                        posees += rect
                        cv.nativeCanvas.drawText(texte, x, pt.y + h / 3, peintureArbre)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { zoom = (zoom / 1.5f).coerceAtLeast(1f); if (zoom == 1f) pan = Offset.Zero else pan /= 1.5f }) { Text("−") }
            OutlinedButton(onClick = { zoom = (zoom * 1.5f).coerceAtMost(12f); pan *= 1.5f }) { Text("+") }
            OutlinedButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text(stringResource(R.string.placette_tout_voir)) }
        }
        if (aRelief || aHouppiers || aSommets) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (aRelief) CaseCouche(stringResource(R.string.couche_relief), voirRelief) { voirRelief = it }
                if (aHouppiers) CaseCouche(stringResource(R.string.couche_houppiers), voirHouppiers) { voirHouppiers = it }
                if (aSommets) CaseCouche(stringResource(R.string.couche_sommets), voirSommets) { voirSommets = it }
            }
        }
    }
}

/** Tick box with its label, the whole row touchable (gloves): 48 dp high at least. */
@Composable
private fun CaseCouche(libelle: String, cochee: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).clickable { onChange(!cochee) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = cochee, onCheckedChange = null)
        Text(libelle, Modifier.padding(start = 4.dp, end = 8.dp))
    }
}
