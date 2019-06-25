package io.agora.rtc.videofukotlin.capture

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import java.lang.RuntimeException

abstract class EglHandlerThread(name: String) : HandlerThread(name) {
    open val EglHandlerThread.msgQuitThread: Int get() {
        return 1
    }

    private var handler: EGLHandler? = null

    override fun run() {
        onCreateEglContext()
        super.run()
    }

    fun getHandler() : EGLHandler {
        if (!isAlive) {
            throw RuntimeException("Get handler before a handler thread starts")
        }

        if (handler == null) {
            handler = EGLHandler(looper)
        }

        return handler!!
    }

    override fun quit() : Boolean {
        getHandler().sendEmptyMessage(this.msgQuitThread)
        return true
    }

    inner class EGLHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message?) {
            when (msg!!.what) {
                this@EglHandlerThread.msgQuitThread -> {
                    if (this@EglHandlerThread.isAlive) {
                        this@EglHandlerThread.onReleaseEglContext()
                        this@EglHandlerThread.quitSafely()
                    }
                }
            }
        }
    }

    abstract fun onCreateEglContext()
    abstract fun onReleaseEglContext()
}