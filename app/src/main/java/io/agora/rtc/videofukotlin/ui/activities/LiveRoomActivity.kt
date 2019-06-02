package io.agora.rtc.videofukotlin.ui.activities

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
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

class LiveRoomActivity : RTCActivity(), IEventHandler, TextureView.SurfaceTextureListener, View.OnClickListener {
    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.live_room_activity)
        local_preview.surfaceTextureListener = this
    }

    override fun onDestroy() {
        super.onDestroy()

        eventHandler().removeHandler(this)
        rtcEngine().leaveChannel()
        RtcEngine.destroy()
    }

    override fun onAllPermissionsGranted() {
        super.onAllPermissionsGranted()
        // configRtcEngine()
        initUI()
        videoCapture().openCamera()
        videoCapture().startPreview()
    }

    private fun initUI() {
        btn_switch_camera.setOnClickListener(this)
    }

    override fun onClick(view: View?) {
        when (view!!.id) {
            btn_switch_camera.id -> { videoCapture().switchCamera() }
        }
    }

    private fun initPreview(surfaceTexture: SurfaceTexture?) {
        videoCapture().setPreviewDisplay(surfaceTexture)
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
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {

    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {

    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        // if the Activity exits, this method will be called AFTER the Activity's
        // onDestroy() is called.
        return true
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        initPreview(surface)
    }
}
