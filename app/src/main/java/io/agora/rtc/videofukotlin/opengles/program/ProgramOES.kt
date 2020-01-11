package io.agora.rtc.videofukotlin.opengles.program

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import io.agora.rtc.videofukotlin.opengles.EglUtil

/**
 * Compiled shader programs that are used by the graphics pipeline.
 * For openGLES programs, the vertex shader and fragment shader
 * are needed to be defined before we can use the rendering system.
 * The default type is TEXTURE_2D
 */
open class ProgramOES : Program() {
    private val tag = "ProgramTextureOES"

    private val textureId : IntArray = IntArray(1)
    private val framebufferId : IntArray = IntArray(1)

    init {
        textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES

        // Prepares the program in the current EGL context
        program = createTextureProgram(vertexShaderString, fragShaderStringOES)

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

        textureUniformLoc = GLES20.glGetUniformLocation(program, "vTextureCoord")

        vertexBuffer.put(fullRectVertexCoords).position(0)
        texBuffer.put(fullRectTexCoords).position(0)
    }

    fun draw(mvpMatrix: FloatArray, texMatrix: FloatArray, textureId: Int, width: Int, height: Int) {
        draw(mvpMatrix, texMatrix, textureId, width, height, fullRectTexCoords)
    }

    fun drawViewRatio(mvpMatrix: FloatArray, texMatrix: FloatArray, textureId: Int,
                      texWidth: Int, texHeight: Int, viewWidth: Int, viewHeight: Int, rotation: Int) {
        val texCoord = ratioTexCoord(texWidth, texHeight, viewWidth, viewHeight, rotation)
        draw(mvpMatrix, texMatrix, textureId, viewWidth, viewHeight, texCoord)
    }

    private fun draw(mvpMatrix: FloatArray, texMatrix: FloatArray, textureId: Int, width: Int, height: Int, texCoord: FloatArray) {
        GLES20.glViewport(0, 0, width, height)

        draw(mvpMatrix, EglUtil.createFloatBuffer(fullRectVertexCoords),
            0, 4, 2, 2 * 4, texMatrix,
            EglUtil.createFloatBuffer(texCoord), textureId, 2 * 4)
    }

    fun drawToFramebuffer(textureId : Int, width: Int, height: Int) : Int {
        if (framebufferId[0] == 0) {
            createFramebuffer(width, height)
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId[0])
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        EglUtil.checkGLError(tag, "glUseProgram")

        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, identityMatrix,0)
        EglUtil.checkGLError(tag, "glUniformMatrix4fv")

        // Copy the texture transformation matrix over
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, identityMatrix, 0)
        EglUtil.checkGLError(tag, "glUniformMatrix4fv")

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        EglUtil.checkGLError(tag, "glEnableVertexAttribArray")

        GLES20.glVertexAttribPointer(aPositionLoc, 2,
            GLES20.GL_FLOAT, false, 8, vertexBuffer)
        EglUtil.checkGLError(tag, "glVertexAttribPointer")

        texBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        EglUtil.checkGLError(tag, "glEnableVertexAttribArray")

        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2,
            GLES20.GL_FLOAT, false, 8, texBuffer)
        EglUtil.checkGLError(tag, "glVertexAttribPointer")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, textureId)
        EglUtil.checkGLError(tag, "glBindTexture")

        GLES20.glUniform1i(textureUniformLoc, 0)
        EglUtil.checkGLError(tag, "glUniform1i")

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EglUtil.checkGLError(tag, "glDrawArrays")

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glUseProgram(0)

        return this.textureId[0]
    }

    private fun createFramebuffer(width: Int, height: Int) {
        GLES20.glGenTextures(1, textureId, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0,
            GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())

        GLES20.glGenFramebuffers(1, framebufferId, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textureId[0], 0)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    public override fun release() {
        Log.i(tag, "release program")
        releaseFramebuffer()
        super.release()
    }

    private fun releaseFramebuffer() {
        if (framebufferId[0] > 0) {
            GLES20.glDeleteFramebuffers(1, framebufferId, 0)
            framebufferId[0] = 0
        }

        if (textureId[0] > 0) {
            GLES20.glDeleteTextures(1, textureId, 0)
            textureId[0] = 0
        }
    }
}