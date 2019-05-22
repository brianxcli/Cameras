package io.agora.rtc.videofukotlin.engine

/**
 * An event handler implemented by applications. Registered to
 * RtcEventHandler, specifically in an activity where
 * controls over rtc event callbacks are required
 */
interface IEventHandler {
    fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int)
    fun onUserJoined(uid: Int, elapsed: Int)
    fun onUserOffline(uid: Int, reason: Int)
    fun onFirstRemoteVideoDecoded(uid: Int, width: Int, height: Int, elapsed: Int)
}