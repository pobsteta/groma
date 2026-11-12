package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mission 027: reception quality recorded with the centre. Expected, written by hand:
 * - nb_positions = accepted fixes; nb_satellites = lower median of the satellites > 0 (0 = unknown, null if none);
 * - hdop = mean of the known HDOPs (null if none); age = max of the ages, null for a phone centre;
 * - duree = seconds from the first to the last accepted fix (injected clock);
 * - an old GeoPackage (no such columns) gets them on the first write, existing rows untouched.
 */
class QualiteReceptionCentreTest {
    private fun rtk(lat: Double, sat: Int, hdop: Double?, age: Double?) =
        FixGnss(Position(lat, 5.0), QualiteFix.RTK_FIXE, sat, hdop, 300.0, age, 0.02, origine = OrigineFix.RECEPTEUR)

    private fun tel(lat: Double, sat: Int) =
        FixGnss(Position(lat, 5.0), QualiteFix.AUTONOME, sat, null, 300.0, null, 3.0, origine = OrigineFix.TELEPHONE)

    private fun horloge(vararg ms: Long): () -> Long {
        val it = ms.iterator()
        return { it.next() }
    }

    @Test
    fun rtk_mediane_moyenne_maximum_duree() {
        val m = MesureCentre(false, horloge = horloge(1_000, 3_000, 4_500, 11_000))
        assertTrue(m.ajouter(rtk(47.0, 10, 0.8, 1.0)))
        assertTrue(m.ajouter(rtk(47.0, 12, 1.0, 3.5)))
        assertTrue(m.ajouter(rtk(47.0, 0, null, null)))      // unknown satellites, HDOP and age: ignored
        assertTrue(m.ajouter(rtk(47.0, 14, 1.2, 2.0)))
        val c = m.resultat()!!
        assertEquals(4, c.nbFixes)
        assertEquals(12, c.nbSatellites)   // sorted 10, 12, 14: median 12
        assertEquals(1.0, c.hdop!!, 1e-9)  // (0.8 + 1.0 + 1.2) / 3
        assertEquals(3.5, c.ageCorrectionsS!!, 1e-9)
        assertEquals(10.0, c.dureeS!!, 1e-9)
    }

    @Test
    fun satellites_pair_mediane_basse() {
        val m = MesureCentre(false, horloge = horloge(0, 1, 2, 3))
        listOf(9, 11, 13, 15).forEach { assertTrue(m.ajouter(rtk(47.0, it, 1.0, 1.0))) }
        assertEquals(11, m.resultat()!!.nbSatellites)
    }

    @Test
    fun tout_inconnu_reste_vide() {
        val m = MesureCentre(false, horloge = horloge(0))
        assertTrue(m.ajouter(rtk(47.0, 0, null, null)))
        val c = m.resultat()!!
        assertNull(c.nbSatellites)
        assertNull(c.hdop)
        assertNull(c.ageCorrectionsS)
        assertEquals(0.0, c.dureeS!!, 1e-9)
    }

    @Test
    fun telephone_sans_age_ni_hdop() {
        val m = MesureCentre(false, accepterTelephone = true, horloge = horloge(*LongArray(20) { it * 1_500L }))
        repeat(20) { assertTrue(m.ajouter(tel(47.0, 7))) }
        val c = m.resultat()!!
        assertEquals("telephone", c.qualite)
        assertEquals(20, c.nbFixes)
        assertEquals(7, c.nbSatellites)
        assertNull(c.hdop)
        assertNull(c.ageCorrectionsS)
        assertEquals(28.5, c.dureeS!!, 1e-9)
    }

    @Test
    fun texte_de_reception() {
        assertEquals("24 positions, 11 satellites, HDOP 0,9", resumeReception(24, 11, 0.9))
        assertEquals("1 position", resumeReception(1, null, null))
        assertEquals("20 positions, 7 satellites", resumeReception(20, 7, null))
        assertNull(resumeReception(null, 11, 0.9))
    }

    private fun ancien(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    private val NOUVELLES = listOf("nb_positions", "nb_satellites", "hdop", "age_corrections_s", "duree_mesure_s")
    private fun colonnes(db: SqliteJdbc) = db.requete("PRAGMA table_info(centres_mesures)").map { it["name"] }

    @Test
    fun ancien_geopackage_recoit_les_colonnes_sans_perdre_ses_lignes() = ancien { db ->
        for (n in NOUVELLES) if (n in colonnes(db)) db.executer("ALTER TABLE centres_mesures DROP COLUMN $n")
        val avant = GeoPackage.lirePoints(db, "centres_mesures")
        assertTrue(avant.isNotEmpty())
        val m = MesureCentre(false, horloge = horloge(0, 2_000))
        m.ajouter(rtk(47.0, 12, 0.9, 1.5))
        m.ajouter(rtk(47.0, 12, 0.9, 1.5))
        val c = m.resultat()!!
        val fid = GeoPackage.ajouterCentreMesure(db, "P002", 925000.0, 6700000.0, c.qualite, 0.02, 1.0, "2026-11-12T10:00:00.000Z", c)
        assertTrue(colonnes(db).containsAll(NOUVELLES))
        val apres = GeoPackage.lirePoints(db, "centres_mesures")
        assertEquals(avant.size + 1, apres.size)
        for (a in avant) { // old rows: same values, new columns empty
            val b = apres.single { (it["fid"] as Number).toLong() == (a["fid"] as Number).toLong() }
            for ((k, v) in a) assertEquals(v, b[k], k)
            for (n in NOUVELLES) assertNull(b[n], n)
        }
        val l = apres.single { (it["fid"] as Number).toLong() == fid }
        assertEquals(2, (l["nb_positions"] as Number).toInt())
        assertEquals(12, (l["nb_satellites"] as Number).toInt())
        assertEquals(0.9, (l["hdop"] as Number).toDouble(), 1e-9)
        assertEquals(1.5, (l["age_corrections_s"] as Number).toDouble(), 1e-9)
        assertEquals(2.0, (l["duree_mesure_s"] as Number).toDouble(), 1e-9)
        val carte = DonneesCarte.lire(db).centres.single { it.plotId == "P002" }
        assertEquals("2 positions, 12 satellites, HDOP 0,9", carte.reception)
    }

    @Test
    fun centre_sans_reception_laisse_les_colonnes_vides() = ancien { db ->
        val fid = GeoPackage.ajouterCentreMesure(db, "P001", 1.0, 2.0, "fix", 0.02, null)
        val l = GeoPackage.lirePoints(db, "centres_mesures", "P001").single { (it["fid"] as Number).toLong() == fid }
        for (n in NOUVELLES) assertNull(l[n], n)
        assertNull(DonneesCarte.lire(db).centres.single { it.plotId == "P001" }.reception)
        assertNotNull(l["plot_id"])
    }
}
