package groma.core

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Mission 033: packing a YUV_420_888 image. Expected arrays written by hand:
 * - 4 x 4 image, Y row stride 6 (2 padding bytes, value 99), Y = 10 * row + column;
 * - chroma 2 x 2, interleaved (pixel stride 2, row stride 4) as on the S23:
 *   U = 50 + 10 * row + column, V = 70 + 10 * row + column.
 * The buffers are passed at position 0 and read whatever their position: rows must differ
 * (the field bug repeated the first row on every row).
 */
class ImageYuvTest {
    private fun tampon(vararg octets: Int): ByteBuffer = ByteBuffer.wrap(ByteArray(octets.size) { octets[it].toByte() })

    private val y = tampon(
        0, 1, 2, 3, 99, 99,
        10, 11, 12, 13, 99, 99,
        20, 21, 22, 23, 99, 99,
        30, 31, 32, 33,
    )

    private fun octets(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun luminanceAvecPasDeLigne() {
        assertContentEquals(
            octets(0, 1, 2, 3, 10, 11, 12, 13, 20, 21, 22, 23, 30, 31, 32, 33),
            ImageYuv.luminance(y, 4, 4, 6),
        )
    }

    @Test
    fun positionDuTamponIgnoree() {
        y.position(18) // a reader that moved the buffer must not shift the image
        assertContentEquals(
            octets(0, 1, 2, 3, 10, 11, 12, 13, 20, 21, 22, 23, 30, 31, 32, 33),
            ImageYuv.luminance(y, 4, 4, 6),
        )
    }

    @Test
    fun luminancePasDePixel2() {
        val y2 = tampon(1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6)
        assertContentEquals(octets(1, 2, 3, 4, 5, 6), ImageYuv.luminance(y2, 3, 2, 6, 2))
    }

    @Test
    fun nv21Entrelace() {
        val lum = ImageYuv.luminance(y, 4, 4, 6)
        // Interleaved planes: U then V one byte apart, row stride 4, last byte of each plane absent.
        val u = tampon(50, 70, 51, 71, 60, 80, 61)
        val v = tampon(70, 50, 71, 51, 80, 60, 81)
        assertContentEquals(
            octets(0, 1, 2, 3, 10, 11, 12, 13, 20, 21, 22, 23, 30, 31, 32, 33, 70, 50, 71, 51, 80, 60, 81, 61),
            ImageYuv.nv21(lum, 4, 4, u, 4, 2, v, 4, 2),
        )
    }
}
