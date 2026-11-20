package groma.core

import fr.nemeton.terrain.gnss.FixGnss
import fr.nemeton.terrain.gnss.OrigineFix
import fr.nemeton.terrain.gnss.QualiteFix
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Mission 035 (voie A): terrain's names live in the code, the GeoPackage keeps "fix", "float",
 * "telephone" (nemeton reads them). Hand-written expected values; both directions.
 */
class QualiteGnssSchemaTest {
    private fun fix(qualite: QualiteFix, origine: OrigineFix, precision: Double) =
        FixGnss(Position(47.0, 5.0), qualite, 20, 0.6, 300.0, 1.0, precision, origine = origine)

    private fun ecrit(mesure: MesureCentre): String {
        val c = mesure.resultat()!!
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        return DriverManager.getConnection("jdbc:sqlite:$tmp").use {
            val db = SqliteJdbc(it)
            val fid = GeoPackage.ajouterCentreMesure(db, "P002", 925000.0, 6700000.0, c.qualite, 0.02, 1.0, "2026-11-20T10:00:00.000Z", c)
            GeoPackage.lirePoints(db, "centres_mesures", "P002").single { r -> (r["fid"] as Number).toLong() == fid }["qualite_gnss"] as String
        }
    }

    @Test
    fun vers_le_schema_garde_les_trois_valeurs_historiques() {
        assertEquals("fix", QualiteGnssSchema.versSchema(OrigineFix.EXTERNE, listOf(QualiteFix.RTK_FIXE, QualiteFix.RTK_FIXE)))
        assertEquals("float", QualiteGnssSchema.versSchema(OrigineFix.EXTERNE, listOf(QualiteFix.RTK_FIXE, QualiteFix.RTK_FLOAT)))
        assertEquals("float", QualiteGnssSchema.versSchema(OrigineFix.EXTERNE, listOf(QualiteFix.RTK_FLOAT)))
        assertEquals("telephone", QualiteGnssSchema.versSchema(OrigineFix.INTERNE, listOf(QualiteFix.AUTONOME)))
    }

    @Test
    fun depuis_le_schema_dans_l_autre_sens() {
        assertEquals(QualiteFix.RTK_FIXE, QualiteGnssSchema.qualiteDepuisSchema("fix"))
        assertEquals(QualiteFix.RTK_FLOAT, QualiteGnssSchema.qualiteDepuisSchema("float"))
        assertNull(QualiteGnssSchema.qualiteDepuisSchema("telephone"))
        assertNull(QualiteGnssSchema.qualiteDepuisSchema("recepteur"))
        assertEquals(OrigineFix.EXTERNE, QualiteGnssSchema.origineDepuisSchema("fix"))
        assertEquals(OrigineFix.EXTERNE, QualiteGnssSchema.origineDepuisSchema("float"))
        assertEquals(OrigineFix.INTERNE, QualiteGnssSchema.origineDepuisSchema("telephone"))
        assertNull(QualiteGnssSchema.origineDepuisSchema("interne"))
        assertNull(QualiteGnssSchema.origineDepuisSchema(null))
    }

    @Test
    fun le_geopackage_ecrit_contient_les_memes_valeurs_qu_avant() {
        val rtk = MesureCentre(accepterFlottant = true).apply { ajouter(fix(QualiteFix.RTK_FIXE, OrigineFix.EXTERNE, 0.02)) }
        assertEquals("fix", ecrit(rtk))
        val flottant = MesureCentre(accepterFlottant = true).apply { ajouter(fix(QualiteFix.RTK_FLOAT, OrigineFix.EXTERNE, 0.4)) }
        assertEquals("float", ecrit(flottant))
        val tel = MesureCentre(accepterFlottant = false, accepterTelephone = true).apply {
            repeat(MesureCentre.MIN_FIXES_TELEPHONE) { ajouter(fix(QualiteFix.AUTONOME, OrigineFix.INTERNE, 4.0)) }
        }
        assertEquals("telephone", ecrit(tel))
    }

    @Test
    fun fichier_de_l_ancien_format_relu_a_l_identique() {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use {
            val db = SqliteJdbc(it)
            val valeurs = GeoPackage.lirePoints(db, "centres_mesures").map { r -> r["qualite_gnss"] }
            assert(valeurs.isNotEmpty() && valeurs.all { v -> v in setOf("fix", "float", "telephone") }) { "valeurs relues : $valeurs" }
            val lus = DonneesCarte.lire(db).centres.map { c -> c.qualite }
            assertEquals(valeurs.toSet(), lus.toSet())
        }
    }

    @Test
    fun projet_ancien_de_l_essai_de_fumee_relu_a_l_identique() {
        val src = Fixtures.racine.resolve("android/app/src/androidTest/assets/projet_ancien.gpkg").toPath()
        val tmp = Files.createTempFile("projet_ancien", ".gpkg")
        Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use {
            val db = SqliteJdbc(it)
            assertEquals(listOf("fix", "fix"), GeoPackage.lirePoints(db, "centres_mesures").map { r -> r["qualite_gnss"] })
            assertEquals(listOf("fix", "fix"), DonneesCarte.lire(db).centres.map { c -> c.qualite })
            assertEquals(QualiteFix.RTK_FIXE, QualiteGnssSchema.qualiteDepuisSchema("fix"))
        }
    }
}
