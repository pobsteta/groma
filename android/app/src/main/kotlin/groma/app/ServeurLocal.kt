package groma.app

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import groma.core.CleProjet
import groma.core.GeoPackage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Local read-only endpoint on 127.0.0.1 for the QField project plugin
 * (`qfield/inventaire_video.qml`), which inserts what Groma produced into
 * QField's own copy of inventaire_video.gpkg (Android 11+ forbids Groma from
 * writing into QField's private folder). Routes:
 *
 * * `GET /inventaire/<projet_id>`: provisional detections of every plot and
 *   measured RTK centres of the project, in one JSON object;
 * * `GET /detections/<projet_id>/<plot_id>` (and `/detections/<plot_id>`):
 *   provisional detections of one plot.
 *
 * To be validated on device: see docs/terrain.md.
 */
class ServeurLocal(private val dossierJson: File, private val dossierCopies: File? = null, val port: Int = 8765) {
    @Volatile private var socket: ServerSocket? = null

    fun demarrer() {
        if (socket != null) return
        val s = ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))
        socket = s
        thread(name = "groma-local", isDaemon = true) {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: java.io.IOException) { break }
                derniereRequeteMs = System.currentTimeMillis()
                c.use {
                    val ligne = it.getInputStream().bufferedReader().readLine() ?: return@use
                    val chemin = ligne.split(" ").getOrNull(1) ?: ""
                    val (code, corps) = try {
                        val inv = inventaire(chemin)
                        val f = if (inv == null) fichier(chemin) else null
                        when {
                            inv != null -> "200 OK" to inv.toString().toByteArray()
                            f != null && f.exists() -> "200 OK" to f.readBytes()
                            else -> "404 Not Found" to "{}".toByteArray()
                        }
                    } catch (e: Exception) {
                        Log.w("groma", "Serveur local : $chemin", e)
                        "500 Internal Server Error" to JSONObject().put("erreur", e.message).toString().toByteArray()
                    }
                    it.getOutputStream().apply {
                        write("HTTP/1.1 $code\r\nContent-Type: application/json\r\nContent-Length: ${corps.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(corps); flush()
                    }
                }
            }
        }
    }

    fun arreter() { socket?.close(); socket = null }

    /**
     * `/detections/<projet_id>/<plot_id>`, or `/detections/<plot_id>` (plugins
     * written before the project list) when only one project has that plot.
     */
    internal fun fichier(chemin: String): File? {
        val id = "[A-Za-z0-9_.-]{1,64}"
        Regex("^/detections/($id)/($id)$").find(chemin)?.let { m ->
            val (projet, plot) = m.destructured
            if (projet == ".." || projet == ".") return null
            return File(dossierJson, "$projet/$plot.json").takeIf { it.isFile }
        }
        val plot = Regex("^/detections/($id)$").find(chemin)?.groupValues?.get(1) ?: return null
        return dossierJson.listFiles { d -> d.isDirectory }.orEmpty()
            .map { File(it, "$plot.json") }.filter { it.isFile }.singleOrNull()
    }

    /**
     * `/inventaire/<projet_id>`: `{projet_id, detections: [...], centres: [...]}`,
     * coordinates in the GeoPackage CRS (Lambert-93). Detections come from the
     * JSON exports of the captures, centres from the working copy (latest per
     * plot). Null when the route does not match or the project is unknown.
     */
    internal fun inventaire(chemin: String): JSONObject? {
        val projet = Regex("^/inventaire/([A-Za-z0-9_.-]{1,64})/?$").find(chemin)?.groupValues?.get(1) ?: return null
        if (!CleProjet.valide(projet)) return null
        val detections = JSONArray()
        File(dossierJson, projet).listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.forEach { f ->
            val tab = JSONArray(f.readText())
            for (i in 0 until tab.length()) detections.put(tab.get(i))
        }
        val copie = dossierCopies?.listFiles { d -> d.isDirectory && File(d, "inventaire_video.gpkg").isFile }.orEmpty()
            .map { Projet(it) }.firstOrNull { it.projetId == projet }
        if (copie == null && detections.length() == 0) return null
        val centres = JSONArray()
        copie?.let { p ->
            SQLiteDatabase.openDatabase(p.gpkg.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val s = SqliteAndroid(db)
                val avecCentres = s.requete("SELECT 1 AS un FROM sqlite_master WHERE type = 'table' AND name = 'centres_mesures'").isNotEmpty()
                if (avecCentres) for (c in GeoPackage.dernierCentreParPlacette(s)) {
                    centres.put(JSONObject().apply { for ((k, v) in c) if (k != "fid" && v != null) put(k, v) })
                }
            }
        }
        return JSONObject().put("projet_id", projet).put("detections", detections).put("centres", centres)
    }

    companion object {
        private var instance: ServeurLocal? = null

        /** Time of the last request (ms), watched by [ServicePartageQfield]. */
        @Volatile var derniereRequeteMs = 0L

        /**
         * Starts the endpoint once per process: the activity is recreated on
         * relaunch while the daemon thread still holds the port.
         */
        @Synchronized
        fun demarrerUneFois(dossierJson: File, dossierCopies: File? = null) {
            if (instance != null) return
            instance = try {
                ServeurLocal(dossierJson, dossierCopies).also { it.demarrer() }
            } catch (e: java.io.IOException) {
                Log.w("groma", "Serveur local indisponible", e); null
            }
        }
    }
}
