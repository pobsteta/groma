package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeureUtcDuFixTest {
    private fun trame(corps: String) = "\$" + corps + "*" + Nmea.sommeControle(corps)
    private fun gga(heure: String) = trame("GNGGA,$heure,4717.112671,N,00833.914843,E,4,12,0.65,123.456,M,47.5,M,1.0,0000")
    private fun rmc(heure: String, statut: String, date: String) = trame("GNRMC,$heure,$statut,4717.112671,N,00833.914843,E,,,$date,,,R")
    private fun utc(a: Int, m: Int, j: Int, h: Int, mi: Int, s: Int, ms: Int = 0) =
        java.time.LocalDateTime.of(a, m, j, h, mi, s).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() + ms

    private fun fix(vararg trames: String): FixGnss =
        LecteurNmea().pousser(trames.joinToString("") { it + "\r\n" }).second.single()

    @Test fun dateDeLaRmcEtCentiemes() {
        val r = Nmea.rmc(rmc("141322.50", "A", "250624"))!!
        assertEquals(utc(2024, 6, 25, 14, 13, 22, 500), r.instantUtcMs)
        assertEquals(utc(2024, 6, 25, 14, 13, 22, 500), fix(rmc("141322.50", "A", "250624"), gga("141322.50")).heureUtcMs)
    }

    @Test fun anneeBissextile() {
        assertEquals(utc(2024, 2, 29, 0, 0, 1), Nmea.rmc(rmc("000001", "A", "290224"))!!.instantUtcMs)
        assertEquals(utc(2024, 3, 1, 12, 0, 0), Nmea.rmc(rmc("120000", "A", "010324"))!!.instantUtcMs)
    }

    @Test fun rmcInvalideOuAbsente() {
        assertNull(Nmea.rmc(rmc("141322.00", "V", "250624"))!!.instantUtcMs)
        assertNull(fix(rmc("141322.00", "V", "250624"), gga("141322.00")).heureUtcMs)
        assertNull(fix(gga("141322.00")).heureUtcMs)
        assertNull(Nmea.rmc(rmc("141322.00", "A", ""))!!.instantUtcMs)
    }

    @Test fun ggaSansHeure() {
        assertNull(fix(rmc("141322.00", "A", "250624"), gga("")).heureUtcMs)
    }

    @Test fun minuitRmcApresGga() {
        // GGA at 23:59:59.9 (eve), RMC already 00:00:00.1 on the 26th: the GGA belongs to the 25th.
        assertEquals(utc(2024, 6, 25, 23, 59, 59, 900), fix(rmc("000000.10", "A", "260624"), gga("235959.90")).heureUtcMs)
    }

    @Test fun minuitRmcAvantGga() {
        // RMC 23:59:59.9 on the 25th, GGA already 00:00:00.1: the GGA belongs to the 26th.
        assertEquals(utc(2024, 6, 26, 0, 0, 0, 100), fix(rmc("235959.90", "A", "250624"), gga("000000.10")).heureUtcMs)
    }

    @Test fun memeJour() {
        assertEquals(utc(2024, 6, 25, 12, 0, 0), fix(rmc("120000.00", "A", "250624"), gga("120000.00")).heureUtcMs)
    }

    @Test fun instantMonotone() {
        var t = 1000L
        val l = LecteurNmea { t }
        assertEquals(1000L, l.pousser(gga("141322.00") + "\r\n").second.single().recuMonotoneMs)
        t = 2500L
        assertEquals(2500L, l.pousser(gga("141323.00") + "\r\n").second.single().recuMonotoneMs)
        assertNull(fix(gga("141322.00")).recuMonotoneMs)
    }
}
