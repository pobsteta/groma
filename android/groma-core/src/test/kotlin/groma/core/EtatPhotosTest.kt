package groma.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mission 022 (reprise 1/3): state of the photo list of the form, survival to a re-creation, photos of a deleted tree. */
class EtatPhotosTest {
    private val Q = "DCIM/IMG_0001.jpg"
    private val G1 = "DCIM/P001_14_20261111-090507.jpg"
    private val G2 = "DCIM/P001_14_20261111-090611.jpg"

    @Test
    fun priseApresRecreation() {
        // Photo taken, activity re-created (state saved as texts), then the camera result arrives.
        val avant = EtatPhotos(listOf(Q)).ajoutee(G1)
        val restaure = EtatPhotos.depuis(avant.enTextes())
        assertEquals(avant, restaure)
        val apres = restaure.ajoutee(G2)
        assertEquals(listOf(Q, G1, G2), apres.courantes)
        assertEquals(listOf(G1, G2), apres.nouvelles)
        assertEquals(listOf(Q), apres.originales)
    }

    @Test
    fun etatVideSauvegarde() {
        val e = EtatPhotos(emptyList())
        assertEquals(e, EtatPhotos.depuis(e.enTextes()))
    }

    @Test
    fun retraitDUnePhotoDeQfieldOuDeGromaDejaEnregistree() {
        val e = EtatPhotos(listOf(Q, G1))
        val (s, aSupprimerMaintenant) = e.retiree(G1)
        assertEquals(listOf(Q), s.courantes)
        assertEquals(emptyList(), aSupprimerMaintenant, "fichier déjà enregistré : supprimé seulement après « Enregistrer »")
        assertEquals(listOf(G1), s.retireesALEnregistrement)
        assertTrue(s.modifiee)
        assertEquals(listOf(G1), EtatPhotos(listOf(Q, G1)).retiree(Q).first.courantes)
    }

    @Test
    fun retraitDUnePhotoPriseDansLaFiche() {
        val (s, maintenant) = EtatPhotos(listOf(Q)).ajoutee(G1).retiree(G1)
        assertEquals(listOf(G1), maintenant)
        assertEquals(listOf(Q), s.courantes)
        assertEquals(emptyList(), s.abandonnees)
        assertFalse(s.modifiee)
    }

    @Test
    fun abandonSeulementDesPhotosPrises() {
        val e = EtatPhotos(listOf(Q)).ajoutee(G1).ajoutee(G2)
        assertEquals(listOf(G1, G2), e.abandonnees, "jamais la photo d'origine")
        assertEquals(emptyList(), e.enregistree().abandonnees)
        assertEquals(listOf(Q, G1, G2), e.enregistree().originales)
    }

    // --- arbre ajouté puis supprimé -----------------------------------------------------------

    @Test
    fun supprimerUnArbreAjouteSupprimeSesPhotosDeGromaSeulement() {
        val tmp = Files.createTempFile("inventaire_video", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), tmp, StandardCopyOption.REPLACE_EXISTING)
        val dossier = Files.createTempDirectory("projet").toFile().also { File(it, "DCIM").mkdirs() }
        File(dossier, G1).writeText("g"); File(dossier, Q).writeText("q"); File(dossier, G2).writeText("g2")
        DriverManager.getConnection("jdbc:sqlite:$tmp").use { c ->
            val db = SqliteJdbc(c)
            val u = "00000000-0000-4000-8000-0000000000aa"
            val modele = db.requete("SELECT * FROM arbres WHERE uuid = ?", listOf("00000000-0000-4000-8000-000000000014")).single()
            val cols = modele.keys.filter { it != "fid" }
            db.executer(
                "INSERT INTO arbres (${cols.joinToString(", ")}) VALUES (${cols.joinToString(", ") { "?" }})",
                cols.map { when (it) { "uuid" -> u; "methode" -> "terrain"; "photos" -> "$Q;$G1;$G2"; else -> modele[it] } },
            )
            // G2 is also cited by another tree: it must stay.
            db.executer("UPDATE arbres SET photos = ? WHERE uuid = ?", listOf(G2, "00000000-0000-4000-8000-000000000014"))
            GeoPackage.supprimerArbreAjoute(db, u, dossier)
            assertTrue(db.requete("SELECT 1 AS un FROM arbres WHERE uuid = ?", listOf(u)).isEmpty())
        }
        assertFalse(File(dossier, G1).exists(), "photo de Groma de l'arbre supprimé")
        assertTrue(File(dossier, Q).exists(), "photo de QField : jamais supprimée")
        assertTrue(File(dossier, G2).exists(), "encore citée par un autre arbre")
    }
}
