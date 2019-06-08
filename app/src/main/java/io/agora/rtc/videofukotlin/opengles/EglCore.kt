package io.agora.rtc.videofukotlin.opengles

import android.opengl.*
import kotlin.RuntimeException
import android.opengl.EGL14
import android.util.Log


class EglCore {
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private lateinit var eglConfig: EGLConfig

    constructor(sharedContext: EGLContext, flags: Int) {
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

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            throw RuntimeException("unable to initialize EGL14 default display")
        }

        val config: EGLConfig? = getConfig(flags, 3)
        val attributesContext = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )

        eglContext = EGL14.eglCreateContext(eglDisplay, config!!,
            sharedContext, attributesContext, 0)

        if (EGL14.eglGetError() == EGL14.EGL_SUCCESS) {
            eglConfig = config
        }
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

    fun releaseSurface(surface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, surface)
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
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
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
     * have the  "recordable" attribute
     */
    fun createWindowSurface(surface: Any) : EGLSurface {
        if (surface.javaClass.name != "Surface" &&
                surface.javaClass.name != "SurfaceTexture") {
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

    fun makeCurrent(surface: EGLSurface) {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.d("EGLCore", "NOTE: makeCurrent w/o display")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    fun makeCurrent(drawSurfaceBase: EGLSurface, readSurfaceBase: EGLSurface) {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.d("EGLCore", "NOTE: makeCurrent(surface) w/o display")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, drawSurfaceBase, readSurfaceBase, eglContext)) {
            throw RuntimeException("eglMakeCurrent(draw,read) failed")
        }
    }

    fun makeNothingCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)) {
            throw RuntimeException("eglMakeCurrent() failed")
        }
    }

    fun swapBuffers(eglSurface: EGLSurface) : Boolean {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun checkEglError(msg: String) {
        val error = EGL14.eglGetError()
        if (error != EGL14.EGL_SUCCESS) {
            throw RuntimeException("$msg EGL Error: 0x${Integer.toHexString(error)}")
        }
    }

}