package io.agora.rtc.videofukotlin.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import io.agora.rtc.videofukotlin.opengles.*
import kotlin.collections.ArrayList

class CameraModule(context : Context) : SurfaceTexture.OnFrameAvailableListener {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private lateinit var camera: CameraDevice
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var captureRequest: CaptureRequest
    private var currentCameraId: String = DEFAULT_CAMERA_ID
    @Volatile private var cameraOpened: Boolean = false
    @Volatile private var isCapturing: Boolean = false
    private val cameraStateCallback: CameraStateCallBack = CameraStateCallBack()
    private val captureStateCallBack: CaptureStateCallBack = CaptureStateCallBack()
    private val captureResultCallback: CaptureResultCallback = CaptureResultCallback()
    private lateinit var captureLooper: CaptureRequestLooper

    // Called in the session's onClosed callback, indicating whether we
    // want to close the current active camera device as well
    @Volatile var shouldCloseCurrentCamera: Boolean = false

    // Whether we want to open the camera again when a capture session
    // is closed. Used in the session's onClosed callback while switching cameras.
    @Volatile var shouldReopenCamera: Boolean = false

    @Volatile var shouldQuitThread: Boolean = false

    // If the device hasn't been prepared properly, the preview request
    // is recorded here, and the preview will be triggered after the
    // camera opens correctly
    private var previewPendingRequest = false

    // Define a Surface from SurfaceTexture as a preview session target.
    // Don't confuse this preview target with the "View" that actually
    // displays the preview images through the window system.
    private val displayTargets: MutableList<Surface> = ArrayList()
    private var targetTexId: Int = 0
    private lateinit var targetSurfaceTex: SurfaceTexture
    private var targetTextureAvailable = false

    // Used to draw on the actual display, TextureView for example
    private lateinit var eglCore : EglCore
    private lateinit var program: ProgramTextureOES
    private var eglPreviewSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private val vertexMatrix = FloatArray(16)

    // Default capture size if not configured
    private var width: Int = DEFAULT_CAPTURE_WIDTH
    private var height: Int = DEFAULT_CAPTURE_HEIGHT

    // The view size used to show the preview on screen
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    // The SurfaceTexture from TextureView
    private var viewSurface: SurfaceTexture? = null

    private lateinit var handlerThread: EGLHandlerThread
    private lateinit var handler: Handler

    constructor(context: Context, width: Int, height: Int) : this(context) {
        configure(width, height)

        handlerThread = createGlWorkingThread()
        handler = Handler(handlerThread.looper)
    }

    private fun createGlWorkingThread() : EGLHandlerThread {
        val thread = EGLHandlerThread(TAG)
        thread.start()
        return thread
    }

    /**
     * Set the capture target width and height which must be chosen carefully
     * before preview starts. The size of the captured images may differ
     * from what has been set because the hardware may not support. The
     * result size will be equal to or the least largest size than desired.
     * It is recommended that the width is no less than the height.
     */
    fun configure(width: Int, height: Int) {
        if (cameraOpened && isCapturing) {
            Log.w(TAG, "Cannot set the capture size when previewing")
            return
        }

        this.width = width
        this.height = height
    }

    companion object CameraUtil {
        const val DEFAULT_CAPTURE_WIDTH = 640
        const val DEFAULT_CAPTURE_HEIGHT = 480
        const val TAG: String = "CameraModule"
        const val LENS_ID_FRONT: String = CameraCharacteristics.LENS_FACING_FRONT.toString()
        const val LENS_ID_BACK: String = CameraCharacteristics.LENS_FACING_BACK.toString()
        const val DEFAULT_CAMERA_ID: String = LENS_ID_BACK

        fun Size.equalOrLarger(another: Size) : Boolean {
            return this.width >= another.width && this.height >= another.height
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(id: String) {
        currentCameraId = id
        cameraManager.openCamera(currentCameraId, cameraStateCallback, handler)
    }

    fun openCamera() {
        openCamera(currentCameraId)
    }

    fun startPreview() {
        handler.post{
            if (::camera.isInitialized && cameraOpened && !isCapturing) {
                createCaptureSession()
            } else {
                previewPendingRequest = true
            }
        }
    }

    private fun createCaptureSession() {
        preparePreviewTargets()
        camera.createCaptureSession(displayTargets, captureStateCallBack, handler)
    }

    private fun preparePreviewTargets() {
        if (displayTargets.isNotEmpty()) {
            displayTargets.clear()
        }

        // It should be guaranteed that we are in an OpenGL context thread.
        targetTexId = eglCore.createTextureOES()
        targetSurfaceTex = SurfaceTexture(targetTexId)
        targetSurfaceTex.setOnFrameAvailableListener(this)

        // The size of the captured images
        val size: Size = findOptimalBufferSize(width, height)
        targetSurfaceTex.setDefaultBufferSize(size.width, size.height)

        displayTargets.add(Surface(targetSurfaceTex))
    }

    /**
     * Find the optimal buffer size according to the size
     * that we want the captured image to be
     * @param width
     * @param height
     */
    private fun findOptimalBufferSize(width: Int, height: Int) : Size {
        // The default buffer size of a Surface as a target to a
        // capture request must be one of the supported values
        // that can be acquired from the camera characteristic.
        val characteristic: CameraCharacteristics =
            cameraManager.getCameraCharacteristics(currentCameraId)
        val sizes: Array<out Size> =
            characteristic[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!
                .getOutputSizes(ImageFormat.JPEG)

        // The input width and height may not be supported.
        // We want to pick one available size, such that the
        // captured image is large enough and will be cropped as
        // little as possible to what we actually need.

        // Note the width is usually larger than the height here
        var curSize = Size(10000, 10000)
        val target = Size(width, height)
        var found = false
        sizes.forEach {
            if (it.equalOrLarger(target) && curSize.equalOrLarger(it)) {
                found = true
                curSize = it
            }
        }

        return if (found) curSize else Size(DEFAULT_CAPTURE_WIDTH, DEFAULT_CAPTURE_HEIGHT)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        if (!targetTextureAvailable) {
            targetTextureAvailable = true
        }
    }

    fun pauseCapture() {
        // abandons current in-flight capture requests as
        // fast as possible
        if (cameraOpened && isCapturing) {
            captureSession.abortCaptures()
            captureLooper.pause()
        }
    }

    fun resumeCapture() {
        captureLooper.resume()
    }

    fun stopCapture(shouldCloseCurrentCamera: Boolean, quitWorkingThread: Boolean) {
        if (cameraOpened && isCapturing) {
            pauseCapture()
            closeSession(shouldCloseCurrentCamera, quitWorkingThread)
        }
    }

    private fun closeSession(shouldCloseCamera: Boolean, quitWorkingThread: Boolean) {
        shouldCloseCurrentCamera = shouldCloseCamera
        shouldQuitThread = quitWorkingThread
        captureSession.close()
    }

    private fun closeCamera() {
        camera.close()
    }

    fun switchCamera() {
        shouldReopenCamera = true
        shouldQuitThread = false
        currentCameraId = switchCameraId()
        stopCapture(true, shouldQuitThread)
    }

    private fun switchCameraId() : String {
        return if (currentCameraId == LENS_ID_FRONT) LENS_ID_BACK else LENS_ID_FRONT
    }

    /**
     * State callback for connections to camera devices
     */
    inner class CameraStateCallBack : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            camera = cameraDevice
            currentCameraId = cameraDevice.id
            cameraOpened = true

            if (previewPendingRequest && !isCapturing) {
                // a preview request was received, but the camera was not ready.
                // it hasn't been handled until here the camera is confirmed to
                // be working properly.
                previewPendingRequest = false
                startPreview()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpened = false
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpened = false
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

            if (::camera.isInitialized && cameraOpened) {
                startCapture(session)
            }
        }

        private fun startCapture(session: CameraCaptureSession) {
            val builder: CaptureRequest.Builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            displayTargets.forEach { surface -> builder.addTarget(surface) }
            captureRequest = builder.build()

            captureLooper = CaptureRequestLooper(session, captureRequest, handler)
            captureLooper.setCaptureFrameRate(24)
            captureLooper.loop()
        }

        override fun onClosed(session: CameraCaptureSession) {
            // Creating a new session also calls the old session's callback here.
            // Closing the session also close the connection to the camera, but
            // the device can be reopened later.
            isCapturing = false
            cameraOpened = false

            if (shouldCloseCurrentCamera) { closeCamera() }

            if (shouldReopenCamera) {
                // Open another camera device instance here like when
                // switching cameras. The default camera should be set
                // to the target camera id before this callback
                previewPendingRequest = true
                openCamera()
                shouldReopenCamera = false
            }

            if (shouldQuitThread) {
                handler.post{ handlerThread.quitSafely() }
                shouldQuitThread = false
            }
        }

        override fun onActive(session: CameraCaptureSession) {
            // the session starts actively processing capture requests
            isCapturing = true
        }
    }

    // A preview capture timer using Handler. Keep and change the capture
    // interval as we want rather than the default repeated capture frequency.
    inner class CaptureRequestLooper(private var session: CameraCaptureSession,
                     private var request: CaptureRequest, private val handler: Handler) {

        // default capture frame rate is 15
        private var interval: Long = 66

        @Volatile private var paused: Boolean = false

        private val callback: CaptureRunnable = CaptureRunnable()

        /**
         * The actual frame rate may be less than desired due to the
         * capability of the camera hardware
         */
        fun setCaptureFrameRate(fps: Int) {
            // set to default if the fps is out of the valid range
            handler.post{ interval = if (fps <= 0 || fps > 30) 66 else (1000L / fps) }
        }

        fun loop() {
            handler.postDelayed(callback, interval)
        }

        fun pause() {
            if (cameraOpened && !paused) {
                paused = true
                handler.removeCallbacks(callback)
            }
        }

        fun resume() {
            if (cameraOpened && paused) {
                paused = false
                loop()
            }
        }

        inner class CaptureRunnable : Runnable {
            override fun run() {
                if (cameraOpened) {
                    session.capture(request, captureResultCallback, handler)
                }

                if (!paused) {
                    handler.postDelayed(callback, interval)
                }
            }
        }
    }

    fun setDisplayView(surface: SurfaceTexture?, width: Int, height: Int) {
        handler.post {
            viewSurface = surface
            viewWidth = width
            viewHeight = height
        }
    }

    inner class CaptureResultCallback : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession,
                        request: CaptureRequest, result: TotalCaptureResult) {
            drawFrame()
        }
    }

    private fun drawFrame() {
        if (!eglCore.initialized() ||
                viewSurface == null ||
            !targetTextureAvailable) {
            return
        }

        eglPreviewSurface = eglCore.createWindowSurface(viewSurface!!)
        eglCore.makeCurrent(eglPreviewSurface)
        program = ProgramTextureOES()

        // Update the most recent capture image
        targetSurfaceTex.updateTexImage()
        targetSurfaceTex.getTransformMatrix(vertexMatrix)

        // Draw the surface
        targetSurfaceTex.getTransformMatrix(vertexMatrix)
        program.draw(EglUtil.getIdentityMatrix(), vertexMatrix, targetTexId, viewWidth, viewHeight)

        eglCore.swapBuffers(eglPreviewSurface)

        program.release()
        eglCore.makeNothingCurrent()
        eglCore.releaseSurface(eglPreviewSurface)
        eglPreviewSurface = EGL14.EGL_NO_SURFACE
    }

    inner class EGLHandlerThread(name: String) : HandlerThread(name) {
        override fun run() {
            createEglContext()
            super.run()
        }

        private fun createEglContext() {
            eglCore = EglCore()
        }

        private fun release() {
            if (eglPreviewSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.releaseSurface(eglPreviewSurface)
                eglPreviewSurface = EGL14.EGL_NO_SURFACE
            }

            eglCore.makeNothingCurrent()
            eglCore.release()
        }

        override fun quitSafely(): Boolean {
            release()
            return super.quitSafely()
        }
    }
}