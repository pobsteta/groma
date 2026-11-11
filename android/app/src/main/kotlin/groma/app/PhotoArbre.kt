package groma.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import groma.core.PhotosArbre
import java.io.File
import java.time.LocalDateTime

/**
 * Photo of a tree (mission 022), Android side: the camera writes a temporary JPEG in the app cache
 * (through the FileProvider `<applicationId>.fichiers`, no other storage permission); [enregistrer]
 * turns it into `DCIM/<plot_id>_<tree_id>_<AAAAMMJJ-HHMMSS>.jpg` of the project folder, upright and
 * at most [PhotosArbre.COTE_MAX_PX] pixels on the long side. Names and rules are those of [PhotosArbre].
 */
object PhotoArbre {
    private const val QUALITE_JPEG = 90

    /** New empty temporary file for the camera, and the content URI to give it. */
    fun temporaire(ctx: Context): Pair<File, Uri> {
        val dossier = File(ctx.cacheDir, "photos").also { it.mkdirs() }
        val f = File.createTempFile("prise_", ".jpg", dossier)
        return f to FileProvider.getUriForFile(ctx, ctx.packageName + ".fichiers", f)
    }

    private fun orientation(chemin: String, m: Matrix) {
        when (ExifInterface(chemin).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
    }

    /**
     * Resizes [temporaire] into the project and deletes it. Returns the path relative to [dossierProjet]
     * (to store in `arbres.photos`), or null when the camera wrote nothing usable.
     */
    fun enregistrer(temporaire: File, dossierProjet: File, plotId: String, treeId: String, instant: LocalDateTime = LocalDateTime.now()): String? {
        try {
            if (!temporaire.isFile || temporaire.length() == 0L) return null
            // Two shots within the same second must not overwrite each other.
            var t = instant
            while (File(dossierProjet, PhotosArbre.chemin(plotId, treeId, t)).exists()) t = t.plusSeconds(1)
            val rel = PhotosArbre.chemin(plotId, treeId, t)
            val dest = File(dossierProjet, rel).also { it.parentFile?.mkdirs() }
            val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporaire.path, bornes)
            if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return null
            val (l, h) = PhotosArbre.tailleReduite(bornes.outWidth, bornes.outHeight)
            var echantillon = 1
            while (bornes.outWidth / (echantillon * 2) >= l && bornes.outHeight / (echantillon * 2) >= h) echantillon *= 2
            val brut = BitmapFactory.decodeFile(temporaire.path, BitmapFactory.Options().apply { inSampleSize = echantillon }) ?: return null
            val matrice = Matrix().apply { postScale(l.toFloat() / brut.width, h.toFloat() / brut.height) }
            orientation(temporaire.path, matrice)
            val final = Bitmap.createBitmap(brut, 0, 0, brut.width, brut.height, matrice, true)
            val tmp = File(dest.path + ".part")
            tmp.outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, QUALITE_JPEG, it) }
            if (!tmp.renameTo(dest)) { tmp.delete(); return null }
            return rel
        } finally {
            temporaire.delete()
        }
    }

    /** Small preview (long side at most [px] pixels) of a project photo, null when the file is missing or unreadable. */
    fun vignette(dossierProjet: File, chemin: String, px: Int = 240): Bitmap? {
        val f = File(dossierProjet, chemin)
        if (PhotosArbre.controlerPhotos(chemin) != null || !f.isFile) return null
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bornes)
        if (bornes.outWidth <= 0) return null
        var echantillon = 1
        while (maxOf(bornes.outWidth, bornes.outHeight) / (echantillon * 2) >= px) echantillon *= 2
        val brut = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = echantillon }) ?: return null
        val m = Matrix()
        orientation(f.path, m)
        return Bitmap.createBitmap(brut, 0, 0, brut.width, brut.height, m, true)
    }
}
