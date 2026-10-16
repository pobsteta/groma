package groma.app

import android.media.Image
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import groma.core.CleProjet
import groma.core.ControleQualite
import groma.core.EchantillonPrise
import groma.core.GeoPackage
import groma.core.Rayon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.Objdetect
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.hypot

/**
 * Capture of one plot: ARCore tracking + depth, ArUco targets (OpenCV), live
 * quality alerts, key-frame recording, then verdict and provisional computation.
 */
class CaptureActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    private lateinit var session: Session
    private lateinit var vue: GLSurfaceView
    private lateinit var etat: TextView
    private lateinit var alerte: TextView
    private val fond = RenduFond()
    private val cq = ControleQualite()
    private val calcul = CalculEmbarque()
    private lateinit var projet: Projet
    private lateinit var plotId: String
    private lateinit var enregistreur: EnregistreurPaquet
    private lateinit var travail: File
    private var dernierCle: FloatArray? = null
    private var tDernierCle = 0.0
    private val ciblesVues = HashMap<String, Int>()
    private val aruco by lazy { ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activite_capture)
        OpenCVLoader.initLocal()
        projet = Projet(File(intent.getStringExtra(EXTRA_PROJET)!!))
        plotId = intent.getStringExtra(EXTRA_PLOT)!!
        travail = File(cacheDir, "capture/" + CleProjet.chemin(projet.projetId, plotId)).apply { deleteRecursively(); mkdirs() }
        enregistreur = EnregistreurPaquet(travail, projet.projetId, plotId)
        vue = findViewById(R.id.vue); etat = findViewById(R.id.etat); alerte = findViewById(R.id.alerte)
        // The camera fills the screen; the overlays stay clear of the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.surcouche)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        vue.preserveEGLContextOnPause = true
        vue.setEGLContextClientVersion(2)
        vue.setRenderer(this)
        vue.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        session = Session(this).also {
            it.configure(Config(it).apply {
                depthMode = if (it.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                focusMode = Config.FocusMode.AUTO
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            })
        }
        findViewById<Button>(R.id.terminer).setOnClickListener { terminer() }
    }

    override fun onResume() { super.onResume(); session.resume(); vue.onResume() }
    override fun onPause() { super.onPause(); vue.onPause(); session.pause() }
    override fun onDestroy() { super.onDestroy(); session.close() }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        fond.creer()
        session.setCameraTextureName(fond.texture)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        session.setDisplayGeometry(windowManager.defaultDisplay.rotation, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val frame = session.update()
        fond.dessiner(frame)
        traiter(frame)
    }

    private fun traiter(frame: Frame) {
        val cam = frame.camera
        val t = frame.timestamp / 1e9
        val suivi = cam.trackingState == TrackingState.TRACKING
        val pose = FloatArray(16).also { cam.pose.toMatrix(it, 0) } // world_from_camera, column-major
        // Local frame: x = X, y = -Z, z = Y (ARCore world is Y up).
        val px = pose[12].toDouble(); val py = -pose[14].toDouble(); val pz = pose[13].toDouble()
        val vx = -pose[8].toDouble(); val vy = pose[10].toDouble() // camera looks along -Z
        val nv = hypot(vx, vy).coerceAtLeast(1e-9)

        var gris: ByteArray? = null; var nv21: ByteArray? = null; var lI = 0; var hI = 0
        try {
            frame.acquireCameraImage().use { img -> lI = img.width; hI = img.height; gris = luminance(img); nv21 = versNv21(img) }
        } catch (e: NotYetAvailableException) { }

        val vues = HashSet<String>()
        val kImg = cam.imageIntrinsics
        val dets = if (gris != null && suivi) detecterCibles(gris!!, lI, hI) else emptyList()
        for ((id, u, v) in dets) {
            vues += id
            ciblesVues[id] = (ciblesVues[id] ?: 0) + 1
            val f = kImg.focalLength; val c = kImg.principalPoint
            // Ray in camera (OpenGL) then local frame.
            val dc = doubleArrayOf((u - c[0]) / f[0], -(v - c[1]) / f[1], -1.0)
            val dw = DoubleArray(3) { r -> (0..2).sumOf { k -> pose[k * 4 + r] * dc[k] } }
            val dl = doubleArrayOf(dw[0], -dw[2], dw[1]).let { d -> val n = Math.sqrt(d.sumOf { it * it }); DoubleArray(3) { d[it] / n } }
            calcul.ajouterRayonCible(id, Rayon(doubleArrayOf(px, py, pz), dl))
            if (id == "C0" && (ciblesVues[id] ?: 0) == 1) cq.fixerCentre(px, py)
        }
        val nettete = gris?.let { ControleQualite.nettete(it, lI, hI, pas = 4) } ?: 0.0
        val alertes = cq.ajouter(EchantillonPrise(t, px, py, pz, Pair(vx / nv, vy / nv), suivi, nettete, vues))

        // Key frame: every 0.25 s and after 10 cm or 10° of motion.
        if (suivi && t - tDernierCle >= 0.25 && aBouge(pose)) {
            tDernierCle = t; dernierCle = pose
            enregistrerCle(frame, t, pose, nv21, lI, hI, dets)
        }
        runOnUiThread {
            etat.text = getString(if (suivi) R.string.suivi_ok else R.string.suivi_perdu) + "\n" +
                getString(R.string.couverture, cq.couverturePct().toInt()) + "\n" +
                getString(R.string.cibles_vues, listOf("C0", "C1", "C2", "C3").joinToString(" ") { "$it:${ciblesVues[it] ?: 0}" })
            alerte.visibility = if (alertes.isEmpty()) View.GONE else View.VISIBLE
            alerte.text = alertes.joinToString("\n")
        }
    }

    private fun aBouge(p: FloatArray): Boolean {
        val q = dernierCle ?: return true
        val d = hypot(hypot((p[12] - q[12]).toDouble(), (p[13] - q[13]).toDouble()), (p[14] - q[14]).toDouble())
        val cosA = (p[8] * q[8] + p[9] * q[9] + p[10] * q[10]).toDouble()
        return d > 0.10 || cosA < Math.cos(Math.toRadians(10.0))
    }

    private fun enregistrerCle(frame: Frame, t: Double, pose: FloatArray, nv21: ByteArray?, lI: Int, hI: Int, dets: List<Triple<String, Double, Double>>) {
        var prof: ByteBuffer? = null; var conf: ByteBuffer? = null; var kP: EnregistreurPaquet.Intrinseques? = null
        try {
            frame.acquireDepthImage16Bits().use { d ->
                frame.acquireRawDepthConfidenceImage().use { c ->
                    prof = compacter(d, 2); conf = compacter(c, 1)
                    val k = frame.camera.textureIntrinsics
                    // Depth intrinsics: texture intrinsics rescaled to the depth resolution.
                    val sx = d.width.toFloat() / k.imageDimensions[0]; val sy = d.height.toFloat() / k.imageDimensions[1]
                    kP = EnregistreurPaquet.Intrinseques(k.focalLength[0] * sx, k.focalLength[1] * sy, k.principalPoint[0] * sx, k.principalPoint[1] * sy, d.width, d.height)
                    accumulerNuage(prof!!.duplicate(), conf!!.duplicate(), kP!!, pose)
                }
            }
        } catch (e: NotYetAvailableException) { }
        val ki = frame.camera.imageIntrinsics
        val kImg = EnregistreurPaquet.Intrinseques(ki.focalLength[0], ki.focalLength[1], ki.principalPoint[0], ki.principalPoint[1], lI, hI)
        val id = enregistreur.ajouterImage(t, pose, true, nv21, lI, hI, kImg, prof, conf, kP)
        // Target detections of this frame go into the package for the server.
        for ((c, u, v) in dets) enregistreur.ajouterCible(id, c, u, v)
    }

    private fun accumulerNuage(prof: ByteBuffer, conf: ByteBuffer, k: EnregistreurPaquet.Intrinseques, pose: FloatArray) {
        prof.order(ByteOrder.LITTLE_ENDIAN)
        for (v in 0 until k.hauteur step 2) for (u in 0 until k.largeur step 2) {
            val i = v * k.largeur + u
            if ((conf.get(i).toInt() and 0xFF) < 128) continue
            val z = (prof.getShort(i * 2).toInt() and 0xFFFF) / 1000.0
            if (z < 0.3 || z > 6.0) continue
            val xc = (u + 0.5 - k.cx) / k.fx * z; val yc = -(v + 0.5 - k.cy) / k.fy * z; val zc = -z
            val wx = pose[0] * xc + pose[4] * yc + pose[8] * zc + pose[12]
            val wy = pose[1] * xc + pose[5] * yc + pose[9] * zc + pose[13]
            val wz = pose[2] * xc + pose[6] * yc + pose[10] * zc + pose[14]
            calcul.ajouterPoint(wx, -wz, wy)
        }
    }

    private fun detecterCibles(gris: ByteArray, l: Int, h: Int): List<Triple<String, Double, Double>> {
        val m = Mat(h, l, CvType.CV_8UC1).apply { put(0, 0, gris) }
        val coins = ArrayList<Mat>(); val ids = Mat()
        aruco.detectMarkers(m, coins, ids)
        // OpenCV 5 may return the ids as a row or a column, and the corners as 1x4 or 4x1:
        // read both as flat arrays, whatever their shape.
        val n = ids.total().toInt()
        if (n == 0 || coins.size != n) return emptyList()
        val tab = IntArray(n).also { Mat().apply { ids.convertTo(this, CvType.CV_32S) }.get(0, 0, it) }
        val out = ArrayList<Triple<String, Double, Double>>()
        for (i in 0 until n) {
            val id = tab[i]
            if (id !in 0..3) continue // identity always from the ArUco decoding
            val xy = FloatArray(8).also { coins[i].get(0, 0, it) }
            val u = (0 until 4).sumOf { xy[2 * it].toDouble() } / 4; val v = (0 until 4).sumOf { xy[2 * it + 1].toDouble() } / 4
            out += Triple("C$id", u, v)
        }
        return out
    }

    private fun terminer() {
        val verdict = cq.verdict()
        lifecycleScope.launch {
            val p = withContext(Dispatchers.IO) { projet.parametres(plotId) }
            val message = withContext(Dispatchers.Default) {
                val prec = projet.ouvrir().use { db -> GeoPackage.lirePoints(SqliteAndroid(db), "detections", plotId) }.map { l ->
                    groma.core.Detection(l["uuid"] as String, plotId, l["x"] as Double, l["y"] as Double, l["num_provisoire"] as String,
                        0.0, 0.0, (l["dbh_cm"] as Number).toDouble(), null, "aucune", null, "provisoire", "embarque", null)
                }
                val r = calcul.calculer(p, projet.cibles(plotId), projet.houppiers(plotId), projet.mnt(plotId), prec)
                if (r.detections.isNotEmpty()) {
                    CalculEmbarque.ecrire(projet, plotId, r.detections)
                    exporterJson(r.detections)
                    ServicePartageQfield.demarrer(this@CaptureActivity)
                }
                val sortie = File(getExternalFilesDir("paquets"), CleProjet.chemin(projet.projetId, plotId, ".groma.zip"))
                sortie.parentFile!!.mkdirs()
                val paquet = enregistreur.terminer(manifeste(p), sortie)
                Preferences.planifierEnvoi(this@CaptureActivity, paquet, projet.projetId, plotId)
                listOfNotNull("${r.detections.size} arbres provisoires.", r.message).joinToString("\n")
            }
            AlertDialog.Builder(this@CaptureActivity)
                .setTitle(if (verdict.suffisante) R.string.verdict_ok else R.string.verdict_refaire)
                .setMessage((verdict.raisons + message).joinToString("\n"))
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .show()
        }
    }

    private fun manifeste(p: groma.core.ParametresPlacette): JSONObject {
        val cibles = JSONArray(projet.cibles(plotId).map {
            JSONObject().put("id_cible", it.idCible).put("azimut_mesure", it.azimutMesure).put("distance_mesuree_m", it.distanceMesureeM).put("dbh_mesure_cm", it.dbhMesureCm)
        })
        return JSONObject()
            .put("date", java.time.Instant.now().toString())
            .put("appareil", JSONObject().put("modele", android.os.Build.MODEL))
            .put("placette", JSONObject().put("rayon_m", p.rayonM).put("seuil_diam_cm", p.seuilDiamCm).put("unite_azimut", p.uniteAzimut)
                .put("declinaison_deg", p.declinaisonDeg).put("convergence_deg", p.convergenceDeg))
            .put("centre", JSONObject().put("x", p.centreX).put("y", p.centreY))
            .put("cibles_mesurees", cibles)
            .put("suivi", JSONObject().put("boucle_fermee", true))
    }

    /** Provisional detections as JSON for the local endpoint read by the QField plugin. */
    private fun exporterJson(det: List<groma.core.Detection>) {
        val f = File(File(filesDir, "detections"), CleProjet.chemin(projet.projetId, plotId, ".json"))
        f.parentFile!!.mkdirs()
        f.writeText(JSONArray(det.map { d ->
            JSONObject(d.valeurs()).put("uuid", d.uuid).put("x", d.x).put("y", d.y)
        }).toString())
    }

    private fun compacter(img: Image, octets: Int): ByteBuffer {
        val plan = img.planes[0]
        val out = ByteBuffer.allocateDirect(img.width * img.height * octets)
        val src = plan.buffer
        for (r in 0 until img.height) {
            src.position(r * plan.rowStride)
            val ligne = ByteArray(img.width * octets)
            src.get(ligne, 0, minOf(ligne.size, src.remaining()))
            out.put(ligne)
        }
        out.flip()
        return out
    }

    private fun luminance(img: Image): ByteArray {
        val p = img.planes[0]
        val out = ByteArray(img.width * img.height)
        for (r in 0 until img.height) { p.buffer.position(r * p.rowStride); p.buffer.get(out, r * img.width, img.width) }
        return out
    }

    private fun versNv21(img: Image): ByteArray {
        val l = img.width; val h = img.height
        val out = ByteArray(l * h * 3 / 2)
        luminance(img).copyInto(out)
        val u = img.planes[1]; val v = img.planes[2]
        var o = l * h
        for (r in 0 until h / 2) for (c in 0 until l / 2) {
            out[o++] = v.buffer.get(r * v.rowStride + c * v.pixelStride)
            out[o++] = u.buffer.get(r * u.rowStride + c * u.pixelStride)
        }
        return out
    }

    companion object {
        const val EXTRA_PROJET = "projet"
        const val EXTRA_PLOT = "plot_id"
    }
}
