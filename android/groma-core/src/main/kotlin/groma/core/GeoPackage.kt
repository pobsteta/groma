package groma.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Write protocol of the Groma app on inventaire_video.gpkg (decision 3), line
 * for line the same as groma/gpkg.py `ecrire_detections`:
 * plain SQLite (no ST_* function), one short BEGIN IMMEDIATE transaction with
 * a busy timeout, writes ONLY in `detections`; the GeoPackage triggers create
 * or update the `arbres` rows without touching the field attributes.
 */
interface BaseSqlite {
    fun executer(sql: String, args: List<Any?> = emptyList())
    fun requete(sql: String, args: List<Any?> = emptyList()): List<Map<String, Any?>>

    /** BEGIN IMMEDIATE ... COMMIT, ROLLBACK on error. */
    fun <T> transactionImmediate(bloc: () -> T): T
}

object GeoPackage {
    const val SRS_ID = 2154
    const val GEOMETRIE = "geom"

    val COLONNES_DETECTIONS = listOf(
        "plot_id", "num_provisoire", "azimut", "distance_m", "dbh_cm", "h_m", "h_source",
        "houppier_id", "etat_detection", "methode", "score", "date_detection",
    )

    fun encoderPoint(x: Double, y: Double, srsId: Int = SRS_ID): ByteArray {
        val b = ByteBuffer.allocate(8 + 21).order(ByteOrder.LITTLE_ENDIAN)
        b.put('G'.code.toByte()).put('P'.code.toByte()).put(0).put(0x01)
        b.putInt(srsId)
        b.put(1).putInt(1).putDouble(x).putDouble(y)
        return b.array()
    }

    fun decoderPoint(blob: ByteArray): Pair<Double, Double> {
        require(blob[0] == 'G'.code.toByte() && blob[1] == 'P'.code.toByte()) { "Géométrie GeoPackage invalide." }
        val drapeaux = blob[3].toInt()
        val enveloppe = intArrayOf(0, 32, 48, 48, 64)[(drapeaux shr 1) and 0x07]
        val debut = 8 + enveloppe
        val wkb = ByteBuffer.wrap(blob, debut, blob.size - debut)
        wkb.order(if (blob[debut].toInt() == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        wkb.get()
        require(wkb.getInt() % 1000 == 1) { "Seuls les points sont pris en charge." }
        return Pair(wkb.getDouble(), wkb.getDouble())
    }

    /** Exterior ring of a GeoPackage POLYGON / MULTIPOLYGON (first polygon), for the crowns. */
    fun decoderAnneau(blob: ByteArray): List<Pair<Double, Double>> {
        require(blob[0] == 'G'.code.toByte() && blob[1] == 'P'.code.toByte()) { "Géométrie GeoPackage invalide." }
        val enveloppe = intArrayOf(0, 32, 48, 48, 64)[(blob[3].toInt() shr 1) and 0x07]
        var debut = 8 + enveloppe
        fun tampon(d: Int) = ByteBuffer.wrap(blob, d, blob.size - d).order(if (blob[d].toInt() == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        var b = tampon(debut)
        b.get()
        var type = b.getInt() % 1000
        if (type == 6) { // multipolygon: skip to the first polygon
            b.getInt()
            debut += 9
            b = tampon(debut); b.get(); type = b.getInt() % 1000
        }
        require(type == 3) { "Polygone attendu." }
        require(b.getInt() >= 1)
        val n = b.getInt()
        return List(n) { Pair(b.getDouble(), b.getDouble()) }
    }

    /**
     * Every polygon of a GeoPackage POLYGON / MULTIPOLYGON, each as its rings
     * (exterior first), for the parcels. Z and M values are skipped.
     */
    fun decoderPolygones(blob: ByteArray): List<List<List<Pair<Double, Double>>>> {
        require(blob.size >= 8 && blob[0] == 'G'.code.toByte() && blob[1] == 'P'.code.toByte()) { "Géométrie GeoPackage invalide." }
        val enveloppe = intArrayOf(0, 32, 48, 48, 64)[(blob[3].toInt() shr 1) and 0x07]
        val b = ByteBuffer.wrap(blob)
        b.position(8 + enveloppe)
        // Reads one WKB header (byte order, type) at the current position: returns (type, coordinates per point).
        fun entete(): Pair<Int, Int> {
            b.order(if (b.get().toInt() == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            val t = b.getInt()
            return Pair(t % 1000, intArrayOf(2, 3, 3, 4)[t / 1000])
        }
        fun polygone(dims: Int) = List(b.getInt()) {
            List(b.getInt()) {
                val p = Pair(b.getDouble(), b.getDouble())
                repeat(dims - 2) { b.getDouble() }
                p
            }
        }
        val (type, dims) = entete()
        return when (type) {
            3 -> listOf(polygone(dims))
            6 -> List(b.getInt()) {
                val (t, d) = entete()
                require(t == 3) { "Polygone attendu dans le multipolygone." }
                polygone(d)
            }
            else -> throw IllegalArgumentException("Polygone attendu.")
        }
    }

    fun maintenant(): String = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()

    /** Insert or update detections keyed by uuid. Returns (inserted, updated). */
    fun ecrireDetections(db: BaseSqlite, detections: List<Detection>): Pair<Int, Int> = db.transactionImmediate {
        var inseres = 0
        var maj = 0
        for (d in detections) {
            val valeurs = d.valeurs().toMutableMap()
            valeurs.putIfAbsent("date_detection", maintenant())
            valeurs[GEOMETRIE] = encoderPoint(d.x, d.y)
            val existe = db.requete("SELECT 1 AS un FROM detections WHERE uuid = ?", listOf(d.uuid)).isNotEmpty()
            if (existe) {
                db.executer("UPDATE detections SET ${valeurs.keys.joinToString(", ") { "$it = ?" }} WHERE uuid = ?", valeurs.values.toList() + d.uuid)
                maj++
            } else {
                val cols = listOf("uuid") + valeurs.keys
                db.executer("INSERT INTO detections (${cols.joinToString(", ")}) VALUES (${cols.joinToString(", ") { "?" }})", listOf(d.uuid) + valeurs.values)
                inseres++
            }
        }
        Pair(inseres, maj)
    }

    fun lirePoints(db: BaseSqlite, couche: String, plotId: String? = null): List<Map<String, Any?>> {
        require(couche.matches(Regex("[a-z_]+"))) { "Nom de couche invalide." }
        val lignes = if (plotId == null) db.requete("SELECT * FROM $couche") else db.requete("SELECT * FROM $couche WHERE plot_id = ?", listOf(plotId))
        return lignes.map { l ->
            val m = l.toMutableMap()
            (m.remove(GEOMETRIE) as? ByteArray)?.let { val (x, y) = decoderPoint(it); m["x"] = x; m["y"] = y }
            m
        }
    }

    /**
     * The one-row `projet` table (schema 1.1.0), or null when the GeoPackage
     * was prepared before it existed or the table is empty: the project list
     * then falls back on the folder name.
     */
    fun lireProjet(db: BaseSqlite): InfosProjet? {
        if (db.requete("SELECT 1 AS un FROM sqlite_master WHERE type = 'table' AND name = 'projet'").isEmpty()) return null
        val l = db.requete("SELECT * FROM projet ORDER BY fid LIMIT 1").firstOrNull() ?: return null
        fun texte(c: String) = (l[c] as? String)?.takeIf { it.isNotBlank() }
        fun reel(c: String) = (l[c] as? Number)?.toDouble()
        fun entier(c: String) = (l[c] as? Number)?.toInt()
        return InfosProjet(
            projetId = texte("projet_id") ?: return null,
            projetNom = texte("projet_nom") ?: return null,
            description = texte("description"),
            proprietaire = texte("proprietaire"),
            communes = texte("communes"),
            surfaceZoneHa = reel("surface_zone_ha"),
            surfaceCadastreHa = reel("surface_cadastre_ha"),
            nbParcelles = entier("nb_parcelles"),
            nbUg = entier("nb_ug"),
            nbPlacettesBase = entier("nb_placettes_base"),
            nbPlacettesRempl = entier("nb_placettes_rempl"),
            planGenereLe = texte("plan_genere_le"),
            prepareLe = texte("prepare_le"),
            preparePar = texte("prepare_par"),
            emprise = texte("emprise"),
        )
    }

    /**
     * The `parcelles` layer (schema 1.2.0), Lambert-93: empty when the
     * GeoPackage was prepared before it existed or holds no parcel.
     */
    fun lireParcelles(db: BaseSqlite): List<Parcelle> {
        if (db.requete("SELECT 1 AS un FROM sqlite_master WHERE type = 'table' AND name = 'parcelles'").isEmpty()) return emptyList()
        return db.requete("SELECT * FROM parcelles ORDER BY fid").mapNotNull { l ->
            val g = l[GEOMETRIE] as? ByteArray ?: return@mapNotNull null
            Parcelle(
                idu = l["idu"]?.toString() ?: return@mapNotNull null,
                commune = l["commune"] as? String,
                section = l["section"] as? String,
                numero = l["numero"] as? String,
                contenanceM2 = (l["contenance_m2"] as? Number)?.toDouble(),
                polygones = decoderPolygones(g),
            )
        }
    }

    /**
     * The `ugf` layer (forest management units), Lambert-93: empty when the
     * GeoPackage was prepared before it existed or holds no unit.
     */
    fun lireUgf(db: BaseSqlite): List<Ugf> {
        if (db.requete("SELECT 1 AS un FROM sqlite_master WHERE type = 'table' AND name = 'ugf'").isEmpty()) return emptyList()
        return db.requete("SELECT * FROM ugf ORDER BY fid").mapNotNull { l ->
            val g = l[GEOMETRIE] as? ByteArray ?: return@mapNotNull null
            Ugf(
                ugId = l["ug_id"]?.toString() ?: return@mapNotNull null,
                libelle = l["libelle"] as? String,
                foret = l["foret"] as? String,
                parcelleOnf = l["parcelle_onf"] as? String,
                surfaceM2 = (l["surface_m2"] as? Number)?.toDouble(),
                polygones = decoderPolygones(g),
            )
        }
    }

    fun mettreAJourEtat(db: BaseSqlite, plotId: String, etat: String) {
        require(etat in setOf("a_faire", "provisoire", "envoye", "definitif"))
        db.transactionImmediate { db.executer("UPDATE placettes SET etat_traitement = ? WHERE plot_id = ?", listOf(etat, plotId)) }
    }
}

/** Cadastral parcel of the `parcelles` layer: polygons → rings → points, Lambert-93. */
data class Parcelle(
    val idu: String, val commune: String?, val section: String?, val numero: String?, val contenanceM2: Double?,
    val polygones: List<List<List<Pair<Double, Double>>>>,
)

/** Forest management unit of the `ugf` layer: ONF parcel number, polygons in Lambert-93. */
data class Ugf(
    val ugId: String, val libelle: String?, val foret: String?, val parcelleOnf: String?, val surfaceM2: Double?,
    val polygones: List<List<List<Pair<Double, Double>>>>,
)

/** Row of the `projet` table: the nemetonshiny project the plan comes from. */
data class InfosProjet(
    val projetId: String, val projetNom: String, val description: String?, val proprietaire: String?,
    val communes: String?, val surfaceZoneHa: Double?, val surfaceCadastreHa: Double?, val nbParcelles: Int?,
    val nbUg: Int?, val nbPlacettesBase: Int?, val nbPlacettesRempl: Int?, val planGenereLe: String?,
    val prepareLe: String?, val preparePar: String?, val emprise: String?,
)

data class Detection(
    val uuid: String, val plotId: String, val x: Double, val y: Double, val numProvisoire: String,
    val azimut: Double, val distanceM: Double, val dbhCm: Double, val hM: Double?, val hSource: String,
    val houppierId: Int?, val etatDetection: String, val methode: String, val score: Double?,
) {
    fun valeurs(): Map<String, Any?> = linkedMapOf(
        "plot_id" to plotId, "num_provisoire" to numProvisoire, "azimut" to azimut, "distance_m" to distanceM,
        "dbh_cm" to dbhCm, "h_m" to hM, "h_source" to hSource, "houppier_id" to houppierId,
        "etat_detection" to etatDetection, "methode" to methode, "score" to score,
    )
}
