package groma.app.carte

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.io.File

/** Base maps of the map screen (brief 2026-10-14 carte RTK, § 5.1). */
enum class Fond(val libelle: String, val attribution: String) {
    PLAN_IGN("Plan IGN", "© IGN Géoplateforme, Licence Ouverte Etalab 2.0"),
    ORTHO_IGN("Ortho IGN", "© IGN Géoplateforme, Licence Ouverte Etalab 2.0"),
    OSM("OpenStreetMap", "© contributeurs OpenStreetMap (ODbL)");

    val source: OnlineTileSourceBase
        get() = when (this) {
            PLAN_IGN -> PLAN_IGN_SOURCE
            ORTHO_IGN -> ORTHO_IGN_SOURCE
            OSM -> TileSourceFactory.MAPNIK
        }
}

/**
 * Tile of the IGN Géoplateforme WMTS (no key, Licence Ouverte Etalab 2.0).
 * The `PM_0_19` matrix set is osmdroid's Web Mercator grid: TILEROW/TILECOL
 * are its Y/X.
 */
private class SourceGeoplateforme(nom: String, private val couche: String, private val style: String, private val format: String) :
    OnlineTileSourceBase(nom, 0, 19, 256, if (format == "image/png") ".png" else ".jpg", arrayOf("https://data.geopf.fr/wmts")) {
    override fun getTileURLString(index: Long): String =
        baseUrl + "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=" + couche +
            "&STYLE=" + style.replace(" ", "%20") + "&FORMAT=" + format + "&TILEMATRIXSET=PM_0_19" +
            "&TILEMATRIX=" + MapTileIndex.getZoom(index) + "&TILEROW=" + MapTileIndex.getY(index) + "&TILECOL=" + MapTileIndex.getX(index)
}

private val PLAN_IGN_SOURCE: OnlineTileSourceBase = SourceGeoplateforme("PlanIGN", "GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2", "normal", "image/png")
private val ORTHO_IGN_SOURCE: OnlineTileSourceBase = SourceGeoplateforme("OrthoIGN", "ORTHOIMAGERY.ORTHOPHOTOS", "normal", "image/jpeg")

/** IGN cadastral parcels, drawn over the base map. */
val SOURCE_CADASTRE: OnlineTileSourceBase = SourceGeoplateforme("CadastreIGN", "CADASTRALPARCELS.PARCELLAIRE_EXPRESS", "PCI vecteur", "image/png")

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
