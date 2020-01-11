package io.agora.rtc.videofukotlin.opengles.program

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import io.agora.rtc.videofukotlin.opengles.EglUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

abstract class Program {
    private val tag : String = "Program"

    /**
     * Simple vertex shader used for all program
     */
    protected val vertexShaderString: String =
        "uniform mat4 uMVPMatrix;\n" +
        "uniform mat4 uTexMatrix;\n" +
        "attribute vec4 aPosition;\n" +
        "attribute vec4 aTextureCoord;\n" +
        "varying vec2 vTextureCoord;\n" +
        "void main() {\n" +
        "    gl_Position = uMVPMatrix * aPosition;\n" +
        "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
        "}\n"

    /**
     * Simple fragment shader for used with "normal" 2D textures
     */
    protected val fragShaderString2D: String =
        "precision mediump float;\n" +
        "varying vec2 vTextureCoord;\n" +
        "uniform sampler2D sTexture;\n" +
        "void main() {\n" +
        "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
        "}\n"

    /**
     * Simple fragment shader for use with external 2D textures,
     * eg. what we get from SurfaceTexture
     */
    protected val fragShaderStringOES =
        "#extension GL_OES_EGL_image_external : require\n" +
        "precision mediump float;\n" +
        "varying vec2 vTextureCoord;\n" +
        "uniform samplerExternalOES sTexture;\n" +
        "void main() {\n" +
        "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
        "}\n"

    protected val fullRectVertexCoords = floatArrayOf(
        -1.0f, -1.0f,   // 0 bottom left
        1.0f, -1.0f,    // 1 bottom right
        -1.0f, 1.0f,    // 2 top left
        1.0f, 1.0f      // 3 top right
    )

    protected val fullRectTexCoords = floatArrayOf(
        0.0f, 0.0f,    // 0 bottom left
        1.0f, 0.0f,    // 1 bottom right
        0.0f, 1.0f,    // 2 top left
        1.0f, 1.0f     // 3 top right
    )

    protected var program: Int = 0
    protected var aPositionLoc: Int = 0
    protected var uMVPMatrixLoc: Int = 0
    protected var uTexMatrixLoc: Int = 0
    protected var aTextureCoordLoc: Int = 0
    protected var textureUniformLoc : Int = 0
    protected var textureTarget : Int = 0

    protected val identityMatrix = FloatArray(16)

    protected val vertexBuffer : FloatBuffer =
        ByteBuffer.allocateDirect(fullRectVertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    protected val texBuffer : FloatBuffer =
        ByteBuffer.allocateDirect(fullRectTexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    init {
        Matrix.setIdentityM(identityMatrix, 0)
    }

    /**
     * Loads and compiles the provided shader source.
     * @return A handle the native shader, or 0 on failure
     */
    open fun loadShader(type: Int, source: String) : Int {
        var shader = GLES20.glCreateShader(type)
        EglUtil.checkGLError(tag, "glCeateShader type $type")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = arrayOf(0).toIntArray()
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)

        if (compiled[0] == 0) {
            Log.e(tag, "Could not compile shader $type:")
            Log.e(tag, "" + GLES20.glGetShaderInfoLog(type))
            GLES20.glDeleteShader(type)
            shader = 0
        }

        return shader
    }

    open fun createTextureProgram(vertexSource: String, fragmentSource: String) : Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            return 0
        }

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            return 0
        }

        var program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(tag, "Could not create program")
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val status = arrayOf(0).toIntArray()
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(tag, "Could not link program")
            Log.e(tag, GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
        }

        return program
    }

    /**
     * Issues the draw call. Does the full setup on every call.
     *
     * @param mvpMatrix 4x4 projection matrix
     * @param vertexBuffer Buffer with vertex position data
     * @param firstVertex Index of the first vertex to use in vertexBuffer
     * @param vertexCount Number of vertices in vertexBuffer
     * @param coordsPerVertex The number of coordinates per vertex
     * @param vertexStride Width, in bytes, of the position data for each vertex
     * @param texMatrix 4x4 transformation matrix for texture coordinates.(
     *                  primarily intended for use with SurfaceTexture)
     * @param texBuffer Buffer with vertex texture data
     * @param texStride Width, in bytes, of the texture data for each vertex
     */
    protected fun draw(mvpMatrix: FloatArray, vertexBuffer: FloatBuffer, firstVertex: Int,
                     vertexCount: Int, coordsPerVertex: Int, vertexStride: Int,
                     texMatrix: FloatArray, texBuffer: FloatBuffer, textureId: Int, texStride: Int) {
        EglUtil.checkGLError(tag, "draw start")

        // Select the program
        GLES20.glUseProgram(program)
        EglUtil.checkGLError(tag, "glUseProgram")

        // Select the texture slot position to use. For the legacy reasons,
        // the active texture unit must be specified for rendering.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        // The glUniformX functions specify the values of uniform
        // variables for the current program object.

        // Copy the model/view/projection matrix
        // Model matrix contains every translations, rotations or scaling
        // View matrix controls the way we look at a scene.
        // Projection matrix indicates how to deal with objects of different
        // distances or directions
        // The order: v' = P * V * M * v
        // Each vertex will be multiplied by MVP matrix by shading program
        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix,0)
        EglUtil.checkGLError(tag, "glUniformMatrix4fv")

        // Copy the texture transformation matrix over
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        EglUtil.checkGLError(tag, "glUniformMatrix4fv")

        // Enable the "aPosition" vertex attribute
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        EglUtil.checkGLError(tag, "glEnableVertexAttribArray")

        // Connect vertex buffer to "aPosition"
        GLES20.glVertexAttribPointer(aPositionLoc, coordsPerVertex, GLES20.GL_FLOAT,
            false, vertexStride, vertexBuffer)
        EglUtil.checkGLError(tag, "glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute
        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        EglUtil.checkGLError(tag, "glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord"
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2,
            GLES20.GL_FLOAT, false, texStride, texBuffer)
        EglUtil.checkGLError(tag, "glVertexAttribPointer")

        // Draw the rect
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount)
        EglUtil.checkGLError(tag, "glDrawArrays")

        // Done, disable vertex array, texture and program
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
        GLES20.glBindTexture(textureTarget, 0)
        GLES20.glUseProgram(0)
    }

    protected fun ratioTexCoord(texWidth: Int, texHeight: Int, viewWidth: Int,
                              viewHeight: Int, rotation: Int) : FloatArray {
        val w: Int
        val h: Int

        // the captured image is always horizontal, and a rotation of
        // 0 or 180 means the corresponding device direction is
        // vertical. So we need to change the direction of the
        // texture to crop correctly.
        if (rotation == 0 || rotation == 180) {
            w = viewHeight
            h = viewWidth
        } else {
            w = viewWidth
            h = viewHeight
        }

        val rt = texWidth / texHeight.toFloat()
        val rv = w / h.toFloat()

        when (rt == rv) {
            true -> {
                return fullRectTexCoords
            }
            else -> {
                when (rt - rv > 0) {
                    true -> {
                        val ratio = w / texWidth.toFloat()
                        val actualH = texHeight * ratio
                        val d = (h - actualH) / h.toFloat() / 2
                        return floatArrayOf(
                            0f, d,
                            1f, d,
                            0f, 1 - d,
                            1f, 1 - d
                        )
                    }
                    else -> {
                        val ratio = h / texHeight.toFloat()
                        val actualW = texWidth * ratio
                        val d = (w - actualW) / w.toFloat() / 2
                        return floatArrayOf(
                            d, 0f,
                            1 - d, 0f,
                            d, 1f,
                            1 - d, 1f
                        )
                    }
                }
            }
        }
    }

    /**
     * Releases the program resources.
     * Note that the appropriate EGL context must be current
     * (i.e.the EGL context or thread that was used to create this program)
     */
    protected open fun release() {
        Log.d(tag, "deleting program $program")
        GLES20.glDeleteProgram(program)
        program = -1
    }
}