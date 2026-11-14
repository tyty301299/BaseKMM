import AVFoundation
import Foundation
import os

/// Full duplex PCM. Hardware format changes restart audio without closing the Live socket.
@MainActor
final class LiveAudioEngine: LiveAudioStreaming {
    private struct Playback {
        let id = UUID()
        let buffer: AVAudioPCMBuffer
    }
    private let log = Logger(subsystem: "com.trioxstudios.shadow", category: "LiveAudio")
    private var engine: AVAudioEngine?
    private var player: AVAudioPlayerNode?
    private var generation = 0
    private var playbackGeneration = 0
    private var queuedFrames: AVAudioFrameCount = 0
    private var playback: [Playback] = []
    private var observers: [NSObjectProtocol] = []
    private var onPCM: ((Data) -> Void)?
    private var onDrained: (() -> Void)?
    private var onFailure: (() -> Void)?
    private var tapInstalled = false
    private var recoveryTask: Task<Void, Never>?
    private var recoveryTimes: [TimeInterval] = []
    private var loggedInput = false
    private var loggedOutput = false
    private let playbackFormat = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 24000, channels: 1, interleaved: false)!

    func start(onPCM: @escaping (Data) -> Void, onDrained: @escaping () -> Void, onFailure: @escaping () -> Void) throws {
        stop()
        let epoch = generation
        self.onPCM = onPCM; self.onDrained = onDrained; self.onFailure = onFailure
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetooth])
            try session.setPreferredSampleRate(48000)
            try session.setPreferredIOBufferDuration(0.02)
            try session.setActive(true)
            let engine = AVAudioEngine()
            self.engine = engine
            try engine.inputNode.setVoiceProcessingEnabled(true)
            let player = AVAudioPlayerNode()
            self.player = player
            engine.attach(player)
            engine.connect(player, to: engine.mainMixerNode, format: playbackFormat)
            try installInputTap(engine, epoch: epoch)
            for name in [AVAudioSession.interruptionNotification, AVAudioSession.mediaServicesWereResetNotification, NSNotification.Name.AVAudioEngineConfigurationChange] {
                observers.append(NotificationCenter.default.addObserver(forName: name, object: name == .AVAudioEngineConfigurationChange ? engine : nil, queue: nil) { [weak self] notification in
                    let began = (notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt) == AVAudioSession.InterruptionType.began.rawValue
                    // Leave Apple's notification callback before touching the engine graph.
                    Task { @MainActor [weak self] in
                        guard let self, self.generation == epoch else { return }
                        if name == .AVAudioEngineConfigurationChange {
                            self.scheduleRecovery(reason: "hardware_format_changed", epoch: epoch)
                        } else if name == AVAudioSession.mediaServicesWereResetNotification {
                            self.log.error("audio_failed media_services_reset")
                            self.onFailure?()
                        } else if began {
                            self.log.info("audio_interrupted_by_system")
                            self.onFailure?()
                        }
                    }
                })
            }
            engine.prepare(); try engine.start(); player.play()
            log.info("audio_started input_rate=\(engine.inputNode.outputFormat(forBus: 0).sampleRate) output_rate=\(engine.outputNode.inputFormat(forBus: 0).sampleRate) volume=\(session.outputVolume)")
        } catch { stop(); throw error }
    }

    private func installInputTap(_ engine: AVAudioEngine, epoch: Int) throws {
        let input = engine.inputNode
        if tapInstalled { input.removeTap(onBus: 0); tapInstalled = false }
        let hardware = input.outputFormat(forBus: 0)
        guard hardware.sampleRate > 0, hardware.channelCount > 0,
              let target = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true),
              let converter = AVAudioConverter(from: hardware, to: target) else {
            throw LiveFailure(message: "Không mở được micro. Kiểm tra quyền truy cập và thử lại.")
        }
        // Converter and accumulator belong only to this tap; replacing it resets both.
        var pending = Data()
        input.installTap(onBus: 0, bufferSize: 2048, format: hardware) { [weak self] buffer, _ in
            guard buffer.frameLength > 0 else { return }
            let capacity = AVAudioFrameCount(ceil(Double(buffer.frameLength) * 16000 / hardware.sampleRate)) + 64
            guard let output = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: capacity) else { return }
            var supplied = false
            var conversionError: NSError?
            let status = converter.convert(to: output, error: &conversionError) { _, inputStatus in
                if supplied { inputStatus.pointee = .noDataNow; return nil }
                supplied = true; inputStatus.pointee = .haveData; return buffer
            }
            if status == .error || conversionError != nil {
                Task { @MainActor [weak self] in self?.scheduleRecovery(reason: "input_conversion_failed", epoch: epoch) }
                return
            }
            // A converter can need more input before producing its first samples.
            guard output.frameLength > 0, let samples = output.int16ChannelData?[0] else { return }
            pending.append(Data(bytes: samples, count: Int(output.frameLength) * 2))
            while pending.count >= 3200 {
                let packet = Data(pending.prefix(3200)); pending.removeFirst(3200)
                Task { @MainActor [weak self] in
                    guard let self, self.generation == epoch else { return }
                    if !self.loggedInput { self.loggedInput = true; self.log.info("microphone_streaming rate=16000") }
                    self.onPCM?(packet)
                }
            }
        }
        tapInstalled = true
    }

    private func scheduleRecovery(reason: String, epoch: Int) {
        guard generation == epoch, engine != nil, recoveryTask == nil else { return }
        let now = ProcessInfo.processInfo.systemUptime
        recoveryTimes.removeAll { now - $0 > 10 }
        guard recoveryTimes.count < 3 else {
            log.error("audio_recovery_exhausted reason=\(reason, privacy: .public)")
            onFailure?(); return
        }
        recoveryTimes.append(now)
        // Ignore completion callbacks caused by the system stopping the player. Keep the
        // unplayed buffers so a route change does not discard the assistant's whole reply.
        playbackGeneration += 1
        log.info("audio_recovering reason=\(reason, privacy: .public)")
        recoveryTask = Task { [weak self] in
            do { try await Task.sleep(nanoseconds: 200_000_000) } catch { return }
            guard let self, self.generation == epoch, let engine = self.engine, let player = self.player else { return }
            self.recoveryTask = nil
            do {
                player.stop(); engine.stop()
                try self.installInputTap(engine, epoch: epoch)
                let outputFormat = engine.outputNode.inputFormat(forBus: 0)
                guard outputFormat.sampleRate > 0, outputFormat.channelCount > 0 else {
                    throw LiveFailure(message: "Chưa mở được loa của thiết bị.")
                }
                engine.disconnectNodeOutput(engine.mainMixerNode)
                engine.connect(engine.mainMixerNode, to: engine.outputNode, format: outputFormat)
                engine.prepare(); try engine.start()
                for item in self.playback { self.schedule(item) }
                player.play()
                self.log.info("audio_recovered input_rate=\(engine.inputNode.outputFormat(forBus: 0).sampleRate) output_rate=\(outputFormat.sampleRate) queued_frames=\(self.queuedFrames)")
            } catch {
                self.log.error("audio_restart_failed code=\((error as NSError).code)")
                self.scheduleRecovery(reason: "restart_failed", epoch: epoch)
            }
        }
    }

    func play(_ pcm: Data, rate: Double) throws {
        guard engine != nil, player != nil, rate == 24000, pcm.count % 2 == 0 else {
            throw LiveFailure(message: "Chưa phát được âm thanh. Bấm micro để kết nối lại.")
        }
        let frames = AVAudioFrameCount(pcm.count / 2)
        guard frames > 0 else { return }
        guard queuedFrames + frames <= 720000,
              let buffer = AVAudioPCMBuffer(pcmFormat: playbackFormat, frameCapacity: frames),
              let channel = buffer.floatChannelData?[0] else {
            throw LiveFailure(message: "Âm thanh đang bị trễ. Bạn thử mở lại micro nhé.")
        }
        buffer.frameLength = frames
        pcm.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            for index in 0..<Int(frames) {
                let word = UInt16(bytes[index * 2]) | (UInt16(bytes[index * 2 + 1]) << 8)
                channel[index] = Float(Int16(bitPattern: word)) / 32768
            }
        }
        let item = Playback(buffer: buffer)
        playback.append(item); queuedFrames += frames
        if !loggedOutput { loggedOutput = true; log.info("assistant_audio_received rate=24000 volume=\(AVAudioSession.sharedInstance().outputVolume)") }
        if recoveryTask == nil && engine?.isRunning == true {
            schedule(item)
            if player?.isPlaying == false { player?.play() }
        } else {
            scheduleRecovery(reason: "output_waiting_for_engine", epoch: generation)
        }
    }

    private func schedule(_ item: Playback) {
        let epoch = generation, playbackEpoch = playbackGeneration
        player?.scheduleBuffer(item.buffer, completionCallbackType: .dataPlayedBack) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.generation == epoch, self.playbackGeneration == playbackEpoch,
                      let index = self.playback.firstIndex(where: { $0.id == item.id }) else { return }
                self.playback.remove(at: index)
                self.queuedFrames -= min(self.queuedFrames, item.buffer.frameLength)
                if self.queuedFrames == 0 { self.log.debug("assistant_audio_drained"); self.onDrained?() }
            }
        }
    }

    func interrupt() {
        playbackGeneration += 1; queuedFrames = 0; playback.removeAll()
        player?.stop()
        if recoveryTask == nil && engine?.isRunning == true { player?.play() }
    }

    func stop() {
        let hadEngine = engine != nil
        generation += 1; playbackGeneration += 1; queuedFrames = 0; playback.removeAll()
        recoveryTask?.cancel(); recoveryTask = nil; recoveryTimes.removeAll()
        observers.forEach(NotificationCenter.default.removeObserver); observers.removeAll()
        player?.stop()
        if let engine { if tapInstalled { engine.inputNode.removeTap(onBus: 0) }; engine.stop() }
        tapInstalled = false; loggedInput = false; loggedOutput = false
        player = nil; engine = nil; onPCM = nil; onDrained = nil; onFailure = nil
        if hadEngine { try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) }
    }
}
