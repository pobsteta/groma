package groma.core

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Result of [ExportTerrain.archiver]: the zip, the photo paths that could not be packed, the content fingerprint. */
data class ResultatArchive(val archive: File, val photosManquantes: List<String>, val empreinte: String)

/**
 * "Terrain" archive of ONE plot (mission 021): what was entered in Groma (and in QField) for the plot,
 * extracted from Groma's own copy of `inventaire_video.gpkg`, to be sent to the server.
 *
 * [ouvrir] opens a GeoPackage file read-write as the phone does. The source copy is only read (its bytes
 * are copied, never opened for writing here), so it is never modified.
 */
class ExportTerrain(private val ouvrir: (File) -> BaseSqlite) {

    companion object {
        /** Tables filtered by `plot_id`; `projet` is kept whole. */
        val TABLES_PLACETTE = listOf("placettes", "centres_mesures", "cibles", "arbres", "detections")

        /** Tables of the schema that are not part of the archive: emptied, schema kept. */
        val TABLES_VIDEES = listOf("houppiers", "parcelles", "ugf")
        const val GPKG = "terrain.gpkg"
        const val DOSSIER_PHOTOS = "photos"
    }

    private fun fermer(db: BaseSqlite) = (db as? AutoCloseable)?.close()

    private inline fun <T> avec(f: File, bloc: (BaseSqlite) -> T): T {
        val db = ouvrir(f)
        try {
            return bloc(db)
        } finally {
            fermer(db)
        }
    }

    private fun verifierPlacette(db: BaseSqlite, plotId: String) {
        require(db.requete("SELECT 1 AS u FROM placettes WHERE plot_id = ?", listOf(plotId)).isNotEmpty()) {
            "Placette inconnue : $plotId"
        }
    }

    fun extraire(copie: File, plotId: String, sortie: File) {
        avec(copie) { verifierPlacette(it, plotId) }
        copier(copie, sortie)
        avec(sortie) { db ->
            db.requete("PRAGMA journal_mode = DELETE") // folds a possible -wal copy into the file
            db.transactionImmediate {
                for (t in TABLES_PLACETTE) db.executer("DELETE FROM $t WHERE plot_id IS NOT ?", listOf(plotId))
                for (t in TABLES_VIDEES) db.executer("DELETE FROM $t")
            }
            db.executer("VACUUM") // no trace of the other plots in free pages
            val ok = db.requete("PRAGMA integrity_check").singleOrNull()?.values?.firstOrNull()
            check(ok == "ok") { "Copie de Groma illisible pendant l'extraction : $ok" }
        }
        File(sortie.path + "-wal").delete()
        File(sortie.path + "-shm").delete()
    }

    private fun copier(copie: File, sortie: File) {
        copie.copyTo(sortie, overwrite = true)
        val wal = File(copie.path + "-wal")
        val dest = File(sortie.path + "-wal")
        if (wal.exists()) wal.copyTo(dest, overwrite = true) else dest.delete()
        File(sortie.path + "-shm").delete()
    }

    // --- photos --------------------------------------------------------------------------------------

    private fun jetons(valeur: Any?): List<String> =
        (valeur as? String)?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /** The file for [rel] when it exists and stays inside [dossier]; null otherwise (never read). */
    private fun resoudre(dossier: File, rel: String): File? {
        if (File(rel).isAbsolute) return null
        val f = File(dossier, rel).canonicalFile
        val racine = dossier.canonicalFile
        return if (f.path.startsWith(racine.path + File.separator) && f.isFile) f else null
    }

    /** Photo paths of the plot in a stable order: the plot row, then the trees by uuid. */
    private fun cheminsPhotos(db: BaseSqlite, plotId: String): List<String> =
        (db.requete("SELECT photos FROM placettes WHERE plot_id = ?", listOf(plotId)) +
            db.requete("SELECT photos FROM arbres WHERE plot_id = ? ORDER BY uuid, fid", listOf(plotId)))
            .flatMap { jetons(it["photos"]) }.distinct()

    // --- fingerprint ---------------------------------------------------------------------------------

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun ligne(r: Map<String, Any?>): String =
        r.keys.filter { it != "fid" }.sorted().joinToString("|") { k ->
            val v = r[k]
            "$k=" + when (v) {
                null -> "n:"
                is ByteArray -> "b:" + hex(v)
                is Double, is Float -> "r:" + (v as Number).toDouble()
                is Number -> "i:" + v.toLong()
                else -> "t:" + v.toString().replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n")
            }
        }

    private fun sha(f: File): String {
        val h = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val buf = ByteArray(1 shl 16); while (true) { val n = s.read(buf); if (n < 0) break; h.update(buf, 0, n) } }
        return hex(h.digest())
    }

    fun empreinte(copie: File, dossierProjet: File, plotId: String): String = avec(copie) { db ->
        verifierPlacette(db, plotId)
        val h = MessageDigest.getInstance("SHA-256")
        fun ecrire(s: String) { h.update(s.toByteArray(Charsets.UTF_8)); h.update(10) }
        for (t in TABLES_PLACETTE + "projet") {
            ecrire("#$t")
            val lignes = if (t == "projet") db.requete("SELECT * FROM projet") else db.requete("SELECT * FROM $t WHERE plot_id = ?", listOf(plotId))
            lignes.map { ligne(it) }.sorted().forEach { ecrire(it) }
        }
        ecrire("#photos")
        for (c in cheminsPhotos(db, plotId).sorted()) resoudre(dossierProjet, c)?.let { ecrire("$c=${sha(it)}") }
        hex(h.digest())
    }

    // --- archive -------------------------------------------------------------------------------------

    fun archiver(copie: File, dossierProjet: File, plotId: String, sortie: File): ResultatArchive {
        val empreinte = empreinte(copie, dossierProjet, plotId)
        val gpkg = File.createTempFile("terrain_", ".gpkg", sortie.absoluteFile.parentFile)
        try {
            extraire(copie, plotId, gpkg)
            val manquantes = LinkedHashSet<String>()
            val noms = LinkedHashMap<String, String>() // path in the project -> entry name in the archive
            val pris = HashSet<String>()
            fun nom(rel: String): String = noms.getOrPut(rel) {
                val base = File(rel).name
                var n = base; var i = 2
                while (!pris.add(n)) n = "${i++}_$base"
                n
            }
            fun reecrire(valeur: Any?): String? {
                val brut = valeur as? String ?: return null
                return brut.split(';').joinToString(";") { j ->
                    val t = j.trim()
                    if (t.isEmpty()) j else {
                        // A photo not found is not packed but keeps its (rewritten) path: nothing is lost.
                        if (resoudre(dossierProjet, t) == null) manquantes.add(t)
                        "$DOSSIER_PHOTOS/${nom(t)}"
                    }
                }
            }
            avec(gpkg) { db ->
                db.requete("PRAGMA journal_mode = DELETE")
                val pl = db.requete("SELECT fid, photos FROM placettes")
                val ar = db.requete("SELECT fid, photos FROM arbres ORDER BY uuid, fid")
                db.transactionImmediate {
                    for ((table, lignes) in listOf("placettes" to pl, "arbres" to ar)) for (l in lignes) {
                        val neuf = reecrire(l["photos"])
                        if (neuf != null && neuf != l["photos"]) db.executer("UPDATE $table SET photos = ? WHERE fid = ?", listOf(neuf, l["fid"]))
                    }
                }
            }
            ZipOutputStream(sortie.outputStream().buffered()).use { z ->
                z.putNextEntry(ZipEntry(GPKG)); gpkg.inputStream().use { it.copyTo(z) }; z.closeEntry()
                for ((rel, entree) in noms) {
                    val f = resoudre(dossierProjet, rel) ?: continue
                    z.putNextEntry(ZipEntry("$DOSSIER_PHOTOS/$entree"))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
            return ResultatArchive(sortie, manquantes.toList(), empreinte)
        } finally {
            gpkg.delete()
        }
    }
}
