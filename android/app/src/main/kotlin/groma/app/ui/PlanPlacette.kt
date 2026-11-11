package groma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Plan of the plot, north up: circle of the radius, C0 at the centre, targets (C1 to C3) and the
 * trees at their Lambert-93 offsets from [centre]. Pinch or +/- to zoom, drag to move, touch a
 * tree to open its form. A pastille takes the colour of the species group (grey without species),
 * a size growing with the diameter (bounded) and a thick dark outline when the diameter was
 * measured with the tape, a dashed purple outline for a tree added by hand (mission 023), and the
 * preview of the tree being added ([apercu], offset in metres east/north of the centre, dashed circle); the "number – diameter" label is drawn where it does not overlap another.
 */
@Composable
internal fun Plan(
    rayonM: Double, unite: String, decl: Double, conv: Double, cibles: List<CiblePlacee>, choisie: String,
    arbres: List<ArbrePlacette>, centre: Pair<Double, Double>, arbreChoisi: String?,
    apercu: Pair<Double, Double>? = null, onArbre: (ArbrePlacette) -> Unit,
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
    }
}
