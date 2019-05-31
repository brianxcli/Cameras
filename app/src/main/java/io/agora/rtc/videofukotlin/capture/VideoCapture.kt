package io.agora.rtc.videofukotlin.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.util.*
import kotlin.collections.ArrayList

class VideoCapture(context : Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private lateinit var camera: CameraDevice
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var captureRequest: CaptureRequest
    private var currentCameraId: String = DEFAULT_CAMERA_ID
    private var cameraConnected: Boolean = false
    private var isCapturing: Boolean = false
    private val cameraStateCallback: CameraStateCallBack = CameraStateCallBack()
    private val captureStateCallBack: CaptureStateCallBack = CaptureStateCallBack()
    private val cameraCaptureCallback: CameraCaptureCallback = CameraCaptureCallback()
    private lateinit var captureLooper: CaptureRequestLooper

    // if the device hasn't been prepared properly, the preview request
    // is recorded here, and the preview will be triggered if the camera opens
    private var previewPendingRequest = false

    private var isPreviewing: Boolean = false

    // surfaces that want to receive the preview data
    private val displayTargetList: MutableList<Surface> = ArrayList()

    private lateinit var previewDisplay: SurfaceTexture

    private var width: Int = 640
    private var height: Int = 480

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    constructor(context: Context, width: Int, height: Int) : this(context) {
        this.width = width
        this.height = height
    }

    @SuppressLint("MissingPermission")
    fun openCamera(id: String) {
        handlerThread = createHandlerThread()
        handler = Handler(handlerThread.looper)

        currentCameraId = id
        cameraManager.openCamera(currentCameraId, cameraStateCallback, handler)
    }

    fun openCamera() {
        openCamera(DEFAULT_CAMERA_ID)
    }

    /**
     * Set the display target as a SurfaceTexture, must be called before
     * starting preview
     */
    fun setPreviewDisplay(preview: SurfaceTexture?) {
        Log.e(TAG, "preview surface is ready")
        previewDisplay = preview!!
    }

    private fun getDisplayTargetList() {
        // keep the preview display the first in the list
        displayTargetList.add(PREVIEW_SURFACE_INDEX, Surface(previewDisplay))

        // TODO Maybe add more target surface here
    }

    private fun createCaptureSession() {
        getDisplayTargetList()
        camera.createCaptureSession(displayTargetList, captureStateCallBack, handler)
    }

    fun startPreview() {
        if (::camera.isInitialized && cameraConnected && !isCapturing) {
            createCaptureSession()
        } else {
            previewPendingRequest = true
        }
    }

    fun closeCamera() {
        handler.post{
            if (cameraConnected) {
                camera.close()
                cameraConnected = false
            }}
    }

    fun destroy() {
        closeCamera()
        quitHandlerThread(handlerThread)
    }

    companion object CameraUtil {
        const val DEFAULT_CAMERA_ID: String = "1"
        const val PREVIEW_SURFACE_INDEX: Int = 0
        const val TAG: String = "VideoCapture"

        fun switchCamera() {

        }

        fun createHandlerThread() : HandlerThread {
            val thread = HandlerThread(TAG)
            thread.start()
            return thread
        }

        fun quitHandlerThread(thread: HandlerThread?) {
            if (thread!!.isAlive) {
                thread.quit()
            }
        }
    }

    /**
     * State callback for connections to camera devices
     */
    inner class CameraStateCallBack : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            camera = cameraDevice
            cameraConnected = true

            if (previewPendingRequest && !isCapturing) {
                // a preview request is received, but it is not
                // responded because the camera is not ready.
                previewPendingRequest = false
                startPreview()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraConnected = false
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraConnected = false
        }
    }

    /**
     * State callback for maintaining capture sessions. Sessions maintain
     * configuration of capture behavior and desired functionality
     */
    inner class CaptureStateCallBack : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            // the session cannot be configured as requested
            // captureSession.abortCaptures()
        }

        override fun onConfigured(session: CameraCaptureSession) {
            // The configuration is complete and the session is ready to actually capture data
            captureSession = session

            if (::camera.isInitialized && cameraConnected) {
                startCapture(session)
            }
        }

        private fun startCapture(session: CameraCaptureSession) {
            val builder: CaptureRequest.Builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            displayTargetList.forEach { surface -> builder.addTarget(surface) }
            captureRequest = builder.build()

            captureLooper = CaptureRequestLooper(session, captureRequest, handler)
            captureLooper.setCaptureFrameRate(60)
            captureLooper.loop()
        }

        override fun onClosed(session: CameraCaptureSession) {
            // the session is closed
            isCapturing = false
            captureLooper.pause()
        }

        override fun onActive(session: CameraCaptureSession) {
            // the session starts actively processing capture requests
            isCapturing = true
        }
    }

    private var lastTs: Long = 0

    /**
     * Capture callback for acquiring states when capturing a single image
     */
    inner class CameraCaptureCallback : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(session: CameraCaptureSession,
            request: CaptureRequest, timestamp: Long, frameNumber: Long) {
            // start to respond to the capture request or reprocess request
        }

        override fun onCaptureSequenceAborted(session: CameraCaptureSession, sequenceId: Int) {
            super.onCaptureSequenceAborted(session, sequenceId)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession,
            request: CaptureRequest, result: TotalCaptureResult) {
        }

        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
            super.onCaptureFailed(session, request, failure)
        }

        override fun onCaptureSequenceCompleted(session: CameraCaptureSession, sequenceId: Int, frameNumber: Long) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber)
        }

        override fun onCaptureProgressed(session: CameraCaptureSession,
            request: CaptureRequest, partialResult: CaptureResult) {
            super.onCaptureProgressed(session, request, partialResult)
        }

        override fun onCaptureBufferLost(session: CameraCaptureSession,
            request: CaptureRequest, target: Surface, frameNumber: Long) {
            super.onCaptureBufferLost(session, request, target, frameNumber)
        }
    }

    inner class CaptureRequestLooper(private var session: CameraCaptureSession,
                                     private var request: CaptureRequest,
                                     private val handler: Handler) {
        // default capture frame rate is 15
        private var interval: Long = 66

        private var paused: Boolean = false

        private val callback: CaptureRunnable = CaptureRunnable()

        fun setCaptureFrameRate(fps: Int) {
            // set to default if the fps is out of the valid range
            interval = if (fps <= 0 || fps > 60) 66 else (1000L / fps)
        }

        fun setSession(session: CameraCaptureSession) {
            this.session = session
        }

        fun setRequest(request: CaptureRequest) {
            this.request = request
        }

        fun loop() {
            handler.postDelayed(callback, interval)
        }

        fun pause() {
            paused = true
        }

        fun resume() {
            paused = false
        }

        inner class CaptureRunnable : Runnable {
            override fun run() {
                if (cameraConnected) {
                    session.capture(request, cameraCaptureCallback, handler)
                }

                if (!paused) {
                    handler.postDelayed(callback, interval)
                }
            }
        }
    }
}