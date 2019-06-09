package io.agora.rtc.videofukotlin.opengles

import android.graphics.SurfaceTexture

class EglSurfaceWindow(eglCore: EglCore) : EglSurfaceBase(eglCore) {

    constructor(eglCore: EglCore, surfaceTexture: SurfaceTexture) : this(eglCore) {
        createWindowSurface(surfaceTexture)
    }
}