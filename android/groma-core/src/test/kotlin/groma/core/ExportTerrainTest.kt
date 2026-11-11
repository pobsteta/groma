package groma.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.sql.DriverManager
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mission 020, points 1, 2 and 4: the "terrain" archive of ONE plot, extracted from Groma's own copy of
 * `inventaire_video.gpkg`, and its fingerprint. Written before the code (missions 021-022); the
 * expected values are written by hand from `exemples/inventaire_video/inventaire_video.gpkg`
 * (P001: 18 trees "1".."18", 1 measured centre at (925000, 6700000), 3 targets, 18 detections;
 * P002: no tree, 1 centre, 3 targets; one `projet` row).
 *
 * API EXPECTED FROM MISSION 021 (`groma-core`, new file ExportTerrain.kt; names and signatures fixed here):
 *
 * ```
 * data class ResultatArchive(val archive: File, val photosManquantes: List<String>, val empreinte: String)
 *
 * /** [ouvrir] opens a GeoPackage file as the phone does (SQLite JDBC in the tests). */
 * class ExportTerrain(private val ouvrir: (File) -> BaseSqlite) {
 *     /**
 *      * Writes into [sortie] a GeoPackage of the SAME schema (same `inventaire_video.schema.json` version,
 *      * same columns, GDAL-valid) with only plot [plotId]: its `placettes` row, its `centres_mesures`,
 *      * `cibles`, `arbres`, `detections` rows, and the `projet` table (all rows). Values identical to
 *      * the source (rule 5), paths of `photos` untouched. [copie] is never modified.
 *      * Throws IllegalArgumentException when [plotId] is not in `placettes`.
 *      */
 *     fun extraire(copie: File, plotId: String, sortie: File)
 *
 *     /**
 *      * SHA-256 (64 lowercase hex characters) of the CONTENT that would be sent: the extracted rows of the
 *      * tables above (every column except `fid`, ordered by a stable key) and the bytes of the photos found.
 *      * File dates, row order in the file and the other plots do not enter.
 *      * Throws IllegalArgumentException when [plotId] is unknown.
 *      */
 *     fun empreinte(copie: File, dossierProjet: File, plotId: String): String
 *
 *     /**
 *      * Writes the zip [sortie] ("<plot_id>.terrain.zip"): entry `terrain.gpkg` (= extraire, but with the
 *      * paths of `arbres.photos` and `placettes.photos` rewritten relative to the archive) and one entry
 *      * `photos/<name>` per photo found. `arbres.photos` holds ONE path or SEVERAL separated by ";", each
 *      * relative to [dossierProjet] (QField convention, e.g. "DCIM/P001_1_20261111T101500.jpg").
 *      * A photo that does not exist or leaves [dossierProjet] (absolute path, "..") is not packed, is
 *      * listed in `photosManquantes` (path as stored in the source) and does NOT stop the archive; its
 *      * path is kept AS STORED IN THE SOURCE in `terrain.gpkg` (e.g. "DCIM/absente.jpg", "../x.jpg",
 *      * an absolute path: decision of Pascal 2026-11-11, same rule for all; nothing is lost, rule 5), while the
 *      * photos found are rewritten to "photos/<name>". [ResultatArchive.empreinte]
 *      * equals [empreinte] for the same arguments.
 *      */
 *     fun archiver(copie: File, dossierProjet: File, plotId: String, sortie: File): ResultatArchive
 * }
 * ```
 */
class ExportTerrainTest {
    private val U1 = "00000000-0000-4000-8000-000000000009" // tree "1" of P001
    private val U4 = "00000000-0000-4000-8000-000000000014" // tree "4" of P001

    private val ouvrir: (File) -> BaseSqlite = { SqliteJdbc(DriverManager.getConnection("jdbc:sqlite:$it")) }
    private val export = ExportTerrain(ouvrir)

    private fun copieExemple(): File {
        val tmp = Files.createTempFile("groma_copie", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        return tmp.toFile()
    }

    private fun projet(): File = Files.createTempDirectory("projet_groma").toFile()

    private fun photo(projet: File, rel: String, contenu: String): File =
        File(projet, rel).also { it.parentFile.mkdirs(); it.writeBytes(contenu.toByteArray()) }

    private fun <T> sur(f: File, bloc: (SqliteJdbc) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:$f").use { bloc(SqliteJdbc(it)) }

    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    private fun compte(db: SqliteJdbc, table: String, plot: String? = null): Long =
        (db.requete("SELECT COUNT(*) AS n FROM $table" + (plot?.let { " WHERE plot_id = '$it'" } ?: "")).single()["n"] as Number).toLong()

    private fun lignes(db: SqliteJdbc, table: String, plot: String, tri: String): List<Map<String, Any?>> =
        db.requete("SELECT * FROM $table WHERE plot_id = ? ORDER BY $tri", listOf(plot)).map { it - "fid" }

    private fun egal(attendu: Any?, obtenu: Any?, msg: String) {
        if (attendu is ByteArray && obtenu is ByteArray) assertContentEquals(attendu, obtenu, msg) else assertEquals(attendu, obtenu, msg)
    }

    private fun egalLignes(attendu: List<Map<String, Any?>>, obtenu: List<Map<String, Any?>>, msg: String) {
        assertEquals(attendu.size, obtenu.size, "$msg : nombre de lignes")
        attendu.zip(obtenu).forEachIndexed { i, (a, o) ->
            assertEquals(a.keys, o.keys, "$msg : colonnes ligne $i")
            for (k in a.keys) egal(a[k], o[k], "$msg : ligne $i, $k")
        }
    }

    private fun extraite(plot: String, source: File = copieExemple()): File {
        val sortie = Files.createTempFile("terrain", ".gpkg").toFile()
        export.extraire(source, plot, sortie)
        return sortie
    }

    private fun gpkgDe(z: ZipFile): File =
        File.createTempFile("dans_archive", ".gpkg").also { it.writeBytes(z.getInputStream(z.getEntry("terrain.gpkg")).readBytes()) }

    private fun noms(z: ZipFile) = z.entries().asSequence().map { it.name }.toList()

    // --- 1. extraction -------------------------------------------------------------------------

    @Test
    fun extraitSeulementLaPlacetteDemandee() {
        val t = extraite("P001")
        sur(t) { db ->
            assertEquals(1, compte(db, "placettes"))
            assertEquals("P001", db.requete("SELECT plot_id FROM placettes").single()["plot_id"])
            assertEquals(1, compte(db, "centres_mesures"))
            assertEquals(3, compte(db, "cibles"))
            assertEquals(18, compte(db, "arbres"))
            assertEquals(18, compte(db, "detections"))
            assertEquals(1, compte(db, "projet"))
            for (table in listOf("centres_mesures", "cibles", "arbres", "detections")) {
                assertEquals(0, compte(db, table) - compte(db, table, "P001"), "$table : aucune autre placette")
            }
        }
    }

    @Test
    fun placetteSansArbreDonneUneArchiveValide() {
        val t = extraite("P002")
        sur(t) { db ->
            assertEquals("P002", db.requete("SELECT plot_id FROM placettes").single()["plot_id"])
            assertEquals(1, compte(db, "centres_mesures"))
            assertEquals(3, compte(db, "cibles"))
            assertEquals(0, compte(db, "arbres"))
            assertEquals(0, compte(db, "detections"))
            assertEquals("ok", db.requete("PRAGMA integrity_check").single()["integrity_check"])
        }
    }

    @Test
    fun placetteInconnueRefusee() {
        val sortie = Files.createTempFile("terrain", ".gpkg").toFile()
        assertFailsWith<IllegalArgumentException> { export.extraire(copieExemple(), "P999", sortie) }
    }

    @Test
    fun valeursIdentiquesALaSourceRegle5() {
        val source = copieExemple()
        // Entries made in Groma and in QField on P001 must travel, every column.
        sur(source) { db ->
            GeoPackage.modifierArbre(db, U4, mapOf("essence_arbre" to "hetre", "dbh_cm" to "47,5", "statut" to "mort", "qualite" to "B"))
            db.executer("UPDATE arbres SET notes = ?, photos = ? WHERE uuid = ?", listOf("fourche à 3 m", "DCIM/a.jpg", U4))
            db.executer("UPDATE placettes SET observateur = ?, notes = ? WHERE plot_id = 'P001'", listOf("PO", "pente forte"))
        }
        val t = extraite("P001", source)
        sur(source) { src ->
            sur(t) { dst ->
                for ((table, tri) in listOf(
                    "placettes" to "plot_id", "centres_mesures" to "date_mesure", "cibles" to "id_cible",
                    "arbres" to "uuid", "detections" to "uuid",
                )) egalLignes(lignes(src, table, "P001", tri), lignes(dst, table, "P001", tri), table)
                egalLignes(src.requete("SELECT * FROM projet").map { it - "fid" }, dst.requete("SELECT * FROM projet").map { it - "fid" }, "projet")
                val a = dst.requete("SELECT * FROM arbres WHERE uuid = ?", listOf(U4)).single()
                assertEquals(
                    listOf("hetre", 47.5, "mesure", "mort", "B", "fourche à 3 m", "DCIM/a.jpg"),
                    listOf(a["essence_arbre"], (a["dbh_cm"] as Number).toDouble(), a["dbh_source"], a["statut"], a["qualite"], a["notes"], a["photos"]),
                )
            }
        }
    }

    @Test
    fun memeSchemaQueLaSource() {
        val source = copieExemple()
        val t = extraite("P001", source)
        fun colonnes(db: SqliteJdbc, table: String) = db.requete("PRAGMA table_info($table)").map { it["name"] to it["type"] }
        sur(source) { src ->
            sur(t) { dst ->
                for (table in listOf("placettes", "centres_mesures", "cibles", "arbres", "detections", "projet")) {
                    assertEquals(colonnes(src, table), colonnes(dst, table), table)
                }
            }
        }
    }

    @Test
    fun fichierValidePourGdal() {
        val t = extraite("P001")
        sur(t) { db ->
            assertEquals(1196444487, (db.requete("PRAGMA application_id").single()["application_id"] as Number).toInt(), "application_id = 'GPKG'")
            assertEquals("ok", db.requete("PRAGMA integrity_check").single()["integrity_check"])
            val contenus = db.requete("SELECT table_name, data_type, srs_id FROM gpkg_contents")
                .associate { it["table_name"].toString() to (it["data_type"].toString() to (it["srs_id"] as Number).toInt()) }
            for (couche in listOf("placettes", "centres_mesures", "cibles", "arbres", "detections")) {
                assertEquals("features" to 2154, contenus[couche], couche)
                val g = db.requete("SELECT geometry_type_name, srs_id FROM gpkg_geometry_columns WHERE table_name = ?", listOf(couche)).single()
                assertEquals("POINT", g["geometry_type_name"], couche)
                assertEquals(2154, (g["srs_id"] as Number).toInt(), couche)
            }
            assertEquals("attributes", contenus["projet"]?.first)
            // No catalogue entry for a table that is not there.
            for (nom in contenus.keys) {
                assertEquals(1, db.requete("SELECT 1 AS u FROM sqlite_master WHERE name = ?", listOf(nom)).size, "gpkg_contents cite $nom absente")
            }
            assertEquals(1, db.requete("SELECT 1 AS u FROM gpkg_spatial_ref_sys WHERE srs_id = 2154").size, "SRS 2154 déclaré")
            // gpkg_ogr_contents counts match what is really in the tables (GDAL trusts them).
            for (c in db.requete("SELECT table_name, feature_count FROM gpkg_ogr_contents")) {
                val table = c["table_name"].toString()
                assertEquals(compte(db, table), (c["feature_count"] as Number).toLong(), "feature_count de $table")
            }
            // R*Tree indexes (centres_mesures, cibles) hold exactly the rows kept.
            for (table in listOf("centres_mesures", "cibles")) {
                assertEquals(compte(db, table), compte(db, "rtree_${table}_geom"), "index R*Tree de $table")
            }
        }
    }

    @Test
    fun copieDeGromaIntacte() {
        val source = copieExemple()
        val avant = sha(source)
        extraite("P001", source)
        assertEquals(avant, sha(source), "la copie de Groma n'est pas modifiée")
    }

    // --- 2. archive et photos -------------------------------------------------------------------

    @Test
    fun archiveContientLeGeopackageEtLesPhotosAvecCheminsRelatifsALArchive() {
        val source = copieExemple(); val dossier = projet()
        photo(dossier, "DCIM/P001_1_20261111T101500.jpg", "JPEG-un")
        photo(dossier, "DCIM/P001_4_20261111T102000.jpg", "JPEG-deux")
        photo(dossier, "DCIM/P001_4_20261111T102100.jpg", "JPEG-trois")
        photo(dossier, "DCIM/autre_placette.jpg", "JPEG-inutile")
        sur(source) { db ->
            db.executer("UPDATE arbres SET photos = ? WHERE uuid = ?", listOf("DCIM/P001_1_20261111T101500.jpg", U1))
            db.executer("UPDATE arbres SET photos = ? WHERE uuid = ?", listOf("DCIM/P001_4_20261111T102000.jpg;DCIM/P001_4_20261111T102100.jpg", U4))
        }
        val zip = File.createTempFile("P001", ".terrain.zip")
        val r = export.archiver(source, dossier, "P001", zip)
        assertEquals(zip, r.archive)
        assertTrue(r.photosManquantes.isEmpty())
        ZipFile(zip).use { z ->
            assertEquals(
                setOf("terrain.gpkg", "photos/P001_1_20261111T101500.jpg", "photos/P001_4_20261111T102000.jpg", "photos/P001_4_20261111T102100.jpg"),
                noms(z).toSet(),
                "contenu exact de l'archive (la photo d'une autre placette n'y est pas)",
            )
            assertEquals("JPEG-deux", z.getInputStream(z.getEntry("photos/P001_4_20261111T102000.jpg")).readBytes().decodeToString())
            sur(gpkgDe(z)) { db ->
                assertEquals("photos/P001_1_20261111T101500.jpg", db.requete("SELECT photos FROM arbres WHERE uuid = ?", listOf(U1)).single()["photos"])
                assertEquals(
                    "photos/P001_4_20261111T102000.jpg;photos/P001_4_20261111T102100.jpg",
                    db.requete("SELECT photos FROM arbres WHERE uuid = ?", listOf(U4)).single()["photos"],
                )
                assertEquals(18, compte(db, "arbres"))
            }
        }
        sur(source) { db -> assertEquals("DCIM/P001_1_20261111T101500.jpg", db.requete("SELECT photos FROM arbres WHERE uuid = ?", listOf(U1)).single()["photos"]) }
    }

    @Test
    fun photoManquanteSignaleeSansBloquer() {
        val source = copieExemple(); val dossier = projet()
        photo(dossier, "DCIM/presente.jpg", "JPEG")
        sur(source) { db ->
            db.executer("UPDATE arbres SET photos = ? WHERE uuid = ?", listOf("DCIM/presente.jpg;DCIM/absente.jpg", U1))
        }
        val zip = File.createTempFile("P001", ".terrain.zip")
        val r = export.archiver(source, dossier, "P001", zip)
        assertEquals(listOf("DCIM/absente.jpg"), r.photosManquantes)
        ZipFile(zip).use { z ->
            assertNotNull(z.getEntry("photos/presente.jpg"))
            assertNull(z.getEntry("photos/absente.jpg"))
            sur(gpkgDe(z)) { db ->
                assertEquals("photos/presente.jpg;DCIM/absente.jpg", db.requete("SELECT photos FROM arbres WHERE uuid = ?", listOf(U1)).single()["photos"], "photo manquante : chemin d'origine conservé, la trouvée réécrite")
            }
        }
    }

    @Test
    fun cheminHorsDuProjetJamaisLuNiEmballe() {
        val source = copieExemple(); val dossier = projet()
        val secret = File(dossier.parentFile, "secret_${System.nanoTime()}.txt").also { it.writeText("SECRET") }
        sur(source) { db ->
            db.executer("UPDATE arbres SET photos = ? WHERE uuid = ?", listOf("../${secret.name};${secret.absolutePath}", U1))
        }
        val zip = File.createTempFile("P001", ".terrain.zip")
        val r = export.archiver(source, dossier, "P001", zip)
        assertEquals(listOf("../${secret.name}", secret.absolutePath), r.photosManquantes)
        ZipFile(zip).use { z -> assertEquals(listOf("terrain.gpkg"), noms(z)) }
    }

    @Test
    fun deuxPhotosDeMemeNomDansDeuxDossiersNeSEcrasentPas() {
        val source = copieExemple(); val dossier = projet()
        photo(dossier, "DCIM/a/IMG.jpg", "PREMIERE")
        photo(dossier, "DCIM/b/IMG.jpg", "SECONDE")
        sur(source) { db ->
            db.executer("UPDATE arbres SET photos = ? WHERE uuid = ?", listOf("DCIM/a/IMG.jpg", U1))
            db.executer("UPDATE arbres SET photos = ? WHERE uuid = ?", listOf("DCIM/b/IMG.jpg", U4))
        }
        val zip = File.createTempFile("P001", ".terrain.zip")
        export.archiver(source, dossier, "P001", zip)
        ZipFile(zip).use { z ->
            sur(gpkgDe(z)) { db ->
                for ((uuid, contenu) in listOf(U1 to "PREMIERE", U4 to "SECONDE")) {
                    val chemin = db.requete("SELECT photos FROM arbres WHERE uuid = ?", listOf(uuid)).single()["photos"] as String
                    assertTrue(chemin.startsWith("photos/"), chemin)
                    assertEquals(contenu, z.getInputStream(z.getEntry(chemin)).readBytes().decodeToString(), chemin)
                }
            }
        }
    }

    @Test
    fun photosDeLaPlacetteReecritesAussi() {
        val source = copieExemple(); val dossier = projet()
        photo(dossier, "DCIM/vue_P001.jpg", "JPEG")
        sur(source) { db -> db.executer("UPDATE placettes SET photos = ? WHERE plot_id = 'P001'", listOf("DCIM/vue_P001.jpg")) }
        val zip = File.createTempFile("P001", ".terrain.zip")
        export.archiver(source, dossier, "P001", zip)
        ZipFile(zip).use { z ->
            assertNotNull(z.getEntry("photos/vue_P001.jpg"))
            sur(gpkgDe(z)) { db -> assertEquals("photos/vue_P001.jpg", db.requete("SELECT photos FROM placettes").single()["photos"]) }
        }
    }

    @Test
    fun archiveSansPhotoNiNote() {
        val zip = File.createTempFile("P002", ".terrain.zip")
        val r = export.archiver(copieExemple(), projet(), "P002", zip)
        assertTrue(r.photosManquantes.isEmpty())
        ZipFile(zip).use { z -> assertEquals(listOf("terrain.gpkg"), noms(z)) }
    }

    // --- 4. empreinte --------------------------------------------------------------------------

    @Test
    fun empreinteStableSansModification() {
        val source = copieExemple(); val dossier = projet()
        val e = export.empreinte(source, dossier, "P001")
        assertTrue(Regex("[0-9a-f]{64}").matches(e), e)
        assertEquals(e, export.empreinte(source, dossier, "P001"))
        // A copy with other file dates and another file name: same content, same fingerprint.
        val autre = Files.createTempFile("autre_nom", ".gpkg").toFile()
        Files.copy(source.toPath(), autre.toPath(), StandardCopyOption.REPLACE_EXISTING)
        autre.setLastModified(source.lastModified() - 86_400_000)
        assertEquals(e, export.empreinte(autre, dossier, "P001"))
        assertEquals(e, export.archiver(source, dossier, "P001", File.createTempFile("P001", ".terrain.zip")).empreinte)
        assertNotEquals(e, export.empreinte(source, dossier, "P002"), "deux placettes, deux empreintes")
    }

    @Test
    fun empreinteChangeApresModifierArbre() {
        val source = copieExemple(); val dossier = projet()
        // U4 carries 'sapin_pectine' in the example: start from a tree without species so that erasing returns to the first state.
        sur(source) { it.executer("UPDATE arbres SET essence_arbre = NULL WHERE uuid = ?", listOf(U4)) }
        val avant = export.empreinte(source, dossier, "P001")
        sur(source) { GeoPackage.modifierArbre(it, U4, mapOf("essence_arbre" to "hetre")) }
        val apres = export.empreinte(source, dossier, "P001")
        assertNotEquals(avant, apres)
        // Same values written again: nothing new to send.
        sur(source) { GeoPackage.modifierArbre(it, U4, mapOf("essence_arbre" to "hetre")) }
        assertEquals(apres, export.empreinte(source, dossier, "P001"))
        // Erasing brings the content back to the first state.
        sur(source) { GeoPackage.modifierArbre(it, U4, mapOf("essence_arbre" to "")) }
        assertEquals(avant, export.empreinte(source, dossier, "P001"))
    }

    @Test
    fun empreinteChangeApresEcrireCible() {
        val source = copieExemple(); val dossier = projet()
        val avant = export.empreinte(source, dossier, "P001")
        sur(source) { GeoPackage.ecrireCible(it, "P001", CiblePlacee("C2", 130.0, 11.0, 41.0, SourceAzimut.BOUSSOLE_VISEE), 925010.0, 6700020.0, 144.4444) }
        assertNotEquals(avant, export.empreinte(source, dossier, "P001"))
    }

    @Test
    fun empreinteChangeApresAjouterCentreMesure() {
        val source = copieExemple(); val dossier = projet()
        val avant = export.empreinte(source, dossier, "P001")
        sur(source) { GeoPackage.ajouterCentreMesure(it, "P001", 925000.5, 6700000.5, "fix", 0.02, 0.7, "2026-11-11T10:00:00.000Z") }
        assertNotEquals(avant, export.empreinte(source, dossier, "P001"))
    }

    @Test
    fun empreinteChangeApresAjouterArbre() {
        val source = copieExemple(); val dossier = projet()
        val avant = export.empreinte(source, dossier, "P001")
        sur(source) { GeoPackage.ajouterArbre(it, "P001", 90.0, 10.0, 38.0) }
        assertNotEquals(avant, export.empreinte(source, dossier, "P001"))
    }

    @Test
    fun empreinteIndifferenteAuxAutresPlacettes() {
        val source = copieExemple(); val dossier = projet()
        val p1 = export.empreinte(source, dossier, "P001")
        val p2 = export.empreinte(source, dossier, "P002")
        sur(source) { GeoPackage.ajouterCentreMesure(it, "P002", 925100.0, 6700050.0, "fix", 0.02, 0.5, "2026-11-11T10:00:00.000Z") }
        assertEquals(p1, export.empreinte(source, dossier, "P001"))
        assertNotEquals(p2, export.empreinte(source, dossier, "P002"))
    }

    @Test
    fun empreinteSuitLesPhotos() {
        val source = copieExemple(); val dossier = projet()
        sur(source) { it.executer("UPDATE arbres SET photos = ? WHERE uuid = ?", listOf("DCIM/p.jpg", U1)) }
        val sansFichier = export.empreinte(source, dossier, "P001")
        photo(dossier, "DCIM/p.jpg", "version 1")
        val v1 = export.empreinte(source, dossier, "P001")
        assertNotEquals(sansFichier, v1, "la photo arrive")
        photo(dossier, "DCIM/p.jpg", "version 2")
        val v2 = export.empreinte(source, dossier, "P001")
        assertNotEquals(v1, v2, "la photo change sous le même nom")
        // An unrelated file in the project does not matter.
        photo(dossier, "DCIM/autre.jpg", "x")
        assertEquals(v2, export.empreinte(source, dossier, "P001"))
    }

    @Test
    fun empreintePlacetteInconnueRefusee() {
        assertFailsWith<IllegalArgumentException> { export.empreinte(copieExemple(), projet(), "P999") }
    }
}
