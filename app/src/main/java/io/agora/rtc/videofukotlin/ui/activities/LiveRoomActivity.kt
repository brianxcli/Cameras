package io.agora.rtc.videofukotlin.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import io.agora.rtc.RtcEngine
import io.agora.rtc.video.VideoCanvas
import io.agora.rtc.video.VideoCanvas.RENDER_MODE_HIDDEN
import io.agora.rtc.video.VideoEncoderConfiguration
import io.agora.rtc.video.VideoEncoderConfiguration.STANDARD_BITRATE
import io.agora.rtc.video.VideoEncoderConfiguration.VD_640x480
import io.agora.rtc.videofukotlin.R
import io.agora.rtc.videofukotlin.engine.IEventHandler
import kotlinx.android.synthetic.main.live_room_activity.*

private const val TAG : String = "LiveRoomActivity"

class LiveRoomActivity : RTCActivity(), IEventHandler {
    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.live_room_activity)
    }

    override fun onDestroy() {
        super.onDestroy()
        eventHandler().removeHandler(this)
        rtcEngine().leaveChannel()
        RtcEngine.destroy()
    }

    override fun onAllPermissionsGranted() {
        super.onAllPermissionsGranted()
        initUI()
        configRtcEngine()
    }

    private fun initUI() {
        val surface : SurfaceView = RtcEngine.CreateRendererView(this)
        rtcEngine().setupLocalVideo(VideoCanvas(surface))
        local_view_container.addView(surface)
    }

    private fun configRtcEngine() {
        rtcEngine().setVideoEncoderConfiguration(VideoEncoderConfiguration(
            VD_640x480,
            VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
            STANDARD_BITRATE,
            VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT))

        addHandler(this)
        rtcEngine().joinChannel(null, "12345", null, 0)
    }

    private fun prepareRemoteView(uid : Int) {
        val surface : SurfaceView = RtcEngine.CreateRendererView(this)
        surface.setZOrderOnTop(true)
        rtcEngine().setupRemoteVideo(VideoCanvas(surface, RENDER_MODE_HIDDEN, uid))
        remote_view_container.addView(surface)
        remote_view_container.visibility = View.VISIBLE
    }

    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
        Log.e(TAG, "channel join successfully: $channel $uid $elapsed")
    }

    override fun onUserJoined(uid: Int, elapsed: Int) {
        Log.e(TAG, "remote user joined: $uid $elapsed")
    }

    override fun onUserOffline(uid: Int, reason: Int) {
        Log.e(TAG, "remote user offline: $uid $reason")
    }

    override fun onFirstRemoteVideoDecoded(uid: Int, width: Int, height: Int, elapsed: Int) {
        Log.e(TAG, "first remote video decoded:$uid $width $height $elapsed")
        runOnUiThread {
            prepareRemoteView(uid)
        }
    }
}
