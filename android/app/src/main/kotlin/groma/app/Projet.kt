package groma.app

import android.database.sqlite.SQLiteDatabase
import groma.core.ArbrePlacette
import groma.core.Azimut
import groma.core.CentreMesure
import groma.core.resumeReception
import groma.core.CiblePlacee
import groma.core.PlacementCible
import groma.core.SaisieArbre
import groma.core.SourceAzimut
import groma.core.CibleMesuree
import groma.core.CleProjet
import groma.core.Declinaison
import groma.core.DonneesCarte
import groma.core.GeoPackage
import groma.core.GeoTiff
import groma.core.Grille
import groma.core.OrientationPlacette
import groma.core.Houppier
import groma.core.InfosProjet
import groma.core.ParametresPlacette
import groma.core.PhotosArbre
import groma.core.Position
import groma.core.Projections
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

    /** Plots, measured centres and parcels for the map screen. */
    fun donneesCarte(): DonneesCarte = SQLiteDatabase.openDatabase(gpkg.path, null, SQLiteDatabase.OPEN_READONLY).use { DonneesCarte.lire(SqliteAndroid(it)) }

    fun placettes(): List<Map<String, Any?>> = ouvrir().use { GeoPackage.lirePoints(SqliteAndroid(it), "placettes") }

    fun parametres(plotId: String): ParametresPlacette = ouvrir().use { db ->
        val s = SqliteAndroid(db)
        val p = GeoPackage.lirePoints(s, "placettes", plotId).single()
        // Latest measured RTK centre when present, else the planned point.
        val c = GeoPackage.dernierCentreParPlacette(s, plotId).firstOrNull() ?: p
        ParametresPlacette(
            plotId, c["x"] as Double, c["y"] as Double,
            (p["rayon_m"] as? Number)?.toDouble() ?: 15.0,
            (p["seuil_diam_cm"] as? Number)?.toDouble() ?: 7.5,
            p["unite_azimut"] as? String ?: "deg",
            // Declination left empty in the GeoPackage: WMM2025 at the planned point, today (never written back).
            (p["declinaison_deg"] as? Number)?.toDouble()
                ?: Projections.versWgs84(p["x"] as Double, p["y"] as Double).let { (lon, lat) -> Declinaison.ouNull(lat, lon) }
                ?: 0.0,
            (p["convergence_deg"] as? Number)?.toDouble() ?: 0.0,
        )
    }

    /**
     * Appends a centre measured on the map (brief carte RTK, § 5.4), never
     * rewriting an earlier one, and moves the plot's targets and trees added by hand to it (missions 031, 032,
     * same orientation as [placerCible]). Returns its distance to the planned
     * point (m) and the targets moved.
     */
    fun ajouterCentre(plotId: String, c: CentreMesure): C0Enregistre {
        val o = parametres(plotId).let { OrientationPlacette(it.uniteAzimut, it.declinaisonDeg, it.convergenceDeg) }
        return ouvrir().use { db ->
            val s = SqliteAndroid(db)
            val p = GeoPackage.lirePoints(s, "placettes", plotId).singleOrNull()
            val ecart = p?.let { c.ecartPrevuM(it["x"] as Double, it["y"] as Double) }?.let { Math.round(it * 1000) / 1000.0 }
            val (x, y) = c.lambert93
            val r = GeoPackage.releverC0(s, plotId, x, y, c.qualite, Math.round(c.precisionM * 1000) / 1000.0, ecart, o, reception = c)
            C0Enregistre(ecart, r.ciblesRecalees, r.arbresRecales)
        }
    }

    fun cibles(plotId: String): List<CibleMesuree> = ouvrir().use { db ->
        GeoPackage.lirePoints(SqliteAndroid(db), "cibles", plotId).mapNotNull {
            val az = (it["azimut_mesure"] as? Number)?.toDouble() ?: return@mapNotNull null
            val d = (it["distance_mesuree_m"] as? Number)?.toDouble() ?: return@mapNotNull null
            val source = (it["source_azimut"] as? String)?.takeIf { s -> s.isNotBlank() } ?: SourceAzimut.BOUSSOLE_VISEE.code
            CibleMesuree(it["id_cible"] as String, az, d, (it["dbh_mesure_cm"] as? Number)?.toDouble(), source)
        }
    }

    /** Centre of the cibles view: latest measured centre, else the planned point ([mesure] false). */
    fun centrePourCibles(plotId: String): CentreCibles = ouvrir().use { db ->
        val s = SqliteAndroid(db)
        val mesure = GeoPackage.dernierCentreParPlacette(s, plotId).firstOrNull()
        val p = GeoPackage.lirePoints(s, "placettes", plotId).single()
        val c = mesure ?: p
        CentreCibles(c["x"] as Double, c["y"] as Double, mesure != null, mesure?.get("qualite_gnss") as? String, (mesure?.get("precision_h_m") as? Number)?.toDouble(),
            resumeReception((mesure?.get("nb_positions") as? Number)?.toInt(), (mesure?.get("nb_satellites") as? Number)?.toInt(), (mesure?.get("hdop") as? Number)?.toDouble()),
            mesure?.get("date_mesure")?.toString(),
            (mesure?.get("ecart_prevu_m") as? Number)?.toDouble(),
            Projections.versWgs84(p["x"] as Double, p["y"] as Double).let { (lon, lat) -> Position(lat, lon) })
    }

    /** Targets already in `cibles` (placed here or read from an existing project), azimuth back in degrees. */
    fun ciblesPlacees(plotId: String, unite: String): List<CiblePlacee> = ouvrir().use { db ->
        GeoPackage.lirePoints(SqliteAndroid(db), "cibles", plotId).mapNotNull {
            val id = it["id_cible"] as? String ?: return@mapNotNull null
            val az = (it["azimut_mesure"] as? Number)?.toDouble() ?: return@mapNotNull null
            val d = (it["distance_mesuree_m"] as? Number)?.toDouble() ?: return@mapNotNull null
            val dbh = (it["dbh_mesure_cm"] as? Number)?.toDouble() ?: return@mapNotNull null
            val src = SourceAzimut.entries.firstOrNull { s -> s.code == it["source_azimut"] } ?: SourceAzimut.BOUSSOLE_VISEE
            CiblePlacee(id, Azimut.depuisUnite(az, unite), d, dbh, src)
        }.sortedBy { it.id }
    }

    /**
     * Places a target: [azimut] typed (or read) in the plot's unit; the Lambert-93 position is
     * computed from the centre and written to `cibles` (one row per plot and target).
     */
    fun placerCible(plotId: String, id: String, azimut: Double, distanceM: Double, dbhCm: Double, source: SourceAzimut): CiblePlacee {
        val p = parametres(plotId)
        PlacementCible.controler(id, azimut, p.uniteAzimut, distanceM, dbhCm)
        val centre = centrePourCibles(plotId)
        val (x, y) = PlacementCible.position(Pair(centre.x, centre.y), azimut, p.uniteAzimut, p.declinaisonDeg, p.convergenceDeg, distanceM)
        val c = CiblePlacee(id, Azimut.depuisUnite(azimut, p.uniteAzimut), distanceM, dbhCm, source)
        ouvrir().use { GeoPackage.ecrireCible(SqliteAndroid(it), plotId, c, x, y, azimut) }
        return c
    }

    /**
     * Writes the values entered in Groma for one tree ([GeoPackage.modifierArbre]); the plan of the
     * plot (mission 019) calls it. Throws IllegalArgumentException on an invalid value.
     */
    fun modifierArbre(uuid: String, saisie: Map<String, String?>) {
        ouvrir().use { GeoPackage.modifierArbre(SqliteAndroid(it), uuid, saisie) }
    }

    /**
     * Adds a tree forgotten by the video ([GeoPackage.ajouterArbre], mission 023): [azimut] in the plot's unit,
     * [saisie] as typed in the form (essence_arbre, dbh_cm required, statut, qualite). Throws IllegalArgumentException
     * on a refused value (no measured centre, missing diameter...).
     */
    fun ajouterArbre(plotId: String, azimut: Double, distanceM: Double, saisie: Map<String, String?>): String {
        val dbh = SaisieArbre.diametre(saisie["dbh_cm"]) ?: throw IllegalArgumentException("Le diamètre au ruban est obligatoire.")
        return ouvrir().use {
            GeoPackage.ajouterArbre(SqliteAndroid(it), plotId, azimut, distanceM, dbh, saisie["essence_arbre"], saisie["statut"], saisie["qualite"])
        }
    }

    /** Deletes a tree added by hand ([GeoPackage.supprimerArbreAjoute]); never a tree of the video. */
    fun supprimerArbreAjoute(uuid: String) {
        ouvrir().use { GeoPackage.supprimerArbreAjoute(SqliteAndroid(it), uuid, dossier) }
    }

    /**
     * Deletes the files of photos Groma took and the form no longer cites (mission 022, after a confirmed
     * removal that was saved, or a photo taken then abandoned). Never a photo of QField: see [PhotosArbre.supprimerFichier].
     */
    fun supprimerPhotos(chemins: List<String>) {
        ouvrir().use { db -> val b = SqliteAndroid(db); for (c in chemins) PhotosArbre.supprimerFichier(b, dossier, c) }
    }

    /** Trees of the plot for the plan (false positives left out, by number): see [ArbrePlacette.lire]. */
    fun arbres(plotId: String): List<ArbrePlacette> = ouvrir().use { ArbrePlacette.lire(SqliteAndroid(it), plotId) }

    fun houppiers(plotId: String): List<Houppier> = ouvrir().use { db ->
        SqliteAndroid(db).requete("SELECT houppier_id, h_max, geom FROM houppiers WHERE plot_id = ?", listOf(plotId)).map {
            Houppier((it["houppier_id"] as Number).toInt(), GeoPackage.decoderAnneau(it["geom"] as ByteArray), (it["h_max"] as? Number)?.toDouble())
        }
    }

    fun mnt(plotId: String): Grille? = raster("${plotId}_mnt.tif")
    fun mnh(plotId: String): Grille? = raster("${plotId}_mnh.tif")

    private fun raster(nom: String): Grille? = File(dossier, "rasters/$nom").takeIf { it.exists() }?.let { GeoTiff.lire(it.readBytes()) }
}

/** Centre used by the cibles view; [mesure] false when only the planned point is known. */
data class CentreCibles(val x: Double, val y: Double, val mesure: Boolean, val qualite: String? = null, val precisionM: Double? = null, val reception: String? = null, val dateMesure: String? = null,
    /** Distance of the latest centre to the planned point (m), and the planned point itself (WGS 84, mission 030). */
    val ecartM: Double? = null, val prevu: Position? = null)

/** A C0 survey saved by [Projet.ajouterCentre]: distance to the planned point (m), targets and trees added by hand moved to it. */
data class C0Enregistre(val ecartM: Double?, val ciblesRecalees: List<String>, val arbresRecales: List<String> = emptyList())
