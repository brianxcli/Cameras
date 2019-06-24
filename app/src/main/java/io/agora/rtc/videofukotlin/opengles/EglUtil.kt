package io.agora.rtc.videofukotlin.opengles

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object EglUtil {
    private val IDENTITY_MATRIX: FloatArray = FloatArray(16)
    private const val SIZE_OF_FLOAT = 4

    init {
        Matrix.setIdentityM(IDENTITY_MATRIX, 0)
    }

    fun getIdentityMatrix() : FloatArray {
        return IDENTITY_MATRIX
    }

    fun checkGLError(tag: String, op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val msg = "$op: glError 0x$error"
            Log.e(tag, msg)
            throw RuntimeException(msg)
        }
    }

    /**
     * Allocates a direct float buffer, and populates it with the float array data.
     */
    fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
        val bb = ByteBuffer.allocateDirect(coords.size * SIZE_OF_FLOAT)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(coords)
        fb.position(0)
        return fb
    }
}