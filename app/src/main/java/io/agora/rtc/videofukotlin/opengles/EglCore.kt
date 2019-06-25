package io.agora.rtc.videofukotlin.opengles

import android.opengl.*
import kotlin.RuntimeException
import android.opengl.EGL14
import android.util.Log

/**
 * Core EGL states (display, context and config)
 * The EGLContext must only be attached to one thread at a time.
 * This class is not thread-safe
 */
class EglCore {
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglConfig: EGLConfig
    private var initialized = false

    constructor(sharedContext: EGLContext, flags: Int) {
        EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API)

        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("EGL display already set")
        }

        // For Android, the display id should only be the default
        // display, and any values other than that would result
        // in unsupported operation exceptions.
        // Although Android maintains the EGLDisplay in a ref-counted
        // manner, we can make it initialized only once by
        // creating only one EglCore instance, and thus terminating
        // the EGLDisplay instance only once should be OK for us.
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Error get EGL14 default display")
        }

        checkEglError("eglGetDisplay")

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            throw RuntimeException("unable to initialize EGL14 default display")
        }
        checkEglError("eglInitialize")

        val config: EGLConfig? = getConfig(flags, 3)
        val attributesContext = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        checkEglError("eglChooseConfig")

        eglContext = EGL14.eglCreateContext(eglDisplay, config!!,
            sharedContext, attributesContext, 0)
        checkEglError("eglCreateContext")

        eglConfig = config
        initialized = true
    }

    constructor() : this(EGL14.EGL_NO_CONTEXT, 0)

    private fun getConfig(flags: Int, version: Int) : EGLConfig? {
        var renderType: Int = EGL14.EGL_OPENGL_ES2_BIT
        if (version >= 3) {
            renderType = renderType or EGLExt.EGL_OPENGL_ES3_BIT_KHR
        }

        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, renderType,
            EGL14.EGL_NONE, 0,  // placeholder for recordable [@-3]
            EGL14.EGL_NONE
        )

        val configs: Array<out EGLConfig?> = arrayOf<EGLConfig?>(null)
        val numConfigs: IntArray = intArrayOf(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attributes, 0,
                configs, 0, configs.size, numConfigs, 0)) {
            return null
        }

        return configs[0]
    }

    fun initialized() : Boolean {
        return initialized
    }

    fun releaseSurface(surface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, surface)
        checkEglError("releaseSurface")
    }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            // Android uses a reference-counted EGLDisplay. So
            // for every eglInitialize() we need an eglTerminate().
            // Each time we call to initialize a display, EGL returns
            // the current display connection with the reference
            // number plus 1. Every time the display is terminated,
            // it simply reduces the ref number until it becomes 1
            // when the actual display connection will be disconnected.
            makeNothingCurrent()

            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }

        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
    }

    /**
     * Create an EGL surface associated with a Surface
     * If this is destined for MediaCodec, the EGLConfig should
     * have the  "recordable" attribute.
     * EGL connects the EGLSurface to the producer interface of the
     * window object's BufferQueue, thus rendering to that EGLSurface
     * results in a buffer being dequeued, rendered into, and queued
     * for used by the consumer.
     */
    fun createWindowSurface(surface: Any) : EGLSurface {
        val name = surface.javaClass.name
        if (name != "android.view.Surface" &&
            name != "android.graphics.SurfaceTexture") {
            throw RuntimeException("invalid surface:$surface")
        }

        val surfaceAttr = intArrayOf(EGL14.EGL_NONE)
        val eglSurface: EGLSurface? = EGL14.eglCreateWindowSurface(
            eglDisplay, eglConfig, surface, surfaceAttr, 0)
        checkEglError("eglCreateWindowSurface")

        if (eglSurface == null) {
            throw RuntimeException("surface was null")
        }

        return eglSurface
    }

    /**
     * Create an EGL surface associated with an offscreen buffer
     */
    fun createOffscreenSurface(width: Int, height: Int) : EGLSurface {
        val surfaceAttr = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )

        val eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay,
            eglConfig, surfaceAttr, 0)

        checkEglError("eglCreatePbufferSurface")
        if (eglSurface == null) {
            throw RuntimeException("surface was null")
        }

        return eglSurface
    }

    fun isCurrent(surface: EGLSurface) : Boolean {
        return eglContext == EGL14.eglGetCurrentContext() &&
                surface == EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
    }

    /**
     * Bind current context to the surface which is specified as
     * both the draw and read surfaces.
     */
    fun makeCurrent(surface: EGLSurface) {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.d("EGLCore", "NOTE: makeCurrent w/o display")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    /**
     * Binds current context to the draw and read surfaces.
     * For an OpenGLES context, draw surfaces are used for all operations
     * except for any pixel data read back or copied, which is taken from
     * the frame buffer values of the read surfaces.
     */
    fun makeCurrent(drawSurface: EGLSurface, readSurface: EGLSurface) {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.d("EGLCore", "NOTE: makeCurrent(surface) w/o display")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, drawSurface, readSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent(draw,read) failed")
        }
    }

    fun makeNothingCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
            throw RuntimeException("eglMakeCurrent() failed")
        }
    }

    /**
     * If the surface is a back-buffered window surface, then the color buffer
     * is copied to the native window associated with that surface.
     * If the surface is a single-buffered window, pixmap, or pbuffer
     * surface, the swap has NO effect.
     */
    fun swapBuffers(eglSurface: EGLSurface) : Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun createTextureOES() : Int {
        return createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
    }

    private fun createTexture(target: Int) : Int {
        // As the GL to acquire an available texture object that is
        // returned as a texture id. Then we bind(assign) this object
        // to a target texture type.
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        checkEglError("glGenTextures")

        val texId = textures[0]
        GLES20.glBindTexture(target, texId)

        // Image textures are created with an (s, t) coordinate system.
        // The glTexParameter functions are used to set the values to the
        // parameters of the target texture type.

        // GL_TEXTURE_MIN_FILTER: the minify function is used whenever the pixel
        // being textured maps to an area greater than one texture element.
        // Options are using the nearest one value or the weighted average of
        // the nearest four values, with some other options available.
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)

        // GL_TEXTURE_MAG_FILTER: is used when the pixel being textured maps to
        // an area less than or equal to one texture element.
        // Options are GL_NEAREST and GL_LINEAR. GL_NEAREST is generally faster
        // but produces images with sharper edges because the transition is not
        // as smooth. The initial value is GL_LINEAR
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // During texture mapping, OpenGLES uses coordinates ranging between 0
        // and 1. The WRAP parameters are used to compute values of coordinates
        // outside this range. It's better to look into how this parameter
        // behaves using figures.
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        return texId
    }

    private fun checkEglError(msg: String) {
        val error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException("$msg EGL Error: 0x${Integer.toHexString(error)}")
        }
    }

}