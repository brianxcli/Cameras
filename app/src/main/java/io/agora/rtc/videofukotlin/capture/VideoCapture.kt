package io.agora.rtc.videofukotlin.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import kotlin.collections.ArrayList

class VideoCapture(context : Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private lateinit var camera: CameraDevice
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var captureRequest: CaptureRequest
    private var currentCameraId: String = DEFAULT_CAMERA_ID
    @Volatile private var cameraOpened: Boolean = false
    @Volatile private var isCapturing: Boolean = false
    private val cameraStateCallback: CameraStateCallBack = CameraStateCallBack()
    private val captureStateCallBack: CaptureStateCallBack = CaptureStateCallBack()
    private lateinit var captureLooper: CaptureRequestLooper

    // Called in the session's onClosed callback, indicating whether we
    // want to close the current active camera device as well
    @Volatile var shouldCloseCurrentCamera: Boolean = false

    // Whether we want to open the camera again when a capture session
    // is closed. Used in the session's onClosed callback while switching cameras.
    @Volatile var shouldReopenCamera: Boolean = false

    @Volatile var shouldQuitWorkingThread: Boolean = false

    // If the device hasn't been prepared properly, the preview request
    // is recorded here, and the preview will be triggered if the
    // camera opens correctly
    private var previewPendingRequest = false

    // Surfaces that want to receive the capture data
    private val displayTargetList: MutableList<Surface> = ArrayList()

    private lateinit var previewDisplay: SurfaceTexture

    private lateinit var capturedImageReader: ImageReader
    private val imageAvailableListener: OnCaptureImageAvailableListener = OnCaptureImageAvailableListener()

    private var width: Int = 640
    private var height: Int = 480

    private lateinit var handlerThread: EGLHandlerThread
    private lateinit var handler: Handler

    constructor(context: Context, width: Int, height: Int) : this(context) {
        this.width = width
        this.height = height

        handlerThread = createHandlerThread()
        handler = Handler(handlerThread.looper)
    }

    private fun createHandlerThread() : EGLHandlerThread {
        val thread = EGLHandlerThread(TAG)
        thread.start()
        return thread
    }

    companion object CameraUtil {
        const val DEFAULT_CAPTURE_WIDTH = 640
        const val DEFAULT_CAPTURE_HEIGHT = 480
        const val PREVIEW_SURFACE_INDEX: Int = 0
        const val MAX_IMAGE_READER_SIZE = 1
        const val TAG: String = "VideoCapture"
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

    /**
     * Set the display target as a SurfaceTexture, must be
     * called before starting camera preview
     */
    fun setPreviewDisplay(preview: SurfaceTexture?) {
        handler.post{ previewDisplay = preview!! }
    }

    private fun createPreviewSurface() : Surface {
        val size: Size = findOptimalBufferSize(width, height)
        previewDisplay.setDefaultBufferSize(size.width, size.height)
        return Surface(previewDisplay)
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

    private fun createImageReader(size: Size) {
        capturedImageReader = ImageReader.newInstance(size.width,
            size.height, ImageFormat.YUV_420_888, MAX_IMAGE_READER_SIZE)
        capturedImageReader.setOnImageAvailableListener(imageAvailableListener, handler)
    }

    private fun getDisplayTargetList() {
        if (displayTargetList.isNotEmpty()) {
            displayTargetList.clear()
        }

        // The size of the captured images
        val size: Size = findOptimalBufferSize(width, height)

        // keep the preview display the first in the list
        displayTargetList.add(PREVIEW_SURFACE_INDEX, createPreviewSurface())

        createImageReader(size)
        displayTargetList.add(capturedImageReader.surface)
    }

    private fun createCaptureSession() {
        getDisplayTargetList()
        camera.createCaptureSession(displayTargetList, captureStateCallBack, handler)
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
        shouldQuitWorkingThread = quitWorkingThread
        captureSession.close()
    }

    private fun closeCamera() {
        camera.close()
        recycleTargets()
    }

    private fun recycleTargets() {
        capturedImageReader.close()
    }

    fun switchCamera() {
        shouldReopenCamera = true
        shouldQuitWorkingThread = false
        currentCameraId = switchCameraId()
        stopCapture(true, shouldQuitWorkingThread)
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
            displayTargetList.forEach { surface -> builder.addTarget(surface) }
            captureRequest = builder.build()

            captureLooper = CaptureRequestLooper(session, captureRequest, handler)
            captureLooper.setCaptureFrameRate(60)
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

            if (shouldQuitWorkingThread) {
                handler.post{ handlerThread.quitSafely() }
                shouldQuitWorkingThread = false
            }
        }

        override fun onActive(session: CameraCaptureSession) {
            // the session starts actively processing capture requests
            isCapturing = true
        }
    }

    inner class CaptureRequestLooper(private var session: CameraCaptureSession,
                                     private var request: CaptureRequest,
                                     private val handler: Handler) {
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
                    session.capture(request, null, handler)
                }

                if (!paused) {
                    handler.postDelayed(callback, interval)
                }
            }
        }
    }

    inner class OnCaptureImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            val image: Image = reader!!.acquireLatestImage()
            if (image.format == ImageFormat.YUV_420_888) {
                // assembly a frame using the planes contained in the image
                // and queue it into the working thread maintaining EGL context
            }

            image.close()
        }
    }

    inner class EGLHandlerThread(name: String) : HandlerThread(name) {
        init {
            createEGLContext()
        }

        private fun createEGLContext() {

        }
    }
}