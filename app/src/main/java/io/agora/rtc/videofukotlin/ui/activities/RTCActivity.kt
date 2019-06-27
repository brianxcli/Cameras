package io.agora.rtc.videofukotlin.ui.activities

abstract class RTCActivity : BaseActivity() {
    /**
     * Agora RTC based functions need to be called after
     * all required permissions are granted
     */
    override fun onAllPermissionsGranted() {
        // Prepare rtc engine here
    }
}