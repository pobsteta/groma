package groma.app.gnss

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import groma.core.Ntrip
import groma.core.PointMontage
import groma.core.StatutNtrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Two-way byte link to an external GNSS receiver: NMEA in, RTCM out.
 * Taken from Marculus (same author).
 */
interface Transport {
    /** Received bytes; ends when the link closes or on an I/O error. */
    fun lire(): Flow<ByteArray>

    /** Sends bytes to the receiver (RTCM corrections). */
    suspend fun ecrire(donnees: ByteArray)
}

private const val TAILLE_TAMPON = 4096

/**
 * Closes [c] as soon as the collecting coroutine ends: a blocking socket read
 * ignores cancellation, closing the socket is what stops it (else « Libérer
 * le récepteur » would leave the Bluetooth link open).
 */
private suspend fun fermerALaFin(c: java.io.Closeable) {
    kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { runCatching { c.close() } }
}

/** Reads [entree] until its end, emitting each chunk. */
private suspend fun kotlinx.coroutines.flow.FlowCollector<ByteArray>.relayer(entree: InputStream) {
    val tampon = ByteArray(TAILLE_TAMPON)
    while (true) {
        val n = entree.read(tampon)
        if (n < 0) break
        if (n > 0) emit(tampon.copyOf(n))
    }
}

/**
 * Bluetooth serial port (SPP) to a paired receiver. BLUETOOTH_CONNECT
 * (API 31+) is checked by the caller. A SPP link serves one app at a time:
 * while Groma holds it, QField cannot, and the other way round.
 */
class TransportBluetooth(private val appareil: BluetoothDevice) : Transport {
    @Volatile private var sortie: OutputStream? = null

    override fun lire(): Flow<ByteArray> = flow {
        val socket = ouvrir()
        fermerALaFin(socket)
        try {
            sortie = socket.outputStream
            relayer(socket.inputStream)
        } finally {
            sortie = null
            runCatching { socket.close() }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun ecrire(donnees: ByteArray) {
        runCatching { sortie?.apply { write(donnees); flush() } }
    }

    /**
     * RFCOMM channel: secure SPP socket, then insecure, then channel 1 by
     * reflection (works around the frequent "read failed, socket might closed").
     */
    @SuppressLint("MissingPermission")
    private fun ouvrir(): BluetoothSocket {
        val fabriques: List<() -> BluetoothSocket> = listOf(
            { appareil.createRfcommSocketToServiceRecord(SPP) },
            { appareil.createInsecureRfcommSocketToServiceRecord(SPP) },
            { appareil.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(appareil, 1) as BluetoothSocket },
        )
        var derniere: Exception? = null
        for (f in fabriques) {
            val s = runCatching { f() }.getOrElse { derniere = it as? Exception; null } ?: continue
            try {
                s.connect()
                return s
            } catch (e: Exception) {
                derniere = e
                runCatching { s.close() }
            }
        }
        throw derniere ?: IOException("Connexion Bluetooth impossible")
    }

    private companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

/**
 * TCP link to a receiver serving NMEA on a socket (Wi-Fi companion, or a PC
 * replaying a log: `nc -l 2947 < trace.nmea`, handy to test without hardware).
 */
class TransportTcp(private val hote: String, private val port: Int) : Transport {
    @Volatile private var sortie: OutputStream? = null

    override fun lire(): Flow<ByteArray> = flow {
        val socket = Socket()
        fermerALaFin(socket)
        try {
            socket.connect(InetSocketAddress(hote, port), 5000)
            sortie = socket.getOutputStream()
            relayer(socket.getInputStream())
        } finally {
            sortie = null
            runCatching { socket.close() }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun ecrire(donnees: ByteArray) {
        runCatching { sortie?.apply { write(donnees); flush() } }
    }
}

/**
 * NTRIP client, network side: connects to the caster, checks the status and
 * streams the RTCM3 corrections; [envoyerGga] gives the rover position back
 * (VRS / NEAR mount points). Protocol in groma-core ([Ntrip]).
 */
class ClientNtrip(
    private val hote: String,
    private val port: Int,
    private val pointMontage: String,
    private val utilisateur: String,
    private val motDePasse: String,
) {
    @Volatile private var sortie: OutputStream? = null

    val connecte: Boolean get() = sortie != null

    /** RTCM3 stream; ends on a refused login or when the connection closes. */
    fun corrections(onStatut: (StatutNtrip) -> Unit): Flow<ByteArray> = flow {
        val socket = Socket()
        fermerALaFin(socket)
        try {
            socket.connect(InetSocketAddress(hote, port), 8000)
            val out = socket.getOutputStream()
            out.write(Ntrip.requete(pointMontage, hote, port, utilisateur, motDePasse).toByteArray(Charsets.US_ASCII))
            out.flush()
            val entree = socket.getInputStream()
            // "ICY 200 OK" (NTRIP 1) or "HTTP/1.1 200 OK" (NTRIP 2, followed by headers).
            val ligne = lireLigne(entree)
            val statut = Ntrip.statut(ligne)
            onStatut(statut)
            if (statut != StatutNtrip.OK) return@flow
            if (!ligne.startsWith("ICY")) {
                var total = 0
                while (total < 4096) {
                    val l = lireLigne(entree)
                    total += l.length + 2
                    if (l.isEmpty()) break
                }
            }
            sortie = out
            relayer(entree)
        } finally {
            sortie = null
            runCatching { socket.close() }
        }
    }.flowOn(Dispatchers.IO)

    /** Sends a GGA sentence to the caster; bytes written, 0 when not connected. */
    suspend fun envoyerGga(phrase: String): Int = withContext(Dispatchers.IO) {
        val d = (phrase + "\r\n").toByteArray(Charsets.US_ASCII)
        runCatching { sortie?.let { it.write(d); it.flush(); d.size } ?: 0 }.getOrDefault(0)
    }

    private fun lireLigne(entree: InputStream): String {
        val sb = StringBuilder()
        while (sb.length < 1024) {
            val b = entree.read()
            if (b < 0 || b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    companion object {
        /** Mount points of the caster (`GET /` source table); empty on any failure. */
        suspend fun pointsMontage(hote: String, port: Int): List<PointMontage> = withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(hote, port), 5000)
                socket.soTimeout = 8000
                socket.getOutputStream().apply { write(Ntrip.requete("", hote, port, "", "").toByteArray(Charsets.US_ASCII)); flush() }
                val recu = ByteArrayOutputStream()
                val tampon = ByteArray(TAILLE_TAMPON)
                val entree = socket.getInputStream()
                runCatching {
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        recu.write(tampon, 0, n)
                        if (recu.size() > 256 * 1024 || recu.toString("US-ASCII").contains("ENDSOURCETABLE")) break
                    }
                }
                Ntrip.tableSources(recu.toString("US-ASCII"))
            } catch (_: Exception) {
                emptyList()
            } finally {
                runCatching { socket.close() }
            }
        }
    }
}
