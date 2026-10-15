package groma.app.carte

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.view.MotionEvent
import groma.core.CentreCarte
import groma.core.ParcelleCarte
import groma.core.PlacetteCarte
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/** Plot fill by processing state (brief § 3): to do white, provisional orange, sent blue, final green. */
fun couleurEtat(etat: String): Int = when (etat) {
    "provisoire" -> Color.rgb(0xF5, 0x7C, 0x00)
    "envoye" -> Color.rgb(0x19, 0x76, 0xD2)
    "definitif" -> Color.rgb(0x2E, 0x7D, 0x32)
    else -> Color.WHITE
}

private fun Projection.pixels(lon: Double, lat: Double, p: Point = Point()): Point = toPixels(GeoPoint(lat, lon), p)

/** Cadastral parcels: outline, light fill, "section number" label from zoom 15. */
class CoucheParcelles(private val densite: Float) : Overlay() {
    var parcelles: List<ParcelleCarte> = emptyList()

    private val contour = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * densite; color = Color.rgb(0xFF, 0xD5, 0x4F) }
    private val remplissage = Paint().apply { style = Paint.Style.FILL; color = Color.argb(0x22, 0xFF, 0xD5, 0x4F) }
    private val texte = etiquette(densite, 12f)
    private val halo = halo(texte, densite)
    private val p = Point()

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = mapView.projection
        for (parcelle in parcelles) {
            val chemin = Path().apply { fillType = Path.FillType.EVEN_ODD }
            for (polygone in parcelle.polygones) for (anneau in polygone) {
                anneau.forEachIndexed { i, (lon, lat) ->
                    proj.pixels(lon, lat, p)
                    if (i == 0) chemin.moveTo(p.x.toFloat(), p.y.toFloat()) else chemin.lineTo(p.x.toFloat(), p.y.toFloat())
                }
                chemin.close()
            }
            c.drawPath(chemin, remplissage)
            c.drawPath(chemin, contour)
        }
        if (mapView.zoomLevelDouble < 15) return
        for (parcelle in parcelles) {
            val (lon, lat) = parcelle.centreEtiquette
            proj.pixels(lon, lat, p)
            c.drawText(parcelle.etiquette, p.x.toFloat(), p.y.toFloat(), halo)
            c.drawText(parcelle.etiquette, p.x.toFloat(), p.y.toFloat(), texte)
        }
    }
}

/**
 * Visiting route, plots and measured centres, drawn in one pass (a hundred
 * plots: no need for one osmdroid object each). A tap on a plot calls
 * [onPlacette].
 */
class CouchePlacettes(private val densite: Float, private val onPlacette: (PlacetteCarte) -> Unit) : Overlay() {
    var placettes: List<PlacetteCarte> = emptyList()
    var parcours: List<PlacetteCarte> = emptyList()
    var centres: List<CentreCarte> = emptyList()
    var afficherPlacettes = true
    var afficherParcours = true
    var afficherCentres = true
    var selection: String? = null

    private val trait = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f * densite; color = Color.rgb(0xE5, 0x39, 0x35)
        pathEffect = DashPathEffect(floatArrayOf(8f * densite, 6f * densite), 0f)
    }
    private val plein = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bord = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * densite; color = Color.rgb(0x21, 0x21, 0x21) }
    private val bordOver = Paint(bord).apply { pathEffect = DashPathEffect(floatArrayOf(3f * densite, 3f * densite), 0f) }
    private val cercleReel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * densite; color = Color.WHITE }
    private val choisi = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * densite; color = Color.rgb(0x00, 0xE5, 0xFF) }
    private val croix = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * densite; color = Color.rgb(0xD5, 0x00, 0xF9) }
    private val ecart = Paint(croix).apply { strokeWidth = 1.5f * densite }
    private val texte = etiquette(densite, 12f).apply { textAlign = Paint.Align.LEFT; isFakeBoldText = true }
    private val halo = halo(texte, densite)
    private val p = Point()
    private val q = Point()

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = mapView.projection
        val zoom = mapView.zoomLevelDouble
        if (afficherParcours && parcours.size >= 2) {
            val chemin = Path()
            parcours.forEachIndexed { i, pl ->
                proj.pixels(pl.lon, pl.lat, p)
                if (i == 0) chemin.moveTo(p.x.toFloat(), p.y.toFloat()) else chemin.lineTo(p.x.toFloat(), p.y.toFloat())
            }
            c.drawPath(chemin, trait)
            // Start ▲ and end ◎, as on nemetonshiny's "Parcours" map.
            proj.pixels(parcours.first().lon, parcours.first().lat, p)
            val r = 11f * densite
            // Triangle just above the first plot, which stays visible.
            val triangle = Path().apply { moveTo(p.x.toFloat(), p.y - 2.6f * r); lineTo(p.x - r, p.y - r); lineTo(p.x + r, p.y - r); close() }
            c.drawPath(triangle, plein.apply { color = Color.rgb(0xE5, 0x39, 0x35) })
            proj.pixels(parcours.last().lon, parcours.last().lat, p)
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), r * 1.3f, Paint(trait).apply { pathEffect = null })
        }
        if (afficherPlacettes) {
            for (pl in placettes) {
                proj.pixels(pl.lon, pl.lat, p)
                val x = p.x.toFloat(); val y = p.y.toFloat()
                // Real plot radius from zoom 18 (a few pixels before).
                if (zoom >= 18 && pl.rayonM != null) c.drawCircle(x, y, proj.metersToPixels(pl.rayonM!!.toFloat(), pl.lat, zoom), cercleReel)
                val r = (if (pl.remplacement) 5f else 7.5f) * densite
                c.drawCircle(x, y, r, plein.apply { color = couleurEtat(pl.etat) })
                c.drawCircle(x, y, r, if (pl.remplacement) bordOver else bord)
                if (pl.plotId == selection) c.drawCircle(x, y, r + 4f * densite, choisi)
                if (zoom >= 15) {
                    c.drawText(pl.plotId, x + r + 3f * densite, y + 4f * densite, halo)
                    c.drawText(pl.plotId, x + r + 3f * densite, y + 4f * densite, texte)
                }
            }
        }
        if (afficherCentres) {
            val prevus = placettes.associateBy { it.plotId }
            val r = 6f * densite
            for (ce in centres) {
                proj.pixels(ce.lon, ce.lat, p)
                val x = p.x.toFloat(); val y = p.y.toFloat()
                c.drawLine(x - r, y - r, x + r, y + r, croix)
                c.drawLine(x - r, y + r, x + r, y - r, croix)
                val prevu = prevus[ce.plotId]
                if (prevu != null && (ce.ecartM ?: 0.0) > 1.0) {
                    proj.pixels(prevu.lon, prevu.lat, q)
                    c.drawLine(x, y, q.x.toFloat(), q.y.toFloat(), ecart)
                }
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (!afficherPlacettes) return false
        val proj = mapView.projection
        val seuil = 24f * densite
        val proche = placettes.map { pl -> proj.pixels(pl.lon, pl.lat, p); pl to hypot(p.x - e.x, p.y - e.y) }
            .filter { it.second <= seuil }.minByOrNull { it.second }?.first ?: return false
        onPlacette(proche)
        return true
    }
}

private fun etiquette(densite: Float, taille: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(0x21, 0x21, 0x21); textSize = taille * densite; textAlign = Paint.Align.CENTER
}

private fun halo(texte: Paint, densite: Float) = Paint(texte).apply {
    style = Paint.Style.STROKE; strokeWidth = 3f * densite; color = Color.WHITE
}
