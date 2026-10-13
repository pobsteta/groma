package groma.app

import android.util.Log
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Local read-only endpoint on 127.0.0.1 serving the provisional detections of
 * a plot as JSON (`GET /detections/<projet_id>/<plot_id>`), for a QField project plugin
 * that inserts them into QField's own copy of inventaire_video.gpkg (Android
 * 11+ forbids Groma from writing into QField's private folder). To be
 * validated on device: see docs/terrain.md.
 */
class ServeurLocal(private val dossierJson: File, val port: Int = 8765) {
    @Volatile private var socket: ServerSocket? = null

    fun demarrer() {
        if (socket != null) return
        val s = ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))
        socket = s
        thread(name = "groma-local", isDaemon = true) {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: java.io.IOException) { break }
                c.use {
                    val ligne = it.getInputStream().bufferedReader().readLine() ?: return@use
                    val chemin = ligne.split(" ").getOrNull(1) ?: ""
                    val f = fichier(chemin)
                    val (code, corps) = if (f != null && f.exists()) "200 OK" to f.readBytes() else "404 Not Found" to "{}".toByteArray()
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

    companion object {
        private var instance: ServeurLocal? = null

        /**
         * Starts the endpoint once per process: the activity is recreated on
         * relaunch while the daemon thread still holds the port.
         */
        @Synchronized
        fun demarrerUneFois(dossierJson: File) {
            if (instance != null) return
            instance = try {
                ServeurLocal(dossierJson).also { it.demarrer() }
            } catch (e: java.io.IOException) {
                Log.w("groma", "Serveur local indisponible", e); null
            }
        }
    }
}
