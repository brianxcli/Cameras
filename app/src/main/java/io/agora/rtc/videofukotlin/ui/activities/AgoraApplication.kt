package io.agora.rtc.videofukotlin.ui.activities

import android.app.Application
import io.agora.rtc.videofukotlin.capture.AgoraCamera

class AgoraApplication : Application() {
    private val defaultWidth: Int = 1280
    private val defaultHeight: Int = 720

    private lateinit var agoraCamera: AgoraCamera

    fun agoraCamera() : AgoraCamera { return agoraCamera }

    override fun onCreate() {
        super.onCreate()
        agoraCamera = AgoraCamera(applicationContext, defaultWidth, defaultHeight)
    }

    override fun onTerminate() {
        super.onTerminate()
        agoraCamera.stopCapture(true, true)
    }
}