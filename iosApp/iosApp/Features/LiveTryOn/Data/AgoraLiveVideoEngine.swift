import UIKit
import AgoraRtcKit
import Shared

/// Platform video adapter. Session policy and network orchestration live in the shared store.
final class AgoraLiveVideoEngine: NSObject, NativeLiveVideoEngine, AgoraRtcEngineDelegate {
    let localView = UIView()
    let remoteView = UIView()
    private var engine: AgoraRtcEngineKit?
    private var listener: LiveVideoListener?
    private var remoteUid: UInt?
    private var remoteAccount = ""
    private var generation = 0

    func start(session: LiveTryOnSession, listener: LiveVideoListener) {
        stop()
        self.listener = listener
        remoteUid = UInt(session.renderUserId)
        remoteAccount = session.renderUserId
        let config = AgoraRtcEngineConfig()
        config.appId = session.appId
        config.channelProfile = .communication
        let rtc = AgoraRtcEngineKit.sharedEngine(with: config, delegate: self)
        engine = rtc
        rtc.disableAudio()
        rtc.enableVideo()
        let camera = AgoraCameraCapturerConfiguration()
        camera.cameraDirection = .front
        rtc.setCameraCapturerConfiguration(camera)
        rtc.setVideoEncoderConfiguration(AgoraVideoEncoderConfiguration(size: CGSize(width: 720, height: 1280),
            frameRate: .fps30, bitrate: AgoraVideoBitrateStandard, orientationMode: .fixedPortrait, mirrorMode: .disabled))
        let canvas = AgoraRtcVideoCanvas()
        canvas.view = localView
        canvas.renderMode = .hidden
        rtc.setupLocalVideo(canvas)
        rtc.startPreview()
        let options = AgoraRtcChannelMediaOptions()
        options.publishCameraTrack = true
        options.publishMicrophoneTrack = false
        options.autoSubscribeVideo = true
        options.autoSubscribeAudio = false
        let result: Int32
        if let uid = session.numericUid {
            result = rtc.joinChannel(byToken: session.token, channelId: session.channelId,
                uid: uid.uintValue, mediaOptions: options, joinSuccess: nil)
        } else {
            result = rtc.joinChannel(byToken: session.token, channelId: session.channelId,
                userAccount: session.userId, mediaOptions: options, joinSuccess: nil)
        }
        if result != 0 { listener.onFailure(message: "Không tham gia được kênh video (\(result)).") }
    }

    func stop() {
        generation += 1
        listener = nil
        remoteUid = nil
        remoteAccount = ""
        guard let rtc = engine else { return }
        engine = nil
        rtc.stopPreview()
        rtc.disableVideo()
        rtc.leaveChannel(nil)
        rtc.setupLocalVideo(nil)
        rtc.delegate = nil
        // Store calls this on main, outside the SDK callback stack.
        AgoraRtcEngineKit.destroy()
    }

    private func deliver(_ rtc: AgoraRtcEngineKit, _ action: @escaping (AgoraLiveVideoEngine) -> Void) {
        let epoch = generation
        DispatchQueue.main.async { [weak self] in
            guard let self, self.engine === rtc, self.generation == epoch else { return }
            action(self)
        }
    }

    private func bindRemote(_ uid: UInt) {
        let canvas = AgoraRtcVideoCanvas()
        canvas.uid = uid
        canvas.view = remoteView
        canvas.renderMode = .hidden
        engine?.setupRemoteVideo(canvas)
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        deliver(engine) { if $0.remoteUid == uid { $0.bindRemote(uid) } }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didUserInfoUpdatedWithUserId uid: UInt, userInfo: AgoraUserInfo) {
        deliver(engine) {
            if userInfo.userAccount == $0.remoteAccount { $0.remoteUid = uid; $0.bindRemote(uid) }
        }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, firstRemoteVideoFrameOfUid uid: UInt, size: CGSize, elapsed: Int) {
        deliver(engine) { if $0.remoteUid == uid { $0.listener?.onRemoteVideo() } }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, remoteVideoStats stats: AgoraRtcRemoteVideoStats) {
        deliver(engine) { if $0.remoteUid == stats.uid { $0.listener?.onStats(framesPerSecond: Int32(stats.rendererOutputFrameRate)) } }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didOfflineOfUid uid: UInt, reason: AgoraUserOfflineReason) {
        deliver(engine) { if $0.remoteUid == uid { $0.listener?.onFailure(message: "Vidu đã dừng video thử đồ.") } }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didOccurError errorCode: AgoraErrorCode) {
        deliver(engine) { $0.listener?.onFailure(message: "Lỗi video Agora (\(errorCode.rawValue)). Hãy mở phiên mới.") }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, tokenPrivilegeWillExpire token: String) {
        deliver(engine) { $0.listener?.onFailure(message: "Phiên video sắp hết hạn. Hãy mở phiên mới.") }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, connectionChangedTo state: AgoraConnectionState, reason: AgoraConnectionChangedReason) {
        deliver(engine) {
            if state == .failed || state == .disconnected { $0.listener?.onFailure(message: "Mất kết nối video. Hãy mở phiên mới.") }
        }
    }
}
