package io.agora.rtc.videofukotlin.ui.activities

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.TextureView
import io.agora.rtc.videofukotlin.R
import io.agora.rtc.videofukotlin.opengles.EglCore
import io.agora.rtc.videofukotlin.opengles.program.Program2D
import io.agora.rtc.videofukotlin.opengles.program.ProgramOES
import kotlinx.android.synthetic.main.camera_activity.*

class CameraActivity : BaseActivity(), TextureView.SurfaceTextureListener {
    private val tag : String = "CameraActivity"

    private lateinit var renderThread : RenderThread
    private lateinit var handler : Handler

    lateinit var eglCore : EglCore
    var programOES: ProgramOES? = null
    var program2D: Program2D? = null
    var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    var dummySurface : EGLSurface = EGL14.EGL_NO_SURFACE
    @Volatile var available: Boolean = false

    var viewWidth = 0
    var viewHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)
        local_preview_container.surfaceTextureListener = this
    }

    override fun onAllPermissionsGranted() {

    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        Log.i(tag, "onSurfaceTextureSizeChanged")
        viewWidth = width
        viewHeight = height
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture?) {
        //Log.i(tag, "onSurfaceTextureUpdated")
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture?): Boolean {
        Log.i(tag, "onSurfaceTextureDestroyed")
        available = false
        return true
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        Log.i(tag, "onSurfaceTextureAvailable")
        viewWidth = width
        viewHeight = height
        renderThread = RenderThread(tag)
        renderThread.start()
        handler = Handler(renderThread.looper)
        available = true
        handler.post {
            renderThread.startPreview(surfaceTexture!!, width, height)
        }
    }

    inner class RenderThread(name: String) : HandlerThread(name), Camera.PreviewCallback {
        private lateinit var camera: Camera
        private var viewWidth: Int = 0
        private var viewHeight: Int = 0

        private var textureId = 0
        private lateinit var previewTex : SurfaceTexture
        private lateinit var buffer : ByteArray
        private val matrix = FloatArray(16)
        private val mvp = FloatArray(16)
        private val identity = FloatArray(16)

        override fun start() {
            initOpenGL()
            openCamera()
            super.start()
        }

        private fun initOpenGL() {
            eglCore = EglCore()
            // create dummy surface to create a context
            dummySurface = eglCore.createOffscreenSurface(1, 1)
            eglCore.makeCurrent(dummySurface)
            programOES = ProgramOES()
            program2D = Program2D()
            textureId = eglCore.createTextureOES()
            previewTex = SurfaceTexture(textureId)

            Matrix.setIdentityM(identity, 0)
        }

        private fun openCamera() {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
        }

        fun startPreview(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            surface = eglCore.createWindowSurface(surfaceTexture)
            camera.setPreviewTexture(previewTex)
            camera.parameters.setPictureSize(1920, 1080)
            buffer = ByteArray(1920 * 1080 * 3 / 2)
            camera.addCallbackBuffer(buffer)
            camera.setPreviewCallbackWithBuffer(this)
            camera.setDisplayOrientation(0)
            camera.startPreview()
            Matrix.setIdentityM(mvp, 0)
            viewWidth = width
            viewHeight = height
        }

        override fun quit(): Boolean {
            stopPreview()
            releaseOpenGL()
            return super.quit()
        }

        private fun stopPreview() {
            if (::camera.isInitialized) {
                camera.stopPreview()
                camera.release()
            }
        }

        private fun releaseOpenGL() {
            eglCore.unbindTexture(textureId)
            previewTex.release()
            programOES?.release()
            program2D?.release()
            releaseSurface()
            eglCore.release()
        }

        private fun releaseSurface() {
            eglCore.makeNothingCurrent()
            if (dummySurface != EGL14.EGL_NO_SURFACE) {
                eglCore.releaseSurface(dummySurface)
                dummySurface = EGL14.EGL_NO_SURFACE
            }

            if (surface != EGL14.EGL_NO_SURFACE) {
                eglCore.releaseSurface(surface)
                surface = EGL14.EGL_NO_SURFACE
            }
        }

        override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
            if (!eglCore.isCurrent(surface)) {
                eglCore.makeCurrent(surface)
            }

            if (available) {
                previewTex.updateTexImage()
                previewTex.getTransformMatrix(matrix)

                // Matrix.setRotateM(mvp, 0, 90f, 0f, 0f, 1f)
                val tex = programOES?.drawToFramebuffer(textureId, 1920, 1080)

                GLES20.glViewport(0, 0, viewWidth, viewHeight)
                program2D?.draw(tex!!, 1920, 1080,
                    viewWidth, viewHeight, mvp, matrix)
                eglCore.swapBuffers(surface)

            }
            camera?.addCallbackBuffer(buffer)
        }
    }
}