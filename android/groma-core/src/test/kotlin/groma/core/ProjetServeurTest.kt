package groma.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjetServeurTest {
    private val sha = "a".repeat(64)

    private fun manifeste(version: String = "20261017T080000Z", fichiers: List<FichierServeur> = listOf(
        FichierServeur("inventaire_video.qgz", sha, 1000), FichierServeur("inventaire_video.qml", sha, 10),
    )) = ManifesteProjet("20261015_194200_yzoh", "Forêt communale de Marsannay-la-côte (ONF)", version, null, fichiers)

    @Test
    fun `etat du projet sur le telephone`() {
        val m = manifeste()
        assertEquals(EtatProjetServeur.ABSENT, ProjetsServeur.etat(m, present = false, versionInstallee = null))
        // Dropped by cable: no known version, the server's may be newer.
        assertEquals(EtatProjetServeur.PLUS_RECENT, ProjetsServeur.etat(m, present = true, versionInstallee = null))
        assertEquals(EtatProjetServeur.PLUS_RECENT, ProjetsServeur.etat(m, true, "20261016T235900Z"))
        assertEquals(EtatProjetServeur.A_JOUR, ProjetsServeur.etat(m, true, "20261017T080000Z"))
        assertEquals(EtatProjetServeur.PLUS_ANCIEN, ProjetsServeur.etat(m, true, "20261018T000000Z"))
        assertEquals(EtatProjetServeur.PLUS_RECENT, ProjetsServeur.etat(m, true, "n'importe quoi"))
    }

    @Test
    fun `manifeste valide`() {
        assertTrue(ProjetsServeur.valide(manifeste()))
        assertEquals(1010L, manifeste().tailleTotale)
        assertFalse(ProjetsServeur.valide(manifeste(version = "2026-10-17")))
        assertFalse(ProjetsServeur.valide(manifeste(fichiers = listOf(FichierServeur("inventaire_video.qml", sha, 10)))))
        assertFalse(ProjetsServeur.valide(manifeste(fichiers = listOf(FichierServeur("../x.qgz", sha, 10)))))
        assertFalse(ProjetsServeur.valide(manifeste().copy(projetId = "..")))
    }

    private fun zip(vararg entrees: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((nom, donnees) in entrees) {
                z.putNextEntry(ZipEntry(nom)); z.write(donnees); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun extraire(qgz: ByteArray): Pair<ContenuQgz, Map<String, ByteArray>> {
        val ecrits = linkedMapOf<String, ByteArray>()
        val c = ExtractionQgz.extraire(ByteArrayInputStream(qgz)) { chemin, s -> ecrits[chemin] = s.readBytes() }
        return c to ecrits
    }

    @Test
    fun `extraction du qgz de nemeton`() {
        val (c, ecrits) = extraire(zip(
            "inventaire_video.qgs" to "<qgis/>".toByteArray(),
            "inventaire_video.gpkg" to byteArrayOf(1, 2, 3),
            "rasters/P001_mnt.tif" to byteArrayOf(4),
            "rasters/P001_mnh.tif" to byteArrayOf(5),
            "notes.txt" to byteArrayOf(6),
        ))
        assertEquals(listOf("inventaire_video.gpkg", "rasters/P001_mnt.tif", "rasters/P001_mnh.tif"), ecrits.keys.toList())
        assertEquals(listOf(1, 2, 3), ecrits["inventaire_video.gpkg"]!!.map { it.toInt() })
        assertEquals(listOf("P001_mnt.tif", "P001_mnh.tif"), c.rasters)
    }

    @Test
    fun `chemins piégés jamais repris`() {
        val (_, ecrits) = extraire(zip(
            "../../evil/inventaire_video.gpkg" to byteArrayOf(1),
            "rasters/../../x.tif" to byteArrayOf(2),
            "/abs/rasters/ok.tif" to byteArrayOf(3),
            "rasters/pas un raster.sh" to byteArrayOf(4),
        ))
        // Output names are rebuilt: the GeoPackage lands at the project root,
        // only a well-formed raster under rasters/.
        assertEquals(setOf("inventaire_video.gpkg", "rasters/ok.tif"), ecrits.keys)
    }

    @Test
    fun `sans geopackage ou en double`() {
        assertThrows<IllegalArgumentException> { extraire(zip("a.qgs" to byteArrayOf(1))) }
        assertThrows<IllegalArgumentException> {
            extraire(zip("inventaire_video.gpkg" to byteArrayOf(1), "x/inventaire_video.gpkg" to byteArrayOf(2)))
        }
    }

    @Test
    fun `empreinte sha256`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Empreinte.sha256("abc".byteInputStream()))
    }
}
