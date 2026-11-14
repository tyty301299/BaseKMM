import AVFoundation
import Foundation

/// Full duplex PCM. Voice-processing I/O cancels playback echo while the mic stays open.
@MainActor
final class LiveAudioEngine: LiveAudioStreaming {
    private var engine: AVAudioEngine?
    private var player: AVAudioPlayerNode?
    private var generation = 0
    private var playbackGeneration = 0
    private var queuedFrames: AVAudioFrameCount = 0
    private var observers: [NSObjectProtocol] = []
    private var onDrained: (() -> Void)?
    private var tapInstalled = false
    private var configurationRecoveries = 0
    private var recoveryTask: Task<Void, Never>?
    private var pendingPlayback: [(Data, Double)] = []
    private let playbackFormat = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 24000, channels: 1, interleaved: false)!

    func start(onPCM: @escaping (Data) -> Void, onDrained: @escaping () -> Void, onFailure: @escaping () -> Void) throws {
        recoveryTask?.cancel(); recoveryTask = nil; pendingPlayback.removeAll()
        configurationRecoveries = 0
        try configure(onPCM: onPCM, onDrained: onDrained, onFailure: onFailure)
    }

    private func configure(onPCM: @escaping (Data) -> Void, onDrained: @escaping () -> Void, onFailure: @escaping () -> Void) throws {
        release(deactivateSession: false)
        let epoch = generation
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetooth])
        try session.setPreferredIOBufferDuration(0.02)
        try session.setActive(true)
        let engine = AVAudioEngine()
        self.engine = engine
        self.onDrained = onDrained
        do {
            let input = engine.inputNode
            try input.setVoiceProcessingEnabled(true)
            let hardware = input.outputFormat(forBus: 0)
            guard hardware.sampleRate > 0, hardware.channelCount > 0,
                  let target = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true),
                  let converter = AVAudioConverter(from: hardware, to: target) else {
                throw LiveFailure(message: "Không mở được micro. Kiểm tra quyền truy cập và thử lại.")
            }
            let player = AVAudioPlayerNode()
            self.player = player
            engine.attach(player)
            engine.connect(player, to: engine.mainMixerNode, format: playbackFormat)
            // Converter state and the packet accumulator are owned by the audio tap callback.
            var pending = Data()
            input.installTap(onBus: 0, bufferSize: 2048, format: hardware) { [weak self] buffer, _ in
                let capacity = AVAudioFrameCount(ceil(Double(buffer.frameLength) * 16000 / hardware.sampleRate)) + 64
                guard let output = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: capacity) else { return }
                var supplied = false
                var conversionError: NSError?
                let status = converter.convert(to: output, error: &conversionError) { _, inputStatus in
                    if supplied { inputStatus.pointee = .noDataNow; return nil }
                    supplied = true; inputStatus.pointee = .haveData; return buffer
                }
                guard status != .error, conversionError == nil, let samples = output.int16ChannelData?[0] else {
                    print("[LiveAudio] Microphone conversion failed: status=\(status.rawValue), error=\(conversionError?.localizedDescription ?? "none"), frames=\(output.frameLength)")
                    Task { @MainActor [weak self] in if self?.generation == epoch { onFailure() } }
                    return
                }
                pending.append(Data(bytes: samples, count: Int(output.frameLength) * 2))
                // 100 ms packets, not speech recognition: pauses and barge-in reach the server VAD.
                while pending.count >= 3200 {
                    let packet = Data(pending.prefix(3200)); pending.removeFirst(3200)
                    Task { @MainActor [weak self] in
                        guard self?.generation == epoch else { return }
                        onPCM(packet)
                    }
                }
            }
            tapInstalled = true
            engine.prepare(); try engine.start(); player.play()
            for name in [AVAudioSession.interruptionNotification, AVAudioSession.mediaServicesWereResetNotification, NSNotification.Name.AVAudioEngineConfigurationChange] {
                observers.append(NotificationCenter.default.addObserver(forName: name, object: name == .AVAudioEngineConfigurationChange ? engine : nil, queue: .main) { [weak self] notification in
                    if name == AVAudioSession.interruptionNotification,
                       (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt) != AVAudioSession.InterruptionType.began.rawValue { return }
                    Task { @MainActor [weak self] in
                        guard let self, self.generation == epoch else { return }
                        let engine = self.engine
                        if name == .AVAudioEngineConfigurationChange, let engine {
                            let current = engine.inputNode.outputFormat(forBus: 0)
                            if current.sampleRate == hardware.sampleRate && current.channelCount == hardware.channelCount &&
                                current.commonFormat == hardware.commonFormat && current.isInterleaved == hardware.isInterleaved {
                                do {
                                    if !engine.isRunning {
                                        engine.prepare()
                                        try engine.start()
                                        self.player?.play()
                                        print("[LiveAudio] Restarted existing engine; input format unchanged")
                                    }
                                    return
                                } catch {
                                    print("[LiveAudio] Engine restart failed: \(error.localizedDescription)")
                                }
                            }
                        }
                        if name == .AVAudioEngineConfigurationChange, self.configurationRecoveries < 2 {
                            guard self.recoveryTask == nil else { return }
                            self.configurationRecoveries += 1
                            print("[LiveAudio] Rebuilding audio after configuration change (attempt \(self.configurationRecoveries))")
                            self.recoveryTask = Task { @MainActor [weak self] in
                                do { try await Task.sleep(nanoseconds: 500_000_000) } catch { return }
                                guard let self, self.generation == epoch else { return }
                                do {
                                    try self.configure(onPCM: onPCM, onDrained: onDrained, onFailure: onFailure)
                                    self.recoveryTask = nil
                                    let pending = self.pendingPlayback
                                    self.pendingPlayback.removeAll()
                                    for (data, rate) in pending { try self.play(data, rate: rate) }
                                    if pending.isEmpty { onDrained() }
                                    print("[LiveAudio] Audio recovered; Live connection preserved")
                                } catch {
                                    self.recoveryTask = nil; self.pendingPlayback.removeAll()
                                    print("[LiveAudio] Recovery failed: \(error.localizedDescription)")
                                    onFailure()
                                }
                            }
                            return
                        }
                        print("[LiveAudio] Audio failure notification: \(name.rawValue), engineRunning=\(engine?.isRunning == true)")
                        onFailure()
                    }
                })
            }
        } catch { stop(); throw error }
    }

    func play(_ pcm: Data, rate: Double) throws {
        if recoveryTask != nil {
            guard rate == 24000, pcm.count % 2 == 0,
                  pendingPlayback.reduce(0, { $0 + $1.0.count }) + pcm.count <= 1_440_000 else {
                throw LiveFailure(message: "Âm thanh đang bị trễ. Bạn thử mở lại micro nhé.")
            }
            pendingPlayback.append((pcm, rate)); return
        }
        guard let player, engine?.isRunning == true, rate == 24000, pcm.count % 2 == 0 else {
            throw LiveFailure(message: "Chưa phát được âm thanh. Bấm micro để kết nối lại.")
        }
        let frames = AVAudioFrameCount(pcm.count / 2)
        guard frames > 0 else { return }
        // Bound playback memory to 30 seconds. The mic remains active during playback.
        guard queuedFrames + frames <= 720000,
              let buffer = AVAudioPCMBuffer(pcmFormat: playbackFormat, frameCapacity: frames),
              let channel = buffer.floatChannelData?[0] else { throw LiveFailure(message: "Âm thanh đang bị trễ. Bạn thử mở lại micro nhé.") }
        buffer.frameLength = frames
        pcm.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            for index in 0..<Int(frames) {
                let word = UInt16(bytes[index * 2]) | (UInt16(bytes[index * 2 + 1]) << 8)
                channel[index] = Float(Int16(bitPattern: word)) / 32768
            }
        }
        queuedFrames += frames
        let epoch = generation, playback = playbackGeneration
        player.scheduleBuffer(buffer, completionCallbackType: .dataPlayedBack) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.generation == epoch, self.playbackGeneration == playback else { return }
                self.queuedFrames -= min(self.queuedFrames, frames)
                if self.queuedFrames == 0 { self.onDrained?() }
            }
        }
        if !player.isPlaying { player.play() }
    }

    func interrupt() {
        pendingPlayback.removeAll()
        playbackGeneration += 1; queuedFrames = 0
        player?.stop()
        if engine?.isRunning == true { player?.play() }
    }

    func stop() {
        recoveryTask?.cancel(); recoveryTask = nil; pendingPlayback.removeAll()
        release(deactivateSession: true)
    }

    private func release(deactivateSession: Bool) {
        generation += 1; playbackGeneration += 1; queuedFrames = 0
        observers.forEach(NotificationCenter.default.removeObserver); observers.removeAll()
        player?.stop()
        if let engine { if tapInstalled { engine.inputNode.removeTap(onBus: 0) }; engine.stop() }
        tapInstalled = false
        player = nil; engine = nil; onDrained = nil
        if deactivateSession {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
    }
}
