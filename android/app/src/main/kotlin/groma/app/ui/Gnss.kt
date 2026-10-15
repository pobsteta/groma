package groma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import groma.app.R
import groma.app.gnss.EtatRtk
import groma.app.gnss.libelleFix
import groma.core.FixGnss
import groma.core.OrigineFix
import groma.core.QualiteFix
import groma.core.SatelliteVu
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Badge colour by fix quality (green RTK fixed → red no position), as in Marculus. */
fun couleurQualite(q: QualiteFix): Color = when (q) {
    QualiteFix.RTK_FIXE -> Color(0xFF2E7D32)
    QualiteFix.RTK_FLOTTANT -> Color(0xFF9E9D24)
    QualiteFix.DGPS -> Color(0xFF1565C0)
    QualiteFix.AUTONOME, QualiteFix.PPS -> Color(0xFF616161)
    else -> Color(0xFFB71C1C)
}

/**
 * Fix badge of the map (brief § 5.1): RTK fixed 2 cm, float, DGPS,
 * autonomous, phone position (flagged), or no position. Opens « État GNSS ».
 */
@Composable
fun BadgeFix(fix: FixGnss?, etat: EtatRtk, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (texte, couleur) = when {
        fix == null && etat.enMarche -> stringResource(R.string.rtk_connexion) to Color(0xFF616161)
        fix == null -> stringResource(R.string.gnss_sans_position) to Color(0xFFB71C1C)
        fix.origine == OrigineFix.TELEPHONE -> stringResource(R.string.gnss_telephone, precision(fix.precisionHorizontaleM)) to Color(0xFF616161)
        else -> libelleFix(fix) to couleurQualite(fix.qualite)
    }
    Text(
        texte, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).background(couleur).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** « État GNSS » (taken from Marculus): fix details, dilutions, corrections, sky plot. */
@Composable
fun DialogueEtatGnss(fix: FixGnss?, etat: EtatRtk, onFermer: () -> Unit) {
    AlertDialog(
        onDismissRequest = onFermer,
        confirmButton = { TextButton(onClick = onFermer) { Text(stringResource(R.string.fermer)) } },
        title = { Text(stringResource(R.string.gnss_etat_titre)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Ligne(stringResource(R.string.gnss_source), stringResource(
                    when {
                        fix?.origine == OrigineFix.TELEPHONE -> R.string.gnss_source_telephone
                        etat.enMarche -> R.string.gnss_source_recepteur
                        else -> R.string.gnss_source_aucune
                    },
                ))
                if (fix == null) {
                    Text(stringResource(R.string.gnss_aucun_fix), modifier = Modifier.padding(top = 8.dp))
                    etat.erreur?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                } else {
                    Ligne(stringResource(R.string.gnss_fix), fix.qualite.libelle)
                    Ligne(stringResource(R.string.gnss_precision), fix.precisionHorizontaleM?.let(::precision))
                    Ligne(stringResource(R.string.gnss_latitude), fmt(fix.position.latitude, 7))
                    Ligne(stringResource(R.string.gnss_longitude), fmt(fix.position.longitude, 7))
                    Ligne(stringResource(R.string.gnss_altitude), fix.altitudeM?.let { "${fmt(it, 1)} m" })
                    Ligne(stringResource(R.string.gnss_satellites), fix.nbSatellites.takeIf { it > 0 }?.toString())
                    Ligne("HDOP", fix.hdop?.let { fmt(it, 2) })
                    Ligne("PDOP", fix.pdop?.let { fmt(it, 2) })
                    Ligne(stringResource(R.string.gnss_age), fix.ageCorrectionsS?.let { "${fmt(it, 0)} s" })
                    Ligne(stringResource(R.string.gnss_station), fix.stationRef)
                    if (fix.satellites.isNotEmpty()) {
                        Text(stringResource(R.string.gnss_ciel), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        Skyplot(fix.satellites, Modifier.fillMaxWidth().heightIn(max = 240.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun Ligne(libelle: String, valeur: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(libelle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(valeur ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun couleurSnr(snr: Int?): Color = when {
    snr == null || snr <= 0 -> Color(0xFF9E9E9E)
    snr < 25 -> Color(0xFFD32F2F)
    snr < 35 -> Color(0xFFF57C00)
    snr < 45 -> Color(0xFF9E9D24)
    else -> Color(0xFF2E7D32)
}

/** Polar plot of the satellites in view: elevation from the centre (90°), north up, colour by signal. */
@Composable
private fun Skyplot(satellites: List<SatelliteVu>, modifier: Modifier) {
    val grille = MaterialTheme.colorScheme.outline
    val texte = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.aspectRatio(1f)) {
        val rayon = size.minDimension / 2f * 0.82f
        val c = Offset(size.width / 2f, size.height / 2f)
        listOf(1f, 2f / 3f, 1f / 3f).forEach { drawCircle(grille, rayon * it, c, style = Stroke(2f)) }
        drawLine(grille, Offset(c.x, c.y - rayon), Offset(c.x, c.y + rayon), 2f)
        drawLine(grille, Offset(c.x - rayon, c.y), Offset(c.x + rayon, c.y), 2f)
        val nc = drawContext.canvas.nativeCanvas
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER; textSize = rayon * 0.12f; color = texte.toArgb()
        }
        nc.drawText("N", c.x, c.y - rayon - 6f, p)
        for (s in satellites) {
            val elev = s.elevation ?: continue
            val az = s.azimut ?: continue
            val r = rayon * (1f - elev.coerceIn(0, 90) / 90f)
            val a = Math.toRadians(az.toDouble())
            val pt = Offset(c.x + (r * sin(a)).toFloat(), c.y - (r * cos(a)).toFloat())
            drawCircle(couleurSnr(s.snr), rayon * 0.06f, pt)
            nc.drawText(s.prn.toString(), pt.x, pt.y - rayon * 0.08f, p.apply { textSize = rayon * 0.09f })
        }
    }
}

private fun fmt(v: Double, decimales: Int): String = String.format(Locale.FRANCE, "%.${decimales}f", v)

/** Accuracy in cm below one metre, in m above. */
fun precision(metres: Double?): String = when {
    metres == null -> "?"
    metres < 1.0 -> "${(metres * 100).toInt()} cm"
    else -> String.format(Locale.FRANCE, "%.0f m", metres)
}
