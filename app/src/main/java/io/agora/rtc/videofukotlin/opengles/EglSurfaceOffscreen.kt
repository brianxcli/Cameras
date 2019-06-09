package io.agora.rtc.videofukotlin.opengles

class EglSurfaceOffscreen(eglCore: EglCore) : EglSurfaceBase(eglCore) {
    constructor(eglCore: EglCore, width: Int, height: Int) : this(eglCore) {
        createOffscreenSurface(width, height)
    }
}