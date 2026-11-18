package groma.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import fr.nemeton.terrain.geo.Position as TerrainPosition
import fr.nemeton.terrain.gnss.EntreeSourcetable
import fr.nemeton.terrain.gnss.NmeaDecoupeur as TerrainDecoupeur
import fr.nemeton.terrain.gnss.NmeaParser
import fr.nemeton.terrain.gnss.Ntrip as TerrainNtrip
import fr.nemeton.terrain.gnss.StatutNtrip as TerrainStatut

/**
 * Mission 020 (brief bibliotheques-terrain): terrain-gnss, terrain-gpkg and terrain-journal at v0.7.1
 * against what Groma does today. Position, Projections, Geodesie, BaseSqlite and the GeoPackage format layer
 * now come from terrain (Terrain.kt); Groma keeps its own NMEA / NTRIP / GeoPackage business code: terrain v0.7.1
 * lacks the UTC instant of the fix, the monotonic stamp, `LecteurNmea` and the TELEPHONE / RECEPTEUR
 * origin (missions 019, 025), and its QualiteFix names differ (RTK_FLOAT, INVALIDE). These tests pin
 * the parts that ARE identical, so the day terrain catches up the swap is a deletion, not a rewrite;
 * a red test here means one of the two codes drifted.
 */
class EquivalenceTerrainBibliothequesTest {
    private val ggas = listOf(
        "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47",
        "\$GNGGA,141322.00,4717.112671,N,00833.914843,E,4,12,0.65,123.456,M,47.5,M,1.0,0000*62",
        "\$GNGGA,141322.00,4717.112671,N,00833.914843,E,5,11,0.80,123.000,M,47.5,M,2.5,0000*6A",
        "\$GPGGA,123519,4807.038,S,01131.000,W,1,08,0.9,545.4,M,46.9,M,,*48",
    )

    @Test
    fun ggaDecodeIdentiquement() {
        for (g in ggas) {
            val a = assertNotNull(Nmea.gga(g))
            val t = assertNotNull(NmeaParser.parseGga(g))
            assertEquals(a.position.latitude, t.position.latitude, g)
            assertEquals(a.position.longitude, t.position.longitude, g)
            assertEquals(a.qualite.codeNmea, t.qualite.codeNmea, g)
            assertEquals(a.nbSatellites, t.nbSatellites, g)
            assertEquals(a.hdop, t.hdop, g)
            assertEquals(a.altitudeM, t.altitudeM, g)
            assertEquals(a.ageCorrectionsS, t.ageCorrectionsS, g)
            assertEquals(a.stationRef, t.stationRef, g)
        }
    }

    @Test
    fun ggaSansFixOuAltereeRefuseDesDeuxCotes() {
        val sansFix = "\$GNGGA,235947.00,,,,,0,00,99.99,,M,,M,,*76"
        val altere = ggas[0].replace("*47", "*00")
        for (g in listOf(sansFix, altere)) {
            assertNull(Nmea.gga(g), g)
            assertNull(NmeaParser.parseGga(g), g)
        }
    }

    @Test
    fun sommeDeControleIdentique() {
        for (corps in listOf("GPGGA,123519,4807.038,N", "GNRMC,1,A,2", "")) {
            assertEquals(Nmea.sommeControle(corps), NmeaParser.sommeControle(corps))
        }
    }

    @Test
    fun decoupeurIdentiqueSurUnFluxFragmenteEtCorrompu() {
        val flux = "\$GPGG" + "A,1*00\r\n\$GPRMC,2*11\r\n\$GPGSA,partiel\$GPGLL,3*22\n" + "x".repeat(2000) + "\r\n\$GNGGA,fin\r\n"
        val a = NmeaDecoupeur()
        val t = TerrainDecoupeur()
        val morceaux = flux.chunked(7)
        assertEquals(morceaux.flatMap { t.pousser(it) }, morceaux.flatMap { a.pousser(it) })
    }

    @Test
    fun ntripRequeteGgaSourcetableEtStatutIdentiques() {
        assertEquals(
            Ntrip.requete("/RTCM3", "caster.exemple.fr", 2101, "u", "p", "Groma"),
            TerrainNtrip.requete("/RTCM3", "caster.exemple.fr", 2101, "u", "p", "Groma"),
        )
        assertEquals(
            Ntrip.gga(Position(47.286, 5.0), "141322.00", 4, 12, 0.7, 250.5),
            TerrainNtrip.gga(TerrainPosition(47.286, 5.0), "141322.00", 4, 12, 0.7, 250.5),
        )
        assertEquals(Ntrip.gga(Position(-33.9, -70.65)), TerrainNtrip.gga(TerrainPosition(-33.9, -70.65)))
        val table = "STR;MP1;Dijon;RTCM 3.2;x\nCAS;truc\nSTR;MP2;Beaune;RTCM 3.3;y\nSTR;court"
        assertEquals(
            Ntrip.tableSources(table).map { Triple(it.nom, it.identifiant, it.format) },
            TerrainNtrip.parseSourcetable(table).map { Triple(it.mountpoint, it.identifiant, it.format) },
        )
        assertEquals(
            listOf(EntreeSourcetable("MP1", "Dijon", "RTCM 3.2"), EntreeSourcetable("MP2", "Beaune", "RTCM 3.3")),
            TerrainNtrip.parseSourcetable(table),
        )
        val statuts = listOf(
            Triple("ICY 200 OK", StatutNtrip.OK, TerrainStatut.OK),
            Triple("HTTP/1.1 401 Unauthorized", StatutNtrip.NON_AUTORISE, TerrainStatut.NON_AUTORISE),
            Triple("SOURCETABLE 200 OK", StatutNtrip.TABLE_SOURCES, TerrainStatut.SOURCETABLE),
            Triple("HTTP/1.1 404", StatutNtrip.INCONNU, TerrainStatut.INCONNU),
        )
        val correspondance = mapOf(
            StatutNtrip.OK to TerrainStatut.OK, StatutNtrip.NON_AUTORISE to TerrainStatut.NON_AUTORISE,
            StatutNtrip.TABLE_SOURCES to TerrainStatut.SOURCETABLE, StatutNtrip.INCONNU to TerrainStatut.INCONNU,
        )
        assertEquals(StatutNtrip.entries.toSet(), correspondance.keys) // every Groma status has a terrain twin
        assertEquals(TerrainStatut.entries.toSet(), correspondance.values.toSet())
        for ((ligne, groma, terrain) in statuts) {
            assertEquals(terrain, correspondance.getValue(Ntrip.statut(ligne)), ligne)
            assertEquals(groma, Ntrip.statut(ligne), ligne)
            assertEquals(terrain, TerrainNtrip.statutReponse(ligne), ligne)
        }
    }

    @Test
    fun pointGeopackageOctetsEcritsAlaMain() {
        // Hand-written expected blob: "GP", version 0, flags 0x01 (little endian, no envelope), srs 2154, WKB point.
        val attendu = byteArrayOf(0x47, 0x50, 0, 1, 0x6A, 0x08, 0, 0, 1, 1, 0, 0, 0) +
            java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN).putDouble(850_000.5).putDouble(6_700_000.25).array()
        assertContentEquals(attendu, GeoPackage.encoderPoint(850_000.5, 6_700_000.25, 2154))
        assertEquals(850_000.5 to 6_700_000.25, GeoPackage.decoderPoint(attendu))
    }

    @Test
    fun azimutGeodesieCoherentAvecAzimutDeGroma() {
        val de = Position(47.0, 5.0)
        for (vers in listOf(Position(47.001, 5.001), Position(46.999, 5.0), Position(47.0, 4.999), Position(46.998, 4.998), Position(47.0, 5.0))) {
            val (e, n) = Geodesie.decalage(de, vers)
            assertEquals(Azimut.azimut(e, n), Geodesie.azimutDeg(de, vers), 1e-9)
            assertEquals(Math.hypot(e, n), Geodesie.distanceM(de, vers), 1e-9)
        }
    }

    @Test
    fun lambertAllerRetourEtCentreDeProjection() {
        val (x, y) = Projections.versLambert93(3.0, 46.5)
        assertEquals(700_000.0, x, 1e-6)
        assertEquals(6_600_000.0, y, 1e-6)
        val (lon, lat) = Projections.versWgs84(x, y)
        assertEquals(3.0, lon, 1e-9)
        assertEquals(46.5, lat, 1e-9)
    }
}
