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
import io.agora.rtc.videofukotlin.opengles.ProgramTextureOES
import kotlinx.android.synthetic.main.camera_activity.*

class CameraActivity : BaseActivity(), TextureView.SurfaceTextureListener {
    private val tag : String = "CameraActivity"

    private lateinit var renderThread : RenderThread
    private lateinit var handler : Handler

    lateinit var eglCore : EglCore
    var program: ProgramTextureOES? = null
    var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    var dummySurface : EGLSurface = EGL14.EGL_NO_SURFACE
    @Volatile var available: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)
        local_preview_container.surfaceTextureListener = this
    }

    override fun onAllPermissionsGranted() {

    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        Log.i(tag, "onSurfaceTextureSizeChanged")
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
            program = ProgramTextureOES()
            textureId = eglCore.createTextureOES()
            previewTex = SurfaceTexture(textureId)
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
            program?.release()
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
                GLES20.glViewport(0, 0, viewWidth, viewHeight)
            }

            if (available) {
                previewTex.updateTexImage()
                previewTex.getTransformMatrix(matrix)

                program?.draw(mvp, matrix, textureId, viewWidth, viewHeight)
                eglCore.swapBuffers(surface)

            }
            equivalence()
            camera?.addCallbackBuffer(buffer)
        }

        private fun equivalence() {

        }
    }
}