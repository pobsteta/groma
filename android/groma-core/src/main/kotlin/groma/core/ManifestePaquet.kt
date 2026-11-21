package groma.core

import java.io.File
import java.util.zip.ZipFile

/** Camera intrinsics of the package (pixels), for an image or its depth map. */
data class IntrinsequesImage(val fx: Double, val fy: Double, val cx: Double, val cy: Double, val largeur: Int, val hauteur: Int)

/**
 * One frame of the manifest. [pose] is ARCore's world_from_camera, 4x4 ROW major (as written by
 * `EnregistreurPaquet`, whatever the "column" wording of mission 045), world Y up, camera looking along -Z.
 */
class ImagePaquet(
    val id: Int, val pose: DoubleArray, val suivi: Boolean,
    val image: String?, val k: IntrinsequesImage?, val profondeur: String?, val kProfondeur: IntrinsequesImage?,
) {
    /** A kept image: tracked, with its file named and a usable pose. */
    val retenue: Boolean get() = suivi && image != null && k != null && pose.size == 16
}

/** A target (C0 to C3) detected at pixel ([u], [v]) of image [image]. */
data class CibleVue(val image: Int, val idCible: String, val u: Double, val v: Double)

class ManifestePaquet(
    val plotId: String,
    val centreX: Double, val centreY: Double,
    val uniteAzimut: String, val declinaisonDeg: Double, val convergenceDeg: Double,
    val ciblesMesurees: List<CibleMesuree>,
    val images: List<ImagePaquet>,
    val detectionsCibles: List<CibleVue>,
) {
    /** Kept images in the order of the manifest. */
    val retenues: List<ImagePaquet> by lazy { images.filter { it.retenue } }
}

object ManifesteLecture {
    fun lire(json: String): ManifestePaquet {
        val m = JsonMini.lire(json) as? Map<*, *> ?: throw IllegalArgumentException("Manifeste illisible.")
        require(m["format"] == "groma-paquet") { "Ce n'est pas un paquet Groma." }
        val pl = m["placette"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val c = m["centre"] as? Map<*, *> ?: throw IllegalArgumentException("Manifeste sans centre.")
        return ManifestePaquet(
            plotId = m["plot_id"] as? String ?: "",
            centreX = num(c["x"]) ?: throw IllegalArgumentException("Centre sans x."),
            centreY = num(c["y"]) ?: throw IllegalArgumentException("Centre sans y."),
            uniteAzimut = pl["unite_azimut"] as? String ?: "deg",
            declinaisonDeg = num(pl["declinaison_deg"]) ?: 0.0,
            convergenceDeg = num(pl["convergence_deg"]) ?: 0.0,
            ciblesMesurees = (m["cibles_mesurees"] as? List<*>).orEmpty().mapNotNull { o ->
                val t = o as? Map<*, *> ?: return@mapNotNull null
                CibleMesuree(
                    t["id_cible"] as? String ?: return@mapNotNull null,
                    num(t["azimut_mesure"]) ?: return@mapNotNull null,
                    num(t["distance_mesuree_m"]) ?: return@mapNotNull null,
                    num(t["dbh_mesure_cm"]),
                    (t["source_azimut"] as? String)?.takeIf { it.isNotBlank() } ?: SourceAzimut.BOUSSOLE_VISEE.code,
                )
            },
            images = (m["images"] as? List<*>).orEmpty().mapNotNull { o ->
                val i = o as? Map<*, *> ?: return@mapNotNull null
                ImagePaquet(
                    id = num(i["id"])?.toInt() ?: return@mapNotNull null,
                    pose = (i["pose"] as? List<*>)?.map { num(it) ?: Double.NaN }?.toDoubleArray() ?: DoubleArray(0),
                    suivi = i["etat_suivi"] == "TRACKING",
                    image = i["image"] as? String, k = intrinseques(i["intrinseques"]),
                    profondeur = i["profondeur"] as? String, kProfondeur = intrinseques(i["intrinseques_profondeur"]),
                )
            },
            detectionsCibles = (m["detections_cibles"] as? List<*>).orEmpty().mapNotNull { o ->
                val d = o as? Map<*, *> ?: return@mapNotNull null
                CibleVue(
                    num(d["image"])?.toInt() ?: return@mapNotNull null, d["id_cible"] as? String ?: return@mapNotNull null,
                    num(d["u"]) ?: return@mapNotNull null, num(d["v"]) ?: return@mapNotNull null,
                )
            },
        )
    }

    private fun num(o: Any?): Double? = (o as? Double)?.takeIf { !it.isNaN() }

    private fun intrinseques(o: Any?): IntrinsequesImage? {
        val k = o as? Map<*, *> ?: return null
        return IntrinsequesImage(
            num(k["fx"]) ?: return null, num(k["fy"]) ?: return null, num(k["cx"]) ?: return null,
            num(k["cy"]) ?: return null, num(k["largeur"])?.toInt() ?: return null, num(k["hauteur"])?.toInt() ?: return null,
        )
    }
}

/**
 * Read-only access to a `<plot>.groma.zip`: one entry at a time through the central directory, so the ~100 MB
 * of a package are never decompressed nor held in memory (mission 045). Nothing is ever written.
 */
class PaquetZip(fichier: File) : AutoCloseable {
    private val zip = ZipFile(fichier)

    fun manifeste(): ManifestePaquet =
        ManifesteLecture.lire(String(octets("manifest.json") ?: throw IllegalArgumentException("Paquet sans manifest.json."), Charsets.UTF_8))

    fun contient(nom: String): Boolean = zip.getEntry(nom) != null

    /** Content of one entry (an image of ~50 KB, a depth map of ~28 KB), null when absent. */
    fun octets(nom: String): ByteArray? = zip.getEntry(nom)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }

    override fun close() = zip.close()
}

/** Which package of a plot to show. */
object ChoixPaquet {
    /**
     * Among [fichiers] (name, last modified): the most recent `<plot>.groma.zip` (or a variant such as
     * `<plot>.avec-lidar.groma.zip`), never a replay (`rejeu` in the name). Null when none.
     */
    fun plusRecent(plotId: String, fichiers: List<Pair<String, Long>>): String? = fichiers
        .filter { (nom, _) -> nom.startsWith("$plotId.") && nom.endsWith(".groma.zip") && !nom.contains("rejeu") }
        .maxByOrNull { it.second }?.first
}
