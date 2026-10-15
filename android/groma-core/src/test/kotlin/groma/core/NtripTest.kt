package groma.core

import java.util.Base64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Taken from Marculus (same author). */
class NtripTest {
    private fun proche(attendu: Double, obtenu: Double, tol: Double = 1e-4) = assertTrue(abs(attendu - obtenu) <= tol, "attendu ~$attendu, obtenu $obtenu")

    @Test
    fun requete() {
        val r = Ntrip.requete("CT", "caster.centipede.fr", 2101, "user", "pass")
        assertTrue(r.startsWith("GET /CT HTTP/1.1\r\n"))
        assertTrue(r.contains("Ntrip-Version: Ntrip/2.0\r\n"))
        assertTrue(r.contains("User-Agent: NTRIP Groma\r\n"))
        assertTrue(r.endsWith("\r\n\r\n"))
        val b64 = r.lineSequence().first { it.startsWith("Authorization: Basic ") }.removePrefix("Authorization: Basic ").trim()
        assertEquals("user:pass", String(Base64.getDecoder().decode(b64)))
        assertTrue(Ntrip.requete("/CT", "h", 2101, "u", "p").startsWith("GET /CT HTTP/1.1"))
    }

    @Test
    fun ggaRelisible() {
        val gga = Ntrip.gga(Position(48.1173, 11.516667), heureUtc = "123519.00")
        assertTrue(Nmea.checksumValide(gga))
        val t = Nmea.gga(gga)!!
        proche(48.1173, t.position.latitude)
        proche(11.516667, t.position.longitude)
        val so = Nmea.gga(Ntrip.gga(Position(-48.1173, -11.516667)))!!
        proche(-48.1173, so.position.latitude)
        proche(-11.516667, so.position.longitude)
    }

    @Test
    fun tableDesSources() {
        val st = """
            SOURCETABLE 200 OK
            CAS;caster.centipede.fr;2101;Centipede;
            STR;CT;Centipede;RTCM 3.2;1004(1),1012(1);2;GPS+GLO;SNIP;FRA;
            STR;LIENSS;La Rochelle;RTCM 3.3;;2;GPS+GLO+GAL;Centipede;FRA;
            NET;Centipede;centipede;B;N;
            ENDSOURCETABLE
        """.trimIndent()
        val e = Ntrip.tableSources(st)
        assertEquals(listOf("CT", "LIENSS"), e.map { it.nom })
        assertEquals("Centipede", e[0].identifiant)
        assertEquals("RTCM 3.2", e[0].format)
    }

    @Test
    fun statut() {
        assertEquals(StatutNtrip.OK, Ntrip.statut("ICY 200 OK"))
        assertEquals(StatutNtrip.OK, Ntrip.statut("HTTP/1.1 200 OK"))
        assertEquals(StatutNtrip.NON_AUTORISE, Ntrip.statut("HTTP/1.1 401 Unauthorized"))
        assertEquals(StatutNtrip.TABLE_SOURCES, Ntrip.statut("SOURCETABLE 200 OK"))
        assertEquals(StatutNtrip.INCONNU, Ntrip.statut("n'importe quoi"))
    }
}
