import Shared
import UIKit

/// Native provider selection. Feature models receive contracts instead of TTS SDK objects.
enum NativeServices {
    @MainActor static func makeLiveSession() -> NativeLiveSession { NativeLiveSession(audio: LiveAudioEngine()) }
    static func makeLiveVideoEngine() -> NativeLiveVideoEngine { AgoraLiveVideoEngine() }
    static func makeSpeaker() -> Speaker { ShadowSpeaker() }
    static func makeCamera() -> ShadowCamera { ShadowCamera() }
    static func makeListener(elapsedSeconds: @escaping () -> Double,
                             onSentence: @escaping (Double, Double, String) -> Void,
                             onPartial: @escaping (String) -> Void,
                             onUnavailable: @escaping () -> Void) -> AuthorizedSpeechListener {
        ShadowListener(elapsedSeconds: elapsedSeconds, onSentence: onSentence,
                       onPartial: onPartial, onUnavailable: onUnavailable)
    }
}
