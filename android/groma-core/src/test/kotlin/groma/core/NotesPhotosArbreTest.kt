package groma.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mission 022: note and photos of a tree. Convention of `arbres.photos` (QField's photo widget:
 * path relative to the project folder; several paths separated by `;`, fixed by the tests of 020).
 * Rule 5: what is already there (QField or Groma) is never lost without an explicit action.
 */
class NotesPhotosArbreTest {
    private val U = "00000000-0000-4000-8000-000000000014"
    private val AUTRE = "00000000-0000-4000-8000-000000000021"

    private fun exemple(bloc: (SqliteJdbc) -> Unit) {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { bloc(SqliteJdbc(it)) }
    }

    private fun arbre(db: SqliteJdbc, uuid: String) = db.requete("SELECT * FROM arbres WHERE uuid = ?", listOf(uuid)).single()

    // --- contrôle ----------------------------------------------------------------------------

    @Test
    fun noteBornee() {
        assertNull(SaisieArbre.controler("notes", null))
        assertNull(SaisieArbre.controler("notes", ""))
        assertNull(SaisieArbre.controler("notes", "fourche à 3 m\nbois mort au pied"))
        assertNull(SaisieArbre.controler("notes", "x".repeat(PhotosArbre.NOTE_MAX)))
        assertNotNull(SaisieArbre.controler("notes", "x".repeat(PhotosArbre.NOTE_MAX + 1)))
    }

    @Test
    fun cheminsDePhotosRelatifsSeulement() {
        for (v in listOf(null, "", "DCIM/a.jpg", "DCIM/a.jpg;DCIM/b.jpg", "photos/c.jpg; DCIM/d.jpg ")) assertNull(SaisieArbre.controler("photos", v), "$v")
        for (v in listOf("/sdcard/a.jpg", "../a.jpg", "DCIM/../../a.jpg", "C:\\a.jpg", "DCIM/a.jpg;/etc/passwd")) {
            assertNotNull(SaisieArbre.controler("photos", v), "$v devrait être refusé")
        }
    }

    @Test
    fun lesChampsDeTerrain() {
        assertEquals(listOf("notes", "photos"), SaisieArbre.CHAMPS_TERRAIN)
        assertEquals(listOf("essence_arbre", "dbh_cm", "statut", "qualite"), SaisieArbre.CHAMPS)
    }

    // --- listes de chemins -------------------------------------------------------------------

    @Test
    fun listerEtJoindre() {
        assertEquals(emptyList(), PhotosArbre.lister(null))
        assertEquals(emptyList(), PhotosArbre.lister(" ; ;"))
        assertEquals(listOf("DCIM/a.jpg", "DCIM/b.jpg"), PhotosArbre.lister("DCIM/a.jpg; DCIM/b.jpg;;DCIM/a.jpg"))
        assertNull(PhotosArbre.joindre(emptyList()))
        assertEquals("DCIM/a.jpg", PhotosArbre.joindre(listOf("DCIM/a.jpg")))
        assertEquals("DCIM/a.jpg;DCIM/b.jpg", PhotosArbre.joindre(listOf("DCIM/a.jpg", "DCIM/b.jpg")))
    }

    @Test
    fun ajouterEtRetirerGardentLesAutres() {
        assertEquals("DCIM/q.jpg;DCIM/g.jpg", PhotosArbre.ajouter("DCIM/q.jpg", "DCIM/g.jpg"))
        assertEquals("DCIM/g.jpg", PhotosArbre.ajouter(null, "DCIM/g.jpg"))
        assertEquals("DCIM/q.jpg", PhotosArbre.retirer("DCIM/q.jpg;DCIM/g.jpg", "DCIM/g.jpg"))
        assertNull(PhotosArbre.retirer("DCIM/g.jpg", "DCIM/g.jpg"))
    }

    @Test
    fun nomDeFichier() {
        val t = LocalDateTime.of(2026, 11, 11, 9, 5, 7)
        assertEquals("DCIM/P001_14_20261111-090507.jpg", PhotosArbre.chemin("P001", "14", t))
        assertEquals("DCIM/P001_A1_20261111-090507.jpg", PhotosArbre.chemin("P001", "A1", t))
        // Anything that could leave the folder or break the `;` list is cleaned.
        val sale = PhotosArbre.chemin("P 0/1", "../x;y", t)
        assertTrue(PhotosArbre.estDeGroma(sale), sale)
        assertNull(SaisieArbre.controler("photos", sale))
        assertFalse(';' in sale || ".." in sale)
    }

    @Test
    fun seulsLesFichiersDeGromaSontReconnus() {
        assertTrue(PhotosArbre.estDeGroma("DCIM/P001_14_20261111-090507.jpg"))
        assertFalse(PhotosArbre.estDeGroma("DCIM/IMG_20261111_090507.jpg"), "photo de QField")
        assertFalse(PhotosArbre.estDeGroma("photos/P001_14_20261111-090507.jpg"), "hors DCIM")
        assertFalse(PhotosArbre.estDeGroma("DCIM/sous/P001_14_20261111-090507.jpg"))
        assertFalse(PhotosArbre.estDeGroma("DCIM/P001_14_20261111T101500.jpg"))
    }

    @Test
    fun reductionA2048() {
        assertEquals(2048 to 1536, PhotosArbre.tailleReduite(4000, 3000))
        assertEquals(1536 to 2048, PhotosArbre.tailleReduite(3000, 4000))
        assertEquals(2048 to 2048, PhotosArbre.tailleReduite(4000, 4000))
        assertEquals(1600 to 1200, PhotosArbre.tailleReduite(1600, 1200), "déjà assez petite : inchangée")
        assertEquals(2048 to 1, PhotosArbre.tailleReduite(100000, 40), "jamais 0 pixel")
        assertFailsWith<IllegalArgumentException> { PhotosArbre.tailleReduite(0, 10) }
    }

    // --- écriture ----------------------------------------------------------------------------

    @Test
    fun noteEtPhotosEcrites() = exemple { db ->
        val avant = arbre(db, U)
        val autreAvant = arbre(db, AUTRE)
        GeoPackage.modifierArbre(db, U, mapOf("notes" to "  fourche à 3 m\nlierre ", "photos" to "DCIM/a.jpg; DCIM/b.jpg"))
        val a = arbre(db, U)
        assertEquals("fourche à 3 m\nlierre", a["notes"])
        assertEquals("DCIM/a.jpg;DCIM/b.jpg", a["photos"])
        for (k in avant.keys - setOf("notes", "photos")) {
            val x = avant[k]; val y = a[k]
            if (x is ByteArray && y is ByteArray) assertTrue(x.contentEquals(y), k) else assertEquals(x, y, k)
        }
        assertEquals(autreAvant["notes"], arbre(db, AUTRE)["notes"])
        assertEquals(autreAvant["photos"], arbre(db, AUTRE)["photos"])
    }

    @Test
    fun saisieSansNotesNiPhotosLesLaisseIntactes() = exemple { db ->
        // Règle 5 : a note and a photo from QField survive an edit of the other fields.
        db.executer("UPDATE arbres SET notes = 'vu par QField', photos = 'DCIM/qfield.jpg' WHERE uuid = ?", listOf(U))
        GeoPackage.modifierArbre(db, U, mapOf("qualite" to "B", "essence_arbre" to "hetre"))
        val a = arbre(db, U)
        assertEquals("vu par QField", a["notes"])
        assertEquals("DCIM/qfield.jpg", a["photos"])
    }

    @Test
    fun ajouterUnePhotoGardeCelleDeQfield() = exemple { db ->
        db.executer("UPDATE arbres SET photos = 'DCIM/qfield.jpg' WHERE uuid = ?", listOf(U))
        val liste = ArbrePlacette.lire(db, "P001").first { it.uuid == U }.photos
        assertEquals(listOf("DCIM/qfield.jpg"), liste)
        GeoPackage.modifierArbre(db, U, mapOf("photos" to PhotosArbre.joindre(liste + "DCIM/P001_14_20261111-090507.jpg")))
        assertEquals("DCIM/qfield.jpg;DCIM/P001_14_20261111-090507.jpg", arbre(db, U)["photos"])
    }

    @Test
    fun effacerEstExplicite() = exemple { db ->
        db.executer("UPDATE arbres SET notes = 'x', photos = 'DCIM/a.jpg' WHERE uuid = ?", listOf(U))
        GeoPackage.modifierArbre(db, U, mapOf("notes" to "  ", "photos" to null))
        assertNull(arbre(db, U)["notes"])
        assertNull(arbre(db, U)["photos"])
    }

    @Test
    fun valeurRefuseeNeRienEcrire() = exemple { db ->
        db.executer("UPDATE arbres SET notes = 'garde' WHERE uuid = ?", listOf(U))
        val avant = arbre(db, U)
        assertFailsWith<IllegalArgumentException> { GeoPackage.modifierArbre(db, U, mapOf("qualite" to "A", "notes" to "x".repeat(PhotosArbre.NOTE_MAX + 1))) }
        assertFailsWith<IllegalArgumentException> { GeoPackage.modifierArbre(db, U, mapOf("qualite" to "A", "photos" to "../../etc/passwd")) }
        assertEquals(avant["notes"], arbre(db, U)["notes"])
        assertEquals(avant["qualite"], arbre(db, U)["qualite"])
        assertEquals(avant["photos"], arbre(db, U)["photos"])
    }

    @Test
    fun noteEtPhotosLuesParLaVuePlacette() = exemple { db ->
        db.executer("UPDATE arbres SET notes = 'fourche', photos = 'DCIM/a.jpg;DCIM/b.jpg' WHERE uuid = ?", listOf(U))
        val a = ArbrePlacette.lire(db, "P001").first { it.uuid == U }
        assertEquals("fourche", a.notes)
        assertEquals(listOf("DCIM/a.jpg", "DCIM/b.jpg"), a.photos)
        val b = ArbrePlacette.lire(db, "P001").first { it.uuid == AUTRE }
        assertNull(b.notes)
        assertEquals(emptyList(), b.photos)
    }

    // --- suppression d'un fichier ------------------------------------------------------------

    private fun projet(): File = Files.createTempDirectory("projet").toFile().also { File(it, "DCIM").mkdirs() }

    @Test
    fun supprimeSeulementUnFichierPrisParGroma() = exemple { db ->
        val dossier = projet()
        val groma = "DCIM/P001_14_20261111-090507.jpg"
        val qfield = "DCIM/IMG_0001.jpg"
        File(dossier, groma).writeText("g"); File(dossier, qfield).writeText("q")
        assertTrue(PhotosArbre.supprimerFichier(db, dossier, groma))
        assertFalse(File(dossier, groma).exists())
        assertFalse(PhotosArbre.supprimerFichier(db, dossier, qfield), "photo de QField : jamais supprimée")
        assertTrue(File(dossier, qfield).exists())
        assertFalse(PhotosArbre.supprimerFichier(db, dossier, groma), "déjà absente : rien")
    }

    @Test
    fun neSupprimePasUnFichierEncoreCite() = exemple { db ->
        val dossier = projet()
        val groma = "DCIM/P001_14_20261111-090507.jpg"
        File(dossier, groma).writeText("g")
        db.executer("UPDATE arbres SET photos = ? WHERE uuid = ?", listOf(groma, AUTRE))
        assertFalse(PhotosArbre.supprimerFichier(db, dossier, groma))
        assertTrue(File(dossier, groma).exists())
    }

    @Test
    fun neSortPasDuDossierDuProjet() = exemple { db ->
        val dossier = projet()
        val dehors = File(dossier.parentFile, "P001_14_20261111-090507.jpg").also { it.writeText("x") }
        assertFalse(PhotosArbre.supprimerFichier(db, dossier, "DCIM/../../${dehors.name}"))
        assertTrue(dehors.exists())
        dehors.delete()
    }

    private fun <T> sur(f: File, bloc: (SqliteJdbc) -> T): T = DriverManager.getConnection("jdbc:sqlite:$f").use { bloc(SqliteJdbc(it)) }

    // --- envoi de terrain (021) --------------------------------------------------------------

    @Test
    fun notesEtPhotosPartentAvecLEnvoi() {
        val copie = Files.createTempFile("groma_copie", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), copie, StandardCopyOption.REPLACE_EXISTING)
        val dossier = projet()
        val export = ExportTerrain { f -> SqliteJdbc(DriverManager.getConnection("jdbc:sqlite:$f")) }
        val groma = "DCIM/P001_4_20261111-090507.jpg"
        File(dossier, groma).writeText("JPEG-GROMA")
        File(dossier, "DCIM/qfield.jpg").writeText("JPEG-QFIELD")
        sur(copie.toFile()) { db ->
            db.executer("UPDATE arbres SET photos = 'DCIM/qfield.jpg' WHERE uuid = ?", listOf(U))
        }
        val avant = export.empreinte(copie.toFile(), dossier, "P001")
        sur(copie.toFile()) { db ->
            GeoPackage.modifierArbre(db, U, mapOf("notes" to "fourche", "photos" to "DCIM/qfield.jpg;$groma"))
        }
        assertTrue(export.empreinte(copie.toFile(), dossier, "P001") != avant, "l'empreinte change : un renvoi sera planifié")
        val zip = File.createTempFile("P001", ".terrain.zip")
        val r = export.archiver(copie.toFile(), dossier, "P001", zip)
        assertEquals(emptyList(), r.photosManquantes)
        java.util.zip.ZipFile(zip).use { z ->
            assertNotNull(z.getEntry("photos/qfield.jpg"))
            assertNotNull(z.getEntry("photos/P001_4_20261111-090507.jpg"))
            val g = File.createTempFile("dans_archive", ".gpkg").also { it.writeBytes(z.getInputStream(z.getEntry("terrain.gpkg")).readBytes()) }
            sur(g) { db ->
                val a = db.requete("SELECT notes, photos FROM arbres WHERE uuid = ?", listOf(U)).single()
                assertEquals("fourche", a["notes"])
                assertEquals("photos/qfield.jpg;photos/P001_4_20261111-090507.jpg", a["photos"])
            }
        }
    }
}
