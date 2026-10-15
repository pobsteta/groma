package groma.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Taken from Marculus (same author): real sentences, checksums computed. */
class NmeaTest {
    private val ggaAutonome = "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"
    private val ggaRtkFixe = "\$GNGGA,141322.00,4717.112671,N,00833.914843,E,4,12,0.65,123.456,M,47.5,M,1.0,0000*62"
    private val ggaRtkFlottant = "\$GNGGA,141322.00,4717.112671,N,00833.914843,E,5,11,0.80,123.000,M,47.5,M,2.5,0000*6A"
    private val ggaSudOuest = "\$GPGGA,123519,4807.038,S,01131.000,W,1,08,0.9,545.4,M,46.9,M,,*48"
    private val ggaSansFix = "\$GNGGA,235947.00,,,,,0,00,99.99,,M,,M,,*76"
    private val gst = "\$GPGST,172814.0,0.006,0.023,0.020,273.6,0.023,0.020,0.031*6A"
    private val gsv = "\$GPGSV,3,1,11,03,03,111,00,04,15,270,00*7F"
    private val gsa = "\$GNGSA,A,3,01,02,03,04,05,06,07,08,09,10,11,12,1.21,0.65,1.02*1D"
    private val rmcMouvement = "\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A"
    private val rmcArret = "\$GNRMC,141322.00,A,4717.112671,N,00833.914843,E,,,250624,,,R*5F"

    private fun proche(attendu: Double, obtenu: Double?, tol: Double = 1e-5) =
        assertTrue(obtenu != null && abs(attendu - obtenu) <= tol, "attendu ~$attendu, obtenu $obtenu")

    @Test
    fun sommeDeControle() {
        assertTrue(Nmea.checksumValide(ggaAutonome))
        assertTrue(Nmea.checksumValide(gst))
        assertFalse(Nmea.checksumValide("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*00"))
        assertFalse(Nmea.checksumValide("sans dollar ni etoile"))
        assertNull(Nmea.gga("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*00"))
    }

    @Test
    fun ggaAutonome() {
        val t = Nmea.gga(ggaAutonome)!!
        proche(48.1173, t.position.latitude)
        proche(11.516667, t.position.longitude)
        assertEquals(QualiteFix.AUTONOME, t.qualite)
        assertFalse(t.qualite.estRtk)
        assertEquals(8, t.nbSatellites)
        proche(0.9, t.hdop, 1e-6)
        proche(545.4, t.altitudeM, 1e-6)
        assertNull(t.ageCorrectionsS)
        assertNull(t.stationRef)
    }

    @Test
    fun ggaRtk() {
        val t = Nmea.gga(ggaRtkFixe)!!
        assertEquals(QualiteFix.RTK_FIXE, t.qualite)
        assertTrue(t.qualite.estRtk)
        assertEquals(12, t.nbSatellites)
        proche(47.285211, t.position.latitude)
        proche(8.565247, t.position.longitude)
        proche(1.0, t.ageCorrectionsS, 1e-6)
        assertEquals("0000", t.stationRef)
        assertEquals(QualiteFix.RTK_FLOTTANT, Nmea.gga(ggaRtkFlottant)!!.qualite)
    }

    @Test
    fun hemispheresSudEtOuest() {
        val t = Nmea.gga(ggaSudOuest)!!
        proche(-48.1173, t.position.latitude)
        proche(-11.516667, t.position.longitude)
    }

    @Test
    fun tramesNonDecodees() {
        assertNull(Nmea.gga(ggaSansFix))
        assertNull(Nmea.gga(gsv))
        assertNull(Nmea.gst(ggaAutonome))
        assertNull(Nmea.gsa(ggaAutonome))
        assertNull(Nmea.rmc(ggaAutonome))
    }

    @Test
    fun gstGsaRmcGsv() {
        val s = Nmea.gst(gst)!!
        proche(0.023, s.ecartTypeLatM, 1e-6)
        proche(0.020, s.ecartTypeLonM, 1e-6)
        proche(0.030480, s.precisionHorizontaleM)
        val a = Nmea.gsa(gsa)!!
        proche(1.21, a.pdop, 1e-6); proche(0.65, a.hdop, 1e-6); proche(1.02, a.vdop, 1e-6)
        val r = Nmea.rmc(rmcMouvement)!!
        proche(84.4, r.capDeg, 1e-6)
        proche(22.4 * 0.514444, r.vitesseMs, 1e-4)
        val arret = Nmea.rmc(rmcArret)!!
        assertNull(arret.capDeg); assertNull(arret.vitesseMs)
        val v = Nmea.gsv(gsv)!!
        assertEquals("GP", v.systeme)
        assertEquals(listOf(3, 4), v.satellites.map { it.prn })
        assertEquals(270, v.satellites[1].azimut)
    }

    @Test
    fun fixComplet() {
        val f = Nmea.fix(Nmea.gga(ggaRtkFixe)!!, Nmea.gst(gst), Nmea.gsa(gsa), Nmea.rmc(rmcMouvement))
        assertEquals(QualiteFix.RTK_FIXE, f.qualite)
        proche(0.030480, f.precisionHorizontaleM)
        proche(1.21, f.pdop, 1e-6)
        proche(84.4, f.capDeg, 1e-6)
        assertNull(Nmea.fix(Nmea.gga(ggaAutonome)!!).precisionHorizontaleM)
    }

    @Test
    fun qualiteDepuisPrecision() {
        assertEquals(QualiteFix.AUCUN, QualiteFix.depuisPrecision(null))
        assertEquals(QualiteFix.RTK_FIXE, QualiteFix.depuisPrecision(0.02))
        assertEquals(QualiteFix.RTK_FLOTTANT, QualiteFix.depuisPrecision(0.3))
        assertEquals(QualiteFix.DGPS, QualiteFix.depuisPrecision(2.0))
        assertEquals(QualiteFix.AUTONOME, QualiteFix.depuisPrecision(8.0))
        assertEquals(QualiteFix.AUCUN, QualiteFix.depuisCode(42))
    }

    @Test
    fun decoupeur() {
        assertEquals(listOf("\$GPGGA,1,2,3"), NmeaDecoupeur().pousser("\$GPGGA,1,2,3\r\n"))
        assertEquals(listOf("\$A,1", "\$B,2"), NmeaDecoupeur().pousser("\$A,1\r\n\$B,2\r\n"))
        val d = NmeaDecoupeur()
        assertTrue(d.pousser("\$GPGGA,12").isEmpty())
        assertTrue(d.pousser("3,45").isEmpty())
        assertEquals(listOf("\$GPGGA,123,45"), d.pousser("\r\n"))
        assertEquals(listOf("\$X,1"), NmeaDecoupeur().pousser("\r\n\n\$X,1\r\r\n"))
        // Runaway guard: a line over the maximum size is dropped, the next one comes through.
        val petit = NmeaDecoupeur(tailleMax = 8)
        petit.pousser("0123456789ABCDEF")
        assertEquals(listOf("\$OK,1"), petit.pousser("\$OK,1\r\n"))
    }

    @Test
    fun lecteurCompleteLesFix() {
        val l = LecteurNmea()
        // Sentences split anywhere, GST and GSV before the GGA.
        val flux = "$gst\r\n$gsv\r\n$gsa\r\n$ggaRtkFixe\r\n"
        val fixes = flux.chunked(7).flatMap { l.pousser(it).second }
        val f = fixes.single()
        assertEquals(QualiteFix.RTK_FIXE, f.qualite)
        proche(0.030480, f.precisionHorizontaleM)
        proche(1.21, f.pdop, 1e-6)
        // GSV sequence 1 of 3 not complete: no satellite yet.
        assertEquals(emptyList(), f.satellites)
        assertEquals(ggaRtkFixe, l.derniereGga)
        // Complete one-message sequence: kept for the next fix.
        val corps = "GLGSV,1,1,01,65,40,120,35"
        val gsvSeul = "\$$corps*${Nmea.sommeControle(corps)}"
        assertEquals(listOf(65), l.pousser("$gsvSeul\r\n$ggaAutonome\r\n").second.single().satellites.map { it.prn })
    }
}
