package groma.app

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Local read-only endpoint on 127.0.0.1 serving the provisional detections of
 * a plot as JSON (`GET /detections/<plot_id>`), for a QField project plugin
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
                    val plot = Regex("^/detections/([A-Za-z0-9_.-]{1,64})$").find(chemin)?.groupValues?.get(1)
                    val f = plot?.let { p -> File(dossierJson, "$p.json") }
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
}
