package io.agora.rtc.videofukotlin.engine

import io.agora.rtc.IRtcEngineEventHandlerEx

class RtcEventHandler : IRtcEngineEventHandlerEx() {
    private val handlerList : ArrayList<IEventHandler> = ArrayList()

    fun addHandler(handler : IEventHandler) {
        if (!handlerList.contains(handler)) {
            handlerList.add(handler)
        }
    }

    fun removeHandler(handler : IEventHandler) {
        if (handlerList.contains(handler)) {
            handlerList.remove(handler)
        }
    }

    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
        handlerList.forEach {
            it.onJoinChannelSuccess(channel, uid, elapsed)
        }
    }

    override fun onUserJoined(uid: Int, elapsed: Int) {
        handlerList.forEach {
            it.onUserJoined(uid, elapsed)
        }
    }

    override fun onUserOffline(uid: Int, reason: Int) {
        handlerList.forEach {
            it.onUserOffline(uid, reason)
        }
    }

    override fun onFirstRemoteVideoDecoded(uid: Int, width: Int, height: Int, elapsed: Int) {
        handlerList.forEach {
            it.onFirstRemoteVideoDecoded(uid, width, height, elapsed)
        }
    }
}