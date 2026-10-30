package groma.app.carte

import android.content.Context
import fr.nemeton.terrain.carte.Attributions
import fr.nemeton.terrain.carte.CoucheWmts
import fr.nemeton.terrain.carte.Geoplateforme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.io.File

/** Base maps of the map screen (brief 2026-10-14 carte RTK, § 5.1). */
enum class Fond(val libelle: String, val attribution: String) {
    PLAN_IGN("Plan IGN", Attributions.IGN),
    ORTHO_IGN("Ortho IGN", Attributions.IGN),
    OSM("OpenStreetMap", Attributions.OSM);

    val source: OnlineTileSourceBase
        get() = when (this) {
            PLAN_IGN -> PLAN_IGN_SOURCE
            ORTHO_IGN -> ORTHO_IGN_SOURCE
            OSM -> TileSourceFactory.MAPNIK
        }
}

/**
 * Osmdroid source of a Géoplateforme WMTS layer (terrain-carte): the layer gives the name, the
 * zoom range, the file extension and the tile URL, so the URL is written once, in `~/dev/terrain`.
 */
private class SourceGeoplateforme(private val couche: CoucheWmts) :
    OnlineTileSourceBase(couche.nom, couche.zoomMin, couche.zoomMax, 256, couche.extension, arrayOf(CoucheWmts.BASE)) {
    override fun getTileURLString(index: Long): String =
        couche.url(MapTileIndex.getZoom(index), MapTileIndex.getX(index), MapTileIndex.getY(index))
}

private val PLAN_IGN_SOURCE: OnlineTileSourceBase = SourceGeoplateforme(Geoplateforme.PLAN_IGN)
private val ORTHO_IGN_SOURCE: OnlineTileSourceBase = SourceGeoplateforme(Geoplateforme.ORTHO_IGN)

/** IGN cadastral parcels, drawn over the base map. */
val SOURCE_CADASTRE: OnlineTileSourceBase = SourceGeoplateforme(Geoplateforme.CADASTRE)

/** Base map whose tiles may be downloaded: IGN only (OSM's tile policy forbids bulk downloads). */
val Fond.telechargeable: Boolean get() = this != Fond.OSM

/** Tile source of a downloaded folder, by its name (IGN base maps and cadastre). */
fun sourceParNom(nom: String): OnlineTileSourceBase? =
    (Fond.entries.filter { it.telechargeable }.map { it.source } + SOURCE_CADASTRE).firstOrNull { it.name() == nom }

/** Highest zoom with tiles; the map over-zooms them up to [ZOOM_MAX]. */
const val ZOOM_TUILES = 19
const val ZOOM_MAX = 22.0

/**
 * osmdroid needs a user agent (tile servers' rules) and a cache in the app's
 * own storage: its default external cache is invisible since Android 10.
 */
fun configurerOsmdroid(ctx: Context, version: String) {
    val config = Configuration.getInstance()
    config.load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    config.userAgentValue = "Groma/$version (+${ctx.packageName})"
    val base = File(ctx.cacheDir, "osmdroid").apply { mkdirs() }
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tuiles").apply { mkdirs() }
}
