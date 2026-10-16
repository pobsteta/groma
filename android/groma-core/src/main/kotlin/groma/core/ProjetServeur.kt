package groma.core

import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * A QField project published on the Groma server by nemetonshiny (brief
 * envoi projet, `GET /projets`): the `.qgz` of creer_projet_inventaire_video()
 * (GeoPackage and rasters inside) and its plugin `.qml`.
 */
data class FichierServeur(val nom: String, val sha256: String, val taille: Long)

data class ManifesteProjet(
    val projetId: String,
    val projetNom: String,
    /** UTC timestamp `AAAAMMJJTHHMMSSZ`: sorts like the publication order. */
    val version: String,
    val publieLe: String?,
    val fichiers: List<FichierServeur>,
) {
    val qgz: FichierServeur? get() = fichiers.singleOrNull { it.nom.endsWith(".qgz") }
    val plugin: FichierServeur? get() = fichiers.singleOrNull { it.nom.endsWith(".qml") }
    val tailleTotale: Long get() = fichiers.sumOf { it.taille }
}

/** Where a published project stands on the phone. */
enum class EtatProjetServeur { ABSENT, A_JOUR, PLUS_RECENT, PLUS_ANCIEN }

object ProjetsServeur {
    private val VERSION = Regex("^\\d{8}T\\d{6}Z$")

    /**
     * [versionInstallee]: version downloaded earlier (null if the project was
     * dropped by cable or never downloaded); [present]: the project folder
     * exists on the phone.
     */
    fun etat(m: ManifesteProjet, present: Boolean, versionInstallee: String?): EtatProjetServeur = when {
        !present -> EtatProjetServeur.ABSENT
        versionInstallee == null || !VERSION.matches(versionInstallee) -> EtatProjetServeur.PLUS_RECENT
        m.version > versionInstallee -> EtatProjetServeur.PLUS_RECENT
        m.version < versionInstallee -> EtatProjetServeur.PLUS_ANCIEN
        else -> EtatProjetServeur.A_JOUR
    }

    /** Only well-formed manifests, for a project id usable as a folder name. */
    fun valide(m: ManifesteProjet): Boolean =
        CleProjet.valide(m.projetId) && VERSION.matches(m.version) && m.qgz != null &&
            m.fichiers.all { NOM_FICHIER.matches(it.nom) && it.taille > 0 && SHA256.matches(it.sha256) }

    private val NOM_FICHIER = Regex("^[A-Za-z0-9_-]{1,64}\\.(qgz|qml)$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")
}

/** Result of [ExtractionQgz.extraire]. */
data class ContenuQgz(val rasters: List<String>)

/**
 * Extracts from a `.qgz` only what Groma reads: `inventaire_video.gpkg` and
 * `rasters/<nom>.tif`. Output paths are rebuilt from the base name, never
 * taken from the archive (no zip-slip): `inventaire_video.gpkg`,
 * `rasters/<nom>.tif`. The `.qgs` and anything else stay in the `.qgz`,
 * which goes to QField as is.
 */
object ExtractionQgz {
    const val GPKG = "inventaire_video.gpkg"
    private val RASTER = Regex("^[A-Za-z0-9_.-]{1,96}\\.tif$")

    /**
     * Streams [qgz] and calls [ecrire] with the relative output path and the
     * entry's bytes (do not close the stream). Throws when there is no
     * GeoPackage or it appears twice.
     */
    fun extraire(qgz: InputStream, ecrire: (chemin: String, contenu: InputStream) -> Unit): ContenuQgz {
        var gpkg = false
        val rasters = mutableListOf<String>()
        ZipInputStream(qgz).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val morceaux = e.name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
                val base = morceaux.lastOrNull() ?: continue
                val parent = morceaux.dropLast(1).lastOrNull()
                when {
                    base == GPKG -> {
                        require(!gpkg) { "Le .qgz contient plusieurs $GPKG." }
                        gpkg = true
                        ecrire(GPKG, NonFermable(z))
                    }
                    parent == "rasters" && RASTER.matches(base) && base !in rasters -> {
                        rasters += base
                        ecrire("rasters/$base", NonFermable(z))
                    }
                }
                z.closeEntry()
            }
        }
        require(gpkg) { "Le .qgz ne contient pas $GPKG." }
        return ContenuQgz(rasters)
    }

    private class NonFermable(s: InputStream) : FilterInputStream(s) {
        override fun close() {}
    }
}

object Empreinte {
    fun sha256(s: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val tampon = ByteArray(1 shl 16)
        while (true) {
            val n = s.read(tampon)
            if (n < 0) break
            md.update(tampon, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
