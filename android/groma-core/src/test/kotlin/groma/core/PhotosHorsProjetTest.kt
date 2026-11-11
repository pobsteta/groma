package groma.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals

/** Mission 021 (reprise 3/3): absolute and ".." photo paths are never read nor packed, and keep their original path. */
class PhotosHorsProjetTest {
    private val export = ExportTerrain { SqliteJdbc(DriverManager.getConnection("jdbc:sqlite:$it")) }
    private val u1 = "00000000-0000-4000-8000-000000000009"

    @Test
    fun cheminAbsoluEtParentNonLusNonEmballesCheminDOrigineGarde() {
        val src = Files.createTempFile("copie", ".gpkg")
        Files.copy(Fixtures.racine.resolve("exemples/inventaire_video/inventaire_video.gpkg").toPath(), src, StandardCopyOption.REPLACE_EXISTING)
        val dossier = Files.createTempDirectory("projet").toFile()
        File(dossier, "DCIM").mkdirs(); File(dossier, "DCIM/ok.jpg").writeText("OK")
        val secret = File(dossier.parentFile, "secret_${System.nanoTime()}.txt").also { it.writeText("SECRET") }
        val valeur = "DCIM/ok.jpg;../${secret.name};${secret.absolutePath}"
        DriverManager.getConnection("jdbc:sqlite:$src").use {
            SqliteJdbc(it).executer("UPDATE arbres SET photos = ? WHERE uuid = ?", listOf(valeur, u1))
        }
        val zip = File.createTempFile("P001", ".terrain.zip")
        val r = export.archiver(src.toFile(), dossier, "P001", zip)
        assertEquals(listOf("../${secret.name}", secret.absolutePath), r.photosManquantes)
        ZipFile(zip).use { z ->
            assertEquals(setOf("terrain.gpkg", "photos/ok.jpg"), z.entries().asSequence().map { it.name }.toSet())
            val g = File.createTempFile("dans", ".gpkg").also { it.writeBytes(z.getInputStream(z.getEntry("terrain.gpkg")).readBytes()) }
            DriverManager.getConnection("jdbc:sqlite:$g").use {
                val p = SqliteJdbc(it).requete("SELECT photos FROM arbres WHERE uuid = ?", listOf(u1)).single()["photos"]
                assertEquals("photos/ok.jpg;../${secret.name};${secret.absolutePath}", p)
            }
        }
    }
}
