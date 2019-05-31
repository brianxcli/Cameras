package io.agora.rtc.videofukotlin.ui.activities

import android.os.Bundle
import io.agora.rtc.RtcEngine
import io.agora.rtc.videofukotlin.R
import io.agora.rtc.videofukotlin.capture.VideoCapture
import io.agora.rtc.videofukotlin.engine.IEventHandler
import io.agora.rtc.videofukotlin.engine.RtcEventHandler

abstract class RTCActivity : BaseActivity() {
    private val eventHandler : RtcEventHandler = RtcEventHandler()

    private lateinit var engine : RtcEngine

    protected fun rtcEngine() : RtcEngine = engine
    protected fun eventHandler() : RtcEventHandler = eventHandler

    protected fun addHandler(handler : IEventHandler) {
        eventHandler.addHandler(handler)
    }

    private val defaultWidth: Int = 1280
    private val defaultHeight: Int = 720

    private lateinit var videoCapture: VideoCapture

    protected fun videoCapture(): VideoCapture = videoCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoCapture = VideoCapture(applicationContext, defaultWidth, defaultHeight)
    }

    /**
     * Agora RTC based functions need to be called after
     * all required permissions are granted
     */
    override fun onAllPermissionsGranted() {
        // prepare rtc engine here
        prepareRtcEngine()
    }

    private fun prepareRtcEngine() {
        engine = RtcEngine.create(this, getString(R.string.app_id), eventHandler)
        engine.setLogFilter(65535)
        engine.enableVideo()
        engine.enableDualStreamMode(false)
    }
}