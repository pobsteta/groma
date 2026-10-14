package groma.app

import android.database.sqlite.SQLiteDatabase
import groma.core.CibleMesuree
import groma.core.CleProjet
import groma.core.GeoPackage
import groma.core.GeoTiff
import groma.core.Grille
import groma.core.Houppier
import groma.core.InfosProjet
import groma.core.ParametresPlacette
import java.io.File

/**
 * The QField project folder delivered by nemetonshiny (inventaire_video.gpkg
 * + rasters/), copied into Groma's own storage by [DepotProjets]. Since
 * Android 11 QField works on ITS imported copy, in its private folder: see
 * docs/terrain.md for how detections reach QField's copy.
 */
class Projet(val dossier: File) {
    val gpkg = File(dossier, "inventaire_video.gpkg")

    fun ouvrir(): SQLiteDatabase = SQLiteDatabase.openDatabase(gpkg.path, null, SQLiteDatabase.OPEN_READWRITE)

    /** Row of the `projet` table, null for a GeoPackage prepared before schema 1.1.0. */
    fun infos(): InfosProjet? = ouvrir().use { GeoPackage.lireProjet(SqliteAndroid(it)) }

    /** Key of the project's packages, uploads and detection exports (see [CleProjet]). */
    val projetId: String by lazy { CleProjet.projetId(infos(), dossier.name) }

    fun placettes(): List<Map<String, Any?>> = ouvrir().use { GeoPackage.lirePoints(SqliteAndroid(it), "placettes") }

    fun parametres(plotId: String): ParametresPlacette = ouvrir().use { db ->
        val s = SqliteAndroid(db)
        val p = GeoPackage.lirePoints(s, "placettes", plotId).single()
        // Measured RTK centre when present, else the planned point.
        val c = GeoPackage.lirePoints(s, "centres_mesures", plotId).firstOrNull() ?: p
        ParametresPlacette(
            plotId, c["x"] as Double, c["y"] as Double,
            (p["rayon_m"] as? Number)?.toDouble() ?: 20.0,
            (p["seuil_diam_cm"] as? Number)?.toDouble() ?: 7.5,
            p["unite_azimut"] as? String ?: "deg",
            (p["declinaison_deg"] as? Number)?.toDouble() ?: 0.0,
            (p["convergence_deg"] as? Number)?.toDouble() ?: 0.0,
        )
    }

    fun cibles(plotId: String): List<CibleMesuree> = ouvrir().use { db ->
        GeoPackage.lirePoints(SqliteAndroid(db), "cibles", plotId).mapNotNull {
            val az = (it["azimut_mesure"] as? Number)?.toDouble() ?: return@mapNotNull null
            val d = (it["distance_mesuree_m"] as? Number)?.toDouble() ?: return@mapNotNull null
            CibleMesuree(it["id_cible"] as String, az, d, (it["dbh_mesure_cm"] as? Number)?.toDouble())
        }
    }

    fun houppiers(plotId: String): List<Houppier> = ouvrir().use { db ->
        SqliteAndroid(db).requete("SELECT houppier_id, h_max, geom FROM houppiers WHERE plot_id = ?", listOf(plotId)).map {
            Houppier((it["houppier_id"] as Number).toInt(), GeoPackage.decoderAnneau(it["geom"] as ByteArray), (it["h_max"] as? Number)?.toDouble())
        }
    }

    fun mnt(plotId: String): Grille? = raster("${plotId}_mnt.tif")
    fun mnh(plotId: String): Grille? = raster("${plotId}_mnh.tif")

    private fun raster(nom: String): Grille? = File(dossier, "rasters/$nom").takeIf { it.exists() }?.let { GeoTiff.lire(it.readBytes()) }
}
