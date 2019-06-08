package io.agora.rtc.videofukotlin.opengles

import android.opengl.EGL14
import android.opengl.EGLSurface

class EglSurfaceBase(val eglCore: EglCore) {
    private var width: Int = -1
    private var height: Int = -1

    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    fun createWindowSurface(surface: Any) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("surface already created")
        }

        eglSurface = eglCore.createWindowSurface(surface)
    }

    fun createOffscreenSurface(width: Int, height: Int) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("surface already created")
        }

        eglSurface = eglCore.createOffscreenSurface(width, height)
        this.width = width
        this.height = height
    }
}