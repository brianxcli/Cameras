package io.agora.rtc.videofukotlin.ui.activities

import android.animation.ObjectAnimator
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.animation.ScaleAnimation
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
        btn_pause_preview.setOnClickListener(this)
        btn_resume_preview.setOnClickListener(this)
        btn_rotate.setOnClickListener(this)
        btn_scaling.setOnClickListener(this)
    }

    override fun onClick(view: View?) {
        when (view!!.id) {
            btn_switch_camera.id -> { videoCapture().switchCamera() }
            btn_pause_preview.id -> { videoCapture().pauseCapture() }
            btn_resume_preview.id -> { videoCapture().resumeCapture() }
            btn_rotate.id -> {
                ObjectAnimator.ofFloat(local_preview, "rotationY", 0F, 360F).apply {
                    duration = 2000
                    start()
                }
            }

            btn_scaling.id -> {
                val scaleAnim = ScaleAnimation(1.0F, 0.4F, 1.0F, 0.4F,
                    local_preview.measuredWidth / 2F, local_preview.measuredHeight / 2F)
                scaleAnim.duration = 500
                local_preview.startAnimation(scaleAnim)
                scaleAnim.reset()
            }
        }
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
        videoCapture().setDisplayView(surface!!, width, height)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {

    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        // if the Activity exits, this method will be called AFTER the Activity's
        // onDestroy() is called.
        Log.i(TAG, "onSurfaceTextureDestroyed")
        videoCapture().setDisplayView(null, 0, 0)
        return true
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        // It is the right time we call back to camera module to set up
        // the preview display.
        videoCapture().setDisplayView(surface!!, width, height)
    }
}
