package io.agora.rtc.videofukotlin.ui.activities

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.EGL14
import android.opengl.EGLSurface
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

    lateinit var camera : Camera

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)
        local_preview_container.surfaceTextureListener = this
    }

    override fun onAllPermissionsGranted() {

    }

    private fun renderThreadRunning() : Boolean {
        return ::renderThread.isInitialized && renderThread.isAlive
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        Log.i(tag, "onSurfaceTextureSizeChanged")
        resetSurface(surfaceTexture, width, height)
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture?) {
        Log.i(tag, "onSurfaceTextureUpdated")
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture?): Boolean {
        Log.i(tag, "onSurfaceTextureDestroyed")
        if (renderThreadRunning()) {
            handler.post {
                if (::camera.isInitialized) {
                    camera.stopPreview()
                    camera.release()
                }
            }
        }
        return true
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        Log.i(tag, "onSurfaceTextureAvailable")
        renderThread = RenderThread(tag)
        renderThread.start()
        handler = Handler(renderThread.looper)

        resetSurface(surfaceTexture, width, height)
    }

    private fun resetSurface(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        surfaceTexture!!.setDefaultBufferSize(width, height)
        if (renderThreadRunning()) {
            handler.post {
                camera = Camera.open()
                camera.setPreviewTexture(surfaceTexture)
                camera.setDisplayOrientation(90)
                camera.startPreview()
            }
        }
    }

    inner class RenderThread(name: String) : HandlerThread(name) {
        override fun start() {
            initOpenGL()
            super.start()
        }

        private fun initOpenGL() {
            eglCore = EglCore()
            // create dummy surface to create a context
            dummySurface = eglCore.createOffscreenSurface(1, 1)
            eglCore.makeCurrent(dummySurface)
            program = ProgramTextureOES()
        }

        override fun quit(): Boolean {
            releaseOpenGL()
            return super.quit()
        }

        private fun releaseOpenGL() {
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
        }
    }
}