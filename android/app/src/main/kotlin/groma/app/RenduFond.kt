package groma.app

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Camera background (external OES texture on a full-screen quad), as in the ARCore samples. */
class RenduFond {
    var texture = 0; private set
    private var programme = 0
    private val quad: FloatBuffer = tampon(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val uv: FloatBuffer = tampon(FloatArray(8))

    fun creer() {
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        texture = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        programme = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, SOMMETS))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENTS))
            GLES20.glLinkProgram(it)
        }
    }

    fun dessiner(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            quad.position(0); uv.position(0)
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad, Coordinates2d.TEXTURE_NORMALIZED, uv)
        }
        if (frame.timestamp == 0L) return
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(programme)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        val pos = GLES20.glGetAttribLocation(programme, "a_pos")
        val tex = GLES20.glGetAttribLocation(programme, "a_uv")
        quad.position(0); uv.position(0)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 0, uv)
        GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(tex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }

    private fun tampon(v: FloatArray) = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }

    companion object {
        const val SOMMETS = "attribute vec4 a_pos; attribute vec2 a_uv; varying vec2 v_uv; void main(){ gl_Position = a_pos; v_uv = a_uv; }"
        const val FRAGMENTS = "#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 v_uv; uniform samplerExternalOES t; void main(){ gl_FragColor = texture2D(t, v_uv); }"
    }
}
