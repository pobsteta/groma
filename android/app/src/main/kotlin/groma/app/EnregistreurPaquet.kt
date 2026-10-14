package groma.app

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes the capture package of one plot, format documented in
 * server/src/groma_server/paquet.py (manifest.json + depth .u16 + JPEG key frames).
 * Frames are streamed to a working folder during the capture, zipped at the end.
 */
class EnregistreurPaquet(private val dossier: File, private val projetId: String, private val plotId: String) {
    private val images = JSONArray()
    private val detectionsCibles = JSONArray()
    private var n = 0

    init {
        File(dossier, "images").mkdirs(); File(dossier, "profondeurs").mkdirs(); File(dossier, "confiances").mkdirs()
    }

    data class Intrinseques(val fx: Float, val fy: Float, val cx: Float, val cy: Float, val largeur: Int, val hauteur: Int) {
        fun json() = JSONObject().put("fx", fx).put("fy", fy).put("cx", cx).put("cy", cy).put("largeur", largeur).put("hauteur", hauteur)
    }

    /** Returns the frame id. `pose` is ARCore world_from_camera, column-major as given by Pose.toMatrix. */
    fun ajouterImage(
        t: Double, poseColonnes: FloatArray, suivi: Boolean,
        nv21: ByteArray?, largeurImg: Int, hauteurImg: Int, kImg: Intrinseques?,
        profondeur: ByteBuffer?, confiance: ByteBuffer?, kProf: Intrinseques?,
    ): Int {
        val id = n++
        val o = JSONObject().put("id", id).put("t", t).put("etat_suivi", if (suivi) "TRACKING" else "PAUSED")
        // Row-major 4x4 for the manifest.
        o.put("pose", JSONArray((0 until 16).map { poseColonnes[(it % 4) * 4 + it / 4].toDouble() }))
        if (nv21 != null && kImg != null) {
            val nom = "images/%06d.jpg".format(id)
            ByteArrayOutputStream().use { b ->
                YuvImage(nv21, ImageFormat.NV21, largeurImg, hauteurImg, null).compressToJpeg(Rect(0, 0, largeurImg, hauteurImg), 90, b)
                File(dossier, nom).writeBytes(b.toByteArray())
            }
            o.put("image", nom).put("intrinseques", kImg.json())
        }
        if (profondeur != null && kProf != null) {
            val nom = "profondeurs/%06d.u16".format(id)
            val tampon = ByteArray(kProf.largeur * kProf.hauteur * 2)
            profondeur.order(ByteOrder.LITTLE_ENDIAN).get(tampon)
            File(dossier, nom).writeBytes(tampon)
            o.put("profondeur", nom).put("intrinseques_profondeur", kProf.json())
            if (confiance != null) {
                val c = ByteArray(kProf.largeur * kProf.hauteur)
                confiance.get(c)
                val nc = "confiances/%06d.u8".format(id)
                File(dossier, nc).writeBytes(c)
                o.put("confiance", nc)
            }
        }
        images.put(o)
        return id
    }

    fun ajouterCible(image: Int, idCible: String, u: Double, v: Double) {
        detectionsCibles.put(JSONObject().put("image", image).put("id_cible", idCible).put("u", u).put("v", v))
    }

    fun terminer(manifesteBase: JSONObject, sortie: File): File {
        val m = manifesteBase.put("format", "groma-paquet").put("version", 1).put("projet_id", projetId).put("plot_id", plotId)
            .put("images", images).put("detections_cibles", detectionsCibles)
        ZipOutputStream(sortie.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write(m.toString().toByteArray()); z.closeEntry()
            dossier.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(dossier).path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        return sortie
    }
}
