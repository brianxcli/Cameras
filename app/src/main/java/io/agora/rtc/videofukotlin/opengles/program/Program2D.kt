package io.agora.rtc.videofukotlin.opengles.program

import android.opengl.GLES20
import android.util.Log
import io.agora.rtc.videofukotlin.opengles.EglUtil
import java.nio.FloatBuffer

open class Program2D : Program() {
    private val tag : String = "Program2D"

    init {
        textureTarget = GLES20.GL_TEXTURE_2D

        // Prepares the program in the current EGL context
        program = createTextureProgram(vertexShaderString, fragShaderString2D)

        if (program == 0) {
            throw RuntimeException("Unable to create program")
        }

        // Find the indices of binding attributes of this program.
        // The associations between the names and the attribute variables take effect
        // and can be queried after the program is linked.
        // We can see those variables as inputs to the programs which
        // can be initialized and modified before the program runs.
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord")

        uMVPMatrixLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")

        vertexBuffer.put(fullRectVertexCoords).position(0)
        texBuffer.put(fullRectTexCoords).position(0)
    }

    fun draw(textureId: Int, imageWidth: Int, imageHeight: Int, viewWidth: Int,
                  viewHieght: Int, mvpTransform: FloatArray, texTransform: FloatArray) {
        draw(mvpTransform, texTransform, vertexBuffer, 0,
            4, 2, 8, texBuffer, textureId, 8)
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
    private fun draw(mvpTransform: FloatArray, texTransform: FloatArray,
                     vertexBuffer: FloatBuffer, firstVertex: Int,
                       vertexCount: Int, coordsPerVertex: Int, vertexStride: Int,
                       texBuffer: FloatBuffer, textureId: Int, texStride: Int) {
        EglUtil.checkGLError(tag, "draw start")

        // Select the program
        GLES20.glUseProgram(program)
        EglUtil.checkGLError(tag, "glUseProgram")

        // Select the texture slot position to use. For the legacy reasons,
        // the active texture unit must be specified for rendering.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, textureId)
        EglUtil.checkGLError(tag, "glBindTexture")

        // The glUniformX functions specify the values of uniform
        // variables for the current program object.
        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpTransform,0)
        EglUtil.checkGLError(tag, "glUniformMatrix4fv")

        // Copy the texture transformation matrix over
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texTransform, 0)
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

    public override fun release() {
        Log.i(tag, "release program")
        super.release()
    }
}