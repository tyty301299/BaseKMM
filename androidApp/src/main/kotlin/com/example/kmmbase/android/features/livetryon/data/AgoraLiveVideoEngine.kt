package com.example.kmmbase.android.features.livetryon.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import com.example.kmmbase.domain.model.LiveTryOnSession
import com.example.kmmbase.android.platform.video.NativeLiveVideoEngine
import com.example.kmmbase.domain.repository.LiveVideoListener
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration

/** Agora is confined to this platform adapter. SDK callbacks are marshalled to the main thread. */
internal class AgoraLiveVideoEngine(private val context: Context) : NativeLiveVideoEngine {
    override val localView = SurfaceView(context).apply { setZOrderMediaOverlay(true) }
    override val remoteView = SurfaceView(context)
    private val main = Handler(Looper.getMainLooper())
    private var engine: RtcEngine? = null
    private var generation = 0

    override fun start(session: LiveTryOnSession, listener: LiveVideoListener) {
        stop()
        val epoch = generation
        var remoteUid: Int? = session.renderUserId.toLongOrNull()?.takeIf { it in 1..0xffffffffL }?.toInt()
        fun current(block: () -> Unit) { main.post { if (generation == epoch) block() } }
        fun bind(uid: Int) {
            engine?.setupRemoteVideo(VideoCanvas(remoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
        }
        val handler = object : IRtcEngineEventHandler() {
            override fun onUserJoined(uid: Int, elapsed: Int) = current { if (uid == remoteUid) bind(uid) }
            override fun onUserInfoUpdated(uid: Int, userInfo: UserInfo?) = current {
                if (userInfo?.userAccount == session.renderUserId) { remoteUid = uid; bind(uid) }
            }
            override fun onFirstRemoteVideoFrame(uid: Int, width: Int, height: Int, elapsed: Int) = current {
                if (uid == remoteUid) listener.onRemoteVideo()
            }
            override fun onRemoteVideoStats(stats: RemoteVideoStats) = current {
                if (stats.uid == remoteUid) listener.onStats(stats.rendererOutputFrameRate)
            }
            override fun onUserOffline(uid: Int, reason: Int) = current {
                if (uid == remoteUid) listener.onFailure("Vidu đã dừng video thử đồ.")
            }
            override fun onError(err: Int) = current { listener.onFailure("Lỗi video Agora ($err). Hãy mở phiên mới.") }
            override fun onTokenPrivilegeWillExpire(token: String?) = current { listener.onFailure("Phiên video sắp hết hạn. Hãy mở phiên mới.") }
            override fun onConnectionStateChanged(state: Int, reason: Int) = current {
                if (state == Constants.CONNECTION_STATE_FAILED || state == Constants.CONNECTION_STATE_DISCONNECTED) {
                    listener.onFailure("Mất kết nối video. Hãy mở phiên mới.")
                }
            }
        }
        try {
            val rtc = RtcEngine.create(context.applicationContext, session.appId, handler)
            engine = rtc
            rtc.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtc.disableAudio()
            rtc.enableVideo()
            rtc.setVideoEncoderConfiguration(VideoEncoderConfiguration(720, 1280,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30, VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT))
            rtc.setupLocalVideo(VideoCanvas(localView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
            rtc.startPreview()
            val options = ChannelMediaOptions().apply {
                publishCameraTrack = true
                publishMicrophoneTrack = false
                autoSubscribeVideo = true
                autoSubscribeAudio = false
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            }
            val result = session.numericUid?.let { rtc.joinChannel(session.token, session.channelId, it.toInt(), options) }
                ?: rtc.joinChannelWithUserAccount(session.token, session.channelId, session.userId, options)
            if (result != 0) listener.onFailure("Không tham gia được kênh video ($result).")
        } catch (_: Exception) { listener.onFailure("Không khởi tạo được camera live.") }
    }

    override fun stop() {
        generation++
        val rtc = engine ?: return
        engine = null
        rtc.stopPreview()
        rtc.disableVideo()
        rtc.leaveChannel()
        rtc.setupLocalVideo(VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        // Never called on an Agora callback thread (the listener is delivered through main).
        RtcEngine.destroy()
    }
}
