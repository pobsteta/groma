package groma.core

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mission 024: the centre measured with the phone's own GNSS (tests written before mission 025).
 *
 * API fixed here (additions only; existing calls and tests keep compiling and passing):
 *  - `MesureCentre(accepterFlottant: Boolean, accepterTelephone: Boolean = false)`.
 *  - `MesureCentre.DUREE_TELEPHONE_S = 30`, `MIN_FIXES_TELEPHONE = 20`, `PRECISION_MAX_TELEPHONE_M = 10.0`
 *    (provisional values, frozen after three test plots). The duration is driven by the UI (FixGnss has
 *    no timestamp); the logic only knows the number of fixes.
 *  - `ajouter(f)` with `accepterTelephone`: accepts a TELEPHONE-origin fix whatever its claimed
 *    `qualite` (even RTK_FIXE), if `precisionHorizontaleM` is non-null and <= 10.0 (10.0 accepted).
 *    Without `accepterTelephone`: refused (current behaviour).
 *  - Origins are never mixed: `accepterTelephone` never makes a RECEPTEUR fix acceptable (a DGPS or
 *    autonomous receiver fix stays refused; RTK fixes still follow the usual rules), and the first accepted
 *    fix fixes the origin of the measure: a fix of the other origin is then refused.
 *  - `resultat()` of a telephone measure: null while fewer than 20 accepted fixes; then the mean,
 *    qualite "telephone", precisionM = max(RMS spread, RMS announced), nbFixes = accepted fixes.
 *  - `MesureCentre.suffisant: Boolean`: telephone measure -> nombre >= 20; RTK measure -> nombre >= 1.
 *  - `MesureCentre.autorisation(f, telephoneDisponible: Boolean = false)`: unchanged when the second
 *    argument is omitted/false (a TELEPHONE fix gives Autorisation.TELEPHONE = refusal, as today, so the
 *    existing test stays valid). With true, a TELEPHONE fix with qualite != AUCUN gives the new value
 *    `Autorisation.CONFIRMER_TELEPHONE`; a RECEPTEUR fix is unaffected; null/AUCUN stay SANS_POSITION.
 *  - `MesureCentre.remplacementAConfirmer(ancienne: String?, nouvelle: String): Boolean`: true only for
 *    (ancienne in {"fix","float"}, nouvelle == "telephone").
 */
class MesureCentreTelephoneTest {
    private fun tel(lat: Double, lon: Double = 5.0, precision: Double? = 3.0, qualite: QualiteFix = QualiteFix.AUTONOME) =
        FixGnss(Position(lat, lon), qualite, 12, 1.0, 300.0, null, precision, origine = OrigineFix.TELEPHONE)

    private fun rec(lat: Double, lon: Double = 5.0, qualite: QualiteFix = QualiteFix.RTK_FIXE, precision: Double? = 0.02) =
        FixGnss(Position(lat, lon), qualite, 20, 0.6, 300.0, 1.0, precision, origine = OrigineFix.RECEPTEUR)

    /** [n] phone fixes alternating between lat 47.0 and 47.00002 (2.22 m apart). */
    private fun remplir(m: MesureCentre, n: Int, precision: Double = 3.0) {
        repeat(n) { assertTrue(m.ajouter(tel(if (it % 2 == 0) 47.0 else 47.00002, precision = precision))) }
    }

    @Test
    fun constantes_provisoires() {
        assertEquals(30, MesureCentre.DUREE_TELEPHONE_S)
        assertEquals(20, MesureCentre.MIN_FIXES_TELEPHONE)
        assertEquals(10.0, MesureCentre.PRECISION_MAX_TELEPHONE_M, 0.0)
    }

    // --- acceptance ---

    @Test
    fun sans_confirmation_le_telephone_est_refuse() {
        val m = MesureCentre(accepterFlottant = false)
        assertFalse(m.ajouter(tel(47.0)))
        assertEquals(0, m.nombre)
        assertNull(m.resultat())
        // Flottant confirmation is not a phone confirmation.
        assertFalse(MesureCentre(accepterFlottant = true).ajouter(tel(47.0)))
    }

    @Test
    fun avec_confirmation_le_telephone_est_accepte_meme_s_il_se_dit_rtk() {
        val m = MesureCentre(accepterFlottant = false, accepterTelephone = true)
        assertTrue(m.ajouter(tel(47.0)))
        assertTrue(m.ajouter(tel(47.0, qualite = QualiteFix.RTK_FIXE, precision = 0.03)))
        assertEquals(2, m.nombre)
    }

    @Test
    fun precision_annoncee_au_dela_de_10_m_ecartee() {
        val m = MesureCentre(false, accepterTelephone = true)
        assertTrue(m.ajouter(tel(47.0, precision = 10.0)))
        assertFalse(m.ajouter(tel(47.0, precision = 10.01)))
        assertFalse(m.ajouter(tel(47.0, precision = 35.0)))
        assertEquals(1, m.nombre)
    }

    @Test
    fun precision_inconnue_ecartee() {
        val m = MesureCentre(false, accepterTelephone = true)
        assertFalse(m.ajouter(tel(47.0, precision = null)))
        assertFalse(m.ajouter(tel(47.0, precision = null, qualite = QualiteFix.AUCUN)))
        assertEquals(0, m.nombre)
    }

    @Test
    fun la_confirmation_telephone_ne_s_applique_pas_au_recepteur() {
        val m = MesureCentre(accepterFlottant = false, accepterTelephone = true)
        assertFalse(m.ajouter(rec(47.0, qualite = QualiteFix.DGPS, precision = 0.8)))
        assertFalse(m.ajouter(rec(47.0, qualite = QualiteFix.AUTONOME, precision = 2.0)))
        assertFalse(m.ajouter(rec(47.0, qualite = QualiteFix.RTK_FLOTTANT, precision = 0.4)))
        assertEquals(0, m.nombre)
        // RTK fixed receiver fixes keep their usual rule.
        assertTrue(m.ajouter(rec(47.0)))
    }

    @Test
    fun les_origines_ne_se_melangent_pas() {
        val a = MesureCentre(false, accepterTelephone = true)
        assertTrue(a.ajouter(tel(47.0)))
        assertFalse(a.ajouter(rec(47.0)))
        assertEquals(1, a.nombre)

        val b = MesureCentre(false, accepterTelephone = true)
        assertTrue(b.ajouter(rec(47.0)))
        assertFalse(b.ajouter(tel(47.0)))
        assertEquals(1, b.nombre)
        assertEquals("fix", b.resultat()!!.qualite)
    }

    // --- mean, count, precision ---

    @Test
    fun moins_de_20_fixes_pas_de_centre() {
        val m = MesureCentre(false, accepterTelephone = true)
        remplir(m, 19)
        assertEquals(19, m.nombre)
        assertFalse(m.suffisant)
        assertNull(m.resultat())
        assertTrue(m.ajouter(tel(47.0)))
        assertTrue(m.suffisant)
        assertNotNull(m.resultat())
    }

    @Test
    fun suffisant_au_rtk_des_un_fix() {
        val m = MesureCentre(false)
        assertFalse(m.suffisant)
        m.ajouter(rec(47.0))
        assertTrue(m.suffisant)
        assertEquals("fix", m.resultat()!!.qualite)
    }

    @Test
    fun moyenne_qualite_telephone_et_precision_max_dispersion_annoncee() {
        // 10 fixes at 47.0, 10 at 47.00002: mean 47.00001; RMS spread = 1.11 m; announced 3 m wins.
        val m = MesureCentre(false, accepterTelephone = true)
        remplir(m, 20, precision = 3.0)
        val c = m.resultat()!!
        assertEquals(20, c.nbFixes)
        assertEquals(47.00001, c.latitude, 1e-9)
        assertEquals(5.0, c.longitude, 1e-9)
        assertEquals("telephone", c.qualite)
        assertEquals(3.0, c.precisionM, 1e-9)
    }

    @Test
    fun la_dispersion_l_emporte_sur_une_precision_annoncee_optimiste() {
        val m = MesureCentre(false, accepterTelephone = true)
        remplir(m, 20, precision = 0.5)
        assertEquals(1.11, m.resultat()!!.precisionM, 0.01)
        // Announced "RTK fixed 3 cm" from the phone does not make the centre "fix".
        val n = MesureCentre(false, accepterTelephone = true)
        repeat(20) { n.ajouter(tel(47.0, qualite = QualiteFix.RTK_FIXE, precision = 0.03)) }
        assertEquals("telephone", n.resultat()!!.qualite)
        assertEquals(0.03, n.resultat()!!.precisionM, 1e-9)
    }

    @Test
    fun les_fixes_ecartes_ne_comptent_pas_dans_la_moyenne() {
        val m = MesureCentre(false, accepterTelephone = true)
        remplir(m, 20)
        assertFalse(m.ajouter(tel(48.0, precision = 25.0)))
        val c = m.resultat()!!
        assertEquals(20, c.nbFixes)
        assertEquals(47.00001, c.latitude, 1e-9)
    }

    // --- authorisation ---

    @Test
    fun autorisation_telephone() {
        val t = tel(47.0)
        // Omitted or false: today's behaviour (refusal).
        assertEquals(MesureCentre.Autorisation.TELEPHONE, MesureCentre.autorisation(t))
        assertEquals(MesureCentre.Autorisation.TELEPHONE, MesureCentre.autorisation(t, telephoneDisponible = false))
        assertEquals(MesureCentre.Autorisation.CONFIRMER_TELEPHONE, MesureCentre.autorisation(t, telephoneDisponible = true))
        // Even when the phone claims RTK fixed.
        assertEquals(
            MesureCentre.Autorisation.CONFIRMER_TELEPHONE,
            MesureCentre.autorisation(tel(47.0, qualite = QualiteFix.RTK_FIXE, precision = 0.03), true),
        )
        // Other cases unchanged by the flag.
        assertEquals(MesureCentre.Autorisation.SANS_POSITION, MesureCentre.autorisation(null, true))
        assertEquals(MesureCentre.Autorisation.SANS_POSITION, MesureCentre.autorisation(tel(47.0, qualite = QualiteFix.AUCUN), true))
        assertEquals(MesureCentre.Autorisation.OUI, MesureCentre.autorisation(rec(47.0), true))
        assertEquals(MesureCentre.Autorisation.CONFIRMER_FLOTTANT, MesureCentre.autorisation(rec(47.0, qualite = QualiteFix.RTK_FLOTTANT, precision = 0.4), true))
        assertEquals(MesureCentre.Autorisation.SANS_RTK, MesureCentre.autorisation(rec(47.0, qualite = QualiteFix.DGPS, precision = 0.8), true))
    }

    // --- priority ---

    @Test
    fun remplacement_a_confirmer() {
        assertTrue(MesureCentre.remplacementAConfirmer("fix", "telephone"))
        assertTrue(MesureCentre.remplacementAConfirmer("float", "telephone"))
        assertFalse(MesureCentre.remplacementAConfirmer("telephone", "telephone"))
        assertFalse(MesureCentre.remplacementAConfirmer(null, "telephone"))
        assertFalse(MesureCentre.remplacementAConfirmer("telephone", "fix"))
        assertFalse(MesureCentre.remplacementAConfirmer("telephone", "float"))
        assertFalse(MesureCentre.remplacementAConfirmer("fix", "float"))
        assertFalse(MesureCentre.remplacementAConfirmer("float", "fix"))
        assertFalse(MesureCentre.remplacementAConfirmer(null, "fix"))
    }

    // --- GeoPackage ---

    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    @Test
    fun ecriture_du_centre_telephone_et_regle_du_plus_recent() = exemple { db ->
        val p = GeoPackage.lirePoints(db, "placettes", "P002").single()
        val x = (p["x"] as Double) + 6.0
        val y = (p["y"] as Double) + 8.0
        val m = MesureCentre(false, accepterTelephone = true)
        remplir(m, 20)
        val c = m.resultat()!!
        val fidTel = GeoPackage.ajouterCentreMesure(db, "P002", x, y, c.qualite, c.precisionM, 10.0, "2026-11-11T10:00:00.000Z")
        val dernier = GeoPackage.dernierCentreParPlacette(db, "P002").single()
        assertEquals(fidTel, (dernier["fid"] as Number).toLong())
        assertEquals("telephone", dernier["qualite_gnss"])
        assertEquals(3.0, (dernier["precision_h_m"] as Number).toDouble(), 1e-9)
        assertEquals(10.0, (dernier["ecart_prevu_m"] as Number).toDouble(), 1e-9)
        // An RTK centre measured afterwards replaces it (latest counts); the phone row is kept.
        val fidRtk = GeoPackage.ajouterCentreMesure(db, "P002", x + 1.0, y, "fix", 0.02, 9.0, "2026-11-11T10:05:00.000Z")
        assertEquals(fidRtk, (GeoPackage.dernierCentreParPlacette(db, "P002").single()["fid"] as Number).toLong())
        assertEquals("fix", GeoPackage.dernierCentreParPlacette(db, "P002").single()["qualite_gnss"])
        assertEquals(3, GeoPackage.lirePoints(db, "centres_mesures", "P002").size)
    }
}
