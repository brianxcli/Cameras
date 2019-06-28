package io.agora.rtc.videofukotlin.opengles

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import java.nio.FloatBuffer

/**
 * Compiled shader programs that are used by the graphics pipeline.
 * For openGLES programs, the vertex shader and fragment shader
 * are needed to be defined before we can use the rendering system.
 * The default type is TEXTURE_2D
 */
class ProgramTextureOES {
    private val textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
    private var handle: Int
    private val aPositionLoc: Int
    private var uMVPMatrixLoc: Int = 0
    private var uTexMatrixLoc: Int = 0
    private val aTextureCoordLoc: Int

    init {
        // Prepares the program in the current EGL context
        handle = createTextureProgram(VERTEX_SHADER, FRAGMENT_SHADER_OES)

        if (handle == 0) {
            throw RuntimeException("Unable to create program")
        }

        // Find the indices of binding attributes of this program.
        // The associations between the names and the attribute variables take effect
        // and can be queried after the program is linked.
        // We can see those variables as inputs to the programs which
        // can be initialized and modified before the program runs.
        aPositionLoc = GLES20.glGetAttribLocation(handle, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(handle, "aTextureCoord")

        uMVPMatrixLoc = GLES20.glGetUniformLocation(handle, "uMVPMatrix")
        uTexMatrixLoc = GLES20.glGetUniformLocation(handle, "uTexMatrix")
    }

    /**
     * Loads and compiles the provided shader source.
     * @return A handle the native shader, or 0 on failure
     */
    private fun loadShader(type: Int, source: String) : Int {
        var shader = GLES20.glCreateShader(type)
        EglUtil.checkGLError(TAG, "glCeateShader type $type")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = arrayOf(0).toIntArray()
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)

        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader $type:")
            Log.e(TAG, "" + GLES20.glGetShaderInfoLog(type))
            GLES20.glDeleteShader(type)
            shader = 0
        }

        return shader
    }

    private fun createTextureProgram(vertexSource: String, fragmentSource: String) : Int {
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
            Log.e(TAG, "Could not create program")
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val status = arrayOf(0).toIntArray()
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program")
            Log.e(TAG, GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
        }

        return program
    }

    fun draw(mvpMatrix: FloatArray, texMatrix: FloatArray, textureId: Int, width: Int, height: Int) {
        draw(mvpMatrix, texMatrix, textureId, width, height, FULL_RECTANGLE_TEX_COORDS)
    }

    fun drawViewRatio(mvpMatrix: FloatArray, texMatrix: FloatArray, textureId: Int,
                      texWidth: Int, texHeight: Int, viewWidth: Int, viewHeight: Int, rotation: Int) {
        val texCoord = ratioTexCoord(texWidth, texHeight, viewWidth, viewHeight, rotation)
        draw(mvpMatrix, texMatrix, textureId, viewWidth, viewHeight, texCoord)
    }

    private fun draw(mvpMatrix: FloatArray, texMatrix: FloatArray, textureId: Int, width: Int, height: Int, texCoord: FloatArray) {
        GLES20.glViewport(0, 0, width, height)

        draw(mvpMatrix, EglUtil.createFloatBuffer(FULL_RECTANGLE_COORDS), 0, 4,
            2, 2 * 4, texMatrix, EglUtil.createFloatBuffer(texCoord),
            textureId, 2 * 4)
    }

    private fun ratioTexCoord(texWidth: Int, texHeight: Int, viewWidth: Int,
                              viewHeight: Int, rotation: Int) : FloatArray {
        val texW = texHeight.toFloat()
        val texH = texWidth.toFloat()

        val ratioT = texW / texH
        val ratioV = viewWidth / viewHeight.toFloat()

        val actualW: Float
        val actualH: Float

        when (ratioT >= ratioV) {
            true -> {
                if (texW >= texHeight) {
                    actualH = texH
                    actualW = actualH * ratioV

                    val diffX = (texW - actualW) / 2 / texW
                    return floatArrayOf(
                        diffX, 0F,
                        1 - diffX, 0F,
                        diffX, 1F,
                        1 - diffX, 1F
                    )
                } else {
                    actualW = texW
                    actualH = actualW / ratioV

                    val diffY = (texH - actualH) / 2 / texH
                    return floatArrayOf(
                        0F, diffY,
                        1F, diffY,
                        0F, 1 - diffY,
                        1F, 1 - diffY
                    )
                }
            }

            false -> {
                if (texW <= texH) {
                    actualW = texW
                    actualH = actualW / ratioV

                    val diffY = (texH - actualH) / 2 / texH
                    return floatArrayOf(
                        0F, diffY,
                        1F, diffY,
                        0F, 1 - diffY,
                        1F, 1 - diffY
                    )
                } else {
                    actualH = texH
                    actualW = actualH * ratioV

                    val diffX = (texW - actualW) / 2 / texW
                    return floatArrayOf(
                        diffX, 0F,
                        1 - diffX, 0F,
                        diffX, 1F,
                        1 - diffX, 1F
                    )
                }
            }
        }
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
    private fun draw(mvpMatrix: FloatArray, vertexBuffer: FloatBuffer, firstVertex: Int,
             vertexCount: Int, coordsPerVertex: Int, vertexStride: Int,
             texMatrix: FloatArray, texBuffer: FloatBuffer, textureId: Int, texStride: Int) {
        EglUtil.checkGLError(TAG, "draw start")

        // Select the program
        GLES20.glUseProgram(handle)
        EglUtil.checkGLError(TAG, "glUseProgram")

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
        EglUtil.checkGLError(TAG, "glUniformMatrix4fv")

        // Copy the texture transformation matrix over
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        EglUtil.checkGLError(TAG, "glUniformMatrix4fv")

        // Enable the "aPosition" vertex attribute
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        EglUtil.checkGLError(TAG, "glEnableVertexAttribArray")

        // Connect vertex buffer to "aPosition"
        GLES20.glVertexAttribPointer(aPositionLoc, coordsPerVertex, GLES20.GL_FLOAT,
            false, vertexStride, vertexBuffer)
        EglUtil.checkGLError(TAG, "glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute
        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        EglUtil.checkGLError(TAG, "glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord"
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2,
            GLES20.GL_FLOAT, false, texStride, texBuffer)
        EglUtil.checkGLError(TAG, "glVertexAttribPointer")

        // Draw the rect
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount)
        EglUtil.checkGLError(TAG, "glDrawArrays")

        // Done, disable vertex array, texture and program
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
        GLES20.glBindTexture(textureTarget, 0)
        GLES20.glUseProgram(0)
    }

    /**
     * Releases the program
     * Note that the appropriate EGL context must be current(i.e.
     * the EGL context or thread that was used to create this program)
     */
    fun release() {
        Log.d(TAG, "deleting program $handle")
        GLES20.glDeleteProgram(handle)
        handle = -1
    }

    companion object ProgramStrings {
        const val TAG: String = "ProgramTextureOES"

        /**
         * Simple vertex shader used for all program
         */
        const val VERTEX_SHADER: String =
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
        const val FRAGMENT_SHADER_2D: String =
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
        const val FRAGMENT_SHADER_OES =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n"

        private val FULL_RECTANGLE_COORDS = floatArrayOf(
            -1.0f, -1.0f,   // 0 bottom left
            1.0f, -1.0f,    // 1 bottom right
            -1.0f, 1.0f,    // 2 top left
            1.0f, 1.0f      // 3 top right
        )

        private val FULL_RECTANGLE_TEX_COORDS = floatArrayOf(
            0.0f, 0.0f,    // 0 bottom left
            1.0f, 0.0f,    // 1 bottom right
            0.0f, 1.0f,    // 2 top left
            1.0f, 1.0f     // 3 top right
        )
    }
}