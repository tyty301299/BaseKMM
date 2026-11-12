import AVFoundation
import Speech
import Shared

protocol AuthorizedSpeechListener: SpeechListener {
    func requestAccess(_ completion: @escaping (Bool) -> Void)
}

/// Continuous Vietnamese dictation for the teach flow, timestamped against the recording clock so
/// the compiler can line sentences up with keyframes.
final class ShadowListener: NSObject, AuthorizedSpeechListener {
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "vi-VN"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var utteranceStart: Double = 0
    private var lastFinal = ""
    private var generation = 0

    private let elapsedSeconds: () -> Double
    private let onSentence: (Double, Double, String) -> Void
    private let onPartial: (String) -> Void
    private let onUnavailable: () -> Void

    init(elapsedSeconds: @escaping () -> Double,
         onSentence: @escaping (Double, Double, String) -> Void,
         onPartial: @escaping (String) -> Void,
         onUnavailable: @escaping () -> Void) {
        self.elapsedSeconds = elapsedSeconds
        self.onSentence = onSentence
        self.onPartial = onPartial
        self.onUnavailable = onUnavailable
        super.init()
    }

    var available: Bool { recognizer?.isAvailable == true }

    func requestAccess(_ completion: @escaping (Bool) -> Void) {
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async { completion(status == .authorized) }
        }
    }

    func start() {
        guard let recognizer, recognizer.isAvailable else { return onUnavailable() }
        stop()
        stopped = false
        let epoch = generation
        // Dictation needs record permission on the session; keep playback so TTS still works.
        try? AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .spokenAudio,
                                                        options: [.defaultToSpeaker, .duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        self.request = request
        utteranceStart = elapsedSeconds()
        lastFinal = ""

        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
        }
        engine.prepare()
        do {
            try engine.start()
        } catch {
            onUnavailable()
            return
        }

        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            DispatchQueue.main.async { [weak self] in
                guard let self, !self.stopped, epoch == self.generation else { return }
                if let result {
                    let text = result.bestTranscription.formattedString
                    if result.isFinal {
                        self.emit(text)
                        // The task ends with each utterance, so listen again for the next sentence.
                        self.restart()
                    } else {
                        self.onPartial(text)
                    }
                }
                if error != nil {
                    self.restart()
                }
            }
        }
    }

    private func emit(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed != lastFinal else { return }
        lastFinal = trimmed
        onSentence(utteranceStart, elapsedSeconds(), trimmed)
    }

    private var stopped = false

    private func restart() {
        guard !stopped else { return }
        let epoch = generation
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.stopped, self.generation == epoch else { return }
            self.start()
        }
    }

    func stop() {
        stopped = true
        generation += 1
        request?.endAudio()
        task?.cancel()
        task = nil
        request = nil
        if engine.isRunning {
            engine.inputNode.removeTap(onBus: 0)
            engine.stop()
        }
    }

    func shutdown() {
        stopped = true
        stop()
    }
}
