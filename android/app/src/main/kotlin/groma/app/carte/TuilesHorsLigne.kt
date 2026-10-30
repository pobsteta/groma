package groma.app.carte

import android.content.Context
import android.graphics.drawable.Drawable
import fr.nemeton.terrain.carte.Tuile
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import java.io.File

/**
 * Offline tiles of a project (brief 2026-10-14 carte RTK, § 5.2), in its
 * folder: `tuiles/<source>/<z>/<x>/<y>.<ext>`, removed with the project.
 */
object TuilesHorsLigne {
    fun racine(dossierProjet: File) = File(dossierProjet, "tuiles")

    fun fichier(racine: File, source: BitmapTileSourceBase, t: Tuile): File =
        File(racine, "${source.name()}/${t.z}/${t.x}/${t.y}${source.imageFilenameEnding()}")

    /** Number of tiles and bytes stored for [source]. */
    fun volume(racine: File, source: ITileSource): Pair<Int, Long> {
        var n = 0
        var octets = 0L
        File(racine, source.name()).walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { n++; octets += it.length() }
        return n to octets
    }

    fun supprimer(racine: File, source: ITileSource) = File(racine, source.name()).deleteRecursively()
}

/**
 * Reads the tiles downloaded in the project folder, before osmdroid's cache
 * and the network: the map works in airplane mode.
 */
private class ModuleDossier(private val racine: File, private var source: ITileSource) : MapTileModuleProviderBase(2, 40) {
    override fun getUsesDataConnection() = false
    override fun getMinimumZoomLevel() = 0
    override fun getMaximumZoomLevel() = ZOOM_TUILES
    override fun setTileSource(s: ITileSource) { source = s }
    override fun getName() = "Tuiles du projet"
    override fun getThreadGroupName() = "tuiles_projet"

    override fun getTileLoader(): TileLoader = object : TileLoader() {
        override fun loadTile(index: Long): Drawable? {
            val s = source as? BitmapTileSourceBase ?: return null
            val f = TuilesHorsLigne.fichier(racine, s, Tuile(MapTileIndex.getZoom(index), MapTileIndex.getX(index), MapTileIndex.getY(index)))
            return if (f.isFile) s.getDrawable(f.path) else null
        }
    }
}

/** osmdroid's usual provider with the project's tiles tried first. */
class FournisseurTuiles(ctx: Context, source: ITileSource, racine: File?) : MapTileProviderBasic(ctx, source) {
    init {
        if (racine != null) mTileProviderList.add(0, ModuleDossier(racine, source))
    }
}
