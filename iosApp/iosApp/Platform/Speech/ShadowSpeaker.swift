import AVFoundation
import Shared

/// Vietnamese text-to-speech. A new line cancels whatever is still being read, which is what the
/// guide's barge-in logic expects.
final class ShadowSpeaker: NSObject, Speaker {
    private let synthesizer = AVSpeechSynthesizer()
    private let voice = AVSpeechSynthesisVoice(language: "vi-VN")

    var available: Bool { voice != nil }

    override init() {
        super.init()
        // Playback category so the voice is audible even with the ringer switch silenced.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    func speak(text: String) {
        guard !text.isEmpty, available else { return }
        // Pronounce the brand as two Vietnamese syllables; retain SKI in the UI.
        let spoken = text.replacingOccurrences(of: #"\bSKI\b"#, with: "sờ ki", options: .regularExpression)
        let utterance = AVSpeechUtterance(string: spoken)
        utterance.voice = voice
        synthesizer.speak(utterance)
    }

    func stop() {
        if synthesizer.isSpeaking { synthesizer.stopSpeaking(at: .immediate) }
    }
    func shutdown() { stop() }
}
