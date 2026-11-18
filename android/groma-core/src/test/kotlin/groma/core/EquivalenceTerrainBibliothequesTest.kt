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
import fr.nemeton.terrain.gpkg.FormatGpkg
import fr.nemeton.terrain.gpkg.Geometrie
import fr.nemeton.terrain.journal.Fusion

/**
 * Mission 020 (brief bibliotheques-terrain): terrain-gnss, terrain-gpkg and terrain-journal at v0.7.1
 * against what Groma does today. Groma keeps its own NMEA / NTRIP / GeoPackage code: terrain v0.7.1
 * lacks the UTC instant of the fix, the monotonic stamp, `LecteurNmea` and the TELEPHONE / RECEPTEUR
 * origin (missions 019, 025), and its QualiteFix names differ (RTK_FLOAT, INVALIDE). These tests pin
 * the parts that ARE identical, so the day terrain catches up the swap is a deletion, not a rewrite;
 * a red test here means one of the two codes drifted. Test-only dependency (docs/dependances.md).
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
        for ((ligne, groma, terrain) in statuts) {
            assertEquals(groma, Ntrip.statut(ligne), ligne)
            assertEquals(terrain, TerrainNtrip.statutReponse(ligne), ligne)
        }
    }

    @Test
    fun pointGeopackageIdentiqueOctetParOctet() {
        for ((x, y) in listOf(850_000.5 to 6_700_000.25, 0.0 to 0.0, -1.5 to 12.75)) {
            val groma = GeoPackage.encoderPoint(x, y, 2154)
            val terrain = FormatGpkg.encoder(Geometrie.Point(x, y), 2154)
            assertContentEquals(groma, terrain)
            val (gx, gy) = GeoPackage.decoderPoint(terrain)
            val relu = FormatGpkg.decoder(groma) as Geometrie.Point
            assertEquals(gx, relu.x)
            assertEquals(gy, relu.y)
            assertEquals(2154, FormatGpkg.srsId(groma))
        }
    }

    @Test
    fun fusionDerniereEcritureGagne() {
        // terrain-journal replaces nothing in Groma (no multi-device merge): the rule is pinned for later.
        data class Saisie(val uuid: String, val modifie: Long, val note: String)
        val locales = listOf(Saisie("a", 10, "locale"), Saisie("b", 20, "locale"))
        val entrantes = listOf(Saisie("a", 10, "distante"), Saisie("b", 30, "distante"), Saisie("c", 5, "distante"))
        val union = Fusion.union(locales, entrantes, { it.uuid }, { it.modifie }).associateBy { it.uuid }
        assertEquals("locale", union.getValue("a").note) // equal stamp: local kept
        assertEquals("distante", union.getValue("b").note) // strictly newer wins
        assertEquals("distante", union.getValue("c").note) // unknown added, nothing deleted
        assertEquals(3, union.size)
    }
}
