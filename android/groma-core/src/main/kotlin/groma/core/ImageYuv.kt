package groma.core

import java.nio.ByteBuffer

/**
 * Copies of a YUV_420_888 camera image into packed arrays (mission 033). Pure functions on the plane
 * buffers, read with ABSOLUTE indexes only: ARCore's `Image.Plane.getBuffer()` returns a new buffer,
 * at position 0, on every call, so a `position()` set on one call is lost on the next (the bug that
 * filled every package with the first sensor row repeated). Callers fetch each buffer ONCE.
 */
object ImageYuv {
    /** Packed luminance (width x height) of the Y plane. */
    fun luminance(y: ByteBuffer, largeur: Int, hauteur: Int, pasLigne: Int, pasPixel: Int = 1): ByteArray {
        val out = ByteArray(largeur * hauteur)
        var o = 0
        for (r in 0 until hauteur) {
            val debut = r * pasLigne
            if (pasPixel == 1) {
                for (c in 0 until largeur) out[o++] = y.get(debut + c)
            } else {
                for (c in 0 until largeur) out[o++] = y.get(debut + c * pasPixel)
            }
        }
        return out
    }

    /** NV21 (Y then interleaved V, U) from the three planes; [lum] is the packed luminance. */
    fun nv21(
        lum: ByteArray, largeur: Int, hauteur: Int,
        u: ByteBuffer, pasLigneU: Int, pasPixelU: Int, v: ByteBuffer, pasLigneV: Int, pasPixelV: Int,
    ): ByteArray {
        val out = ByteArray(largeur * hauteur * 3 / 2)
        lum.copyInto(out)
        var o = largeur * hauteur
        for (r in 0 until hauteur / 2) for (c in 0 until largeur / 2) {
            out[o++] = v.get(r * pasLigneV + c * pasPixelV)
            out[o++] = u.get(r * pasLigneU + c * pasPixelU)
        }
        return out
    }
}
