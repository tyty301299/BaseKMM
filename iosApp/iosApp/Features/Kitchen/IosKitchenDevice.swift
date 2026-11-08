import AVFoundation
import CoreImage
import Shared
import UIKit

/// Camera, microphone and speaker for KitchenStore.
final class IosKitchenDevice: NSObject, KitchenDevice, AVCaptureVideoDataOutputSampleBufferDelegate {
    let captureSession = AVCaptureSession()

    private let cameraQueue = DispatchQueue(label: "kitchen.camera")
    private let ciContext = CIContext()
    private let frameLock = NSLock()
    private var latestFrame: String?
    private var lastFrameAt = Date.distantPast

    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let speakerFormat = AVAudioFormat(
        commonFormat: .pcmFormatFloat32, sampleRate: 24_000, channels: 1, interleaved: false
    )!
    private let micFormat = AVAudioFormat(
        commonFormat: .pcmFormatInt16, sampleRate: 16_000, channels: 1, interleaved: true
    )!
    private var micRunning = false

    func startCamera() {
        #if targetEnvironment(simulator)
        publishSimulatorFrame()
        #endif
        cameraQueue.async { [self] in
            if captureSession.inputs.isEmpty { configureCamera() }
            if !captureSession.isRunning { captureSession.startRunning() }
        }
    }

    #if targetEnvironment(simulator)
    // The Simulator has no camera. A blank frame lets the flow run against the mock server.
    private func publishSimulatorFrame() {
        let bounds = CGRect(x: 0, y: 0, width: 640, height: 480)
        let jpeg = UIGraphicsImageRenderer(bounds: bounds).jpegData(withCompressionQuality: 0.7) { context in
            UIColor.systemGray4.setFill()
            context.fill(bounds)
        }
        frameLock.lock()
        latestFrame = jpeg.base64EncodedString()
        frameLock.unlock()
    }
    #endif

    func stopCamera() {
        cameraQueue.async { [self] in
            if captureSession.isRunning { captureSession.stopRunning() }
        }
    }

    // Without a camera no frames arrive and the store reports AppError.camera.
    private func configureCamera() {
        guard
            let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
            let input = try? AVCaptureDeviceInput(device: camera)
        else { return }
        captureSession.beginConfiguration()
        captureSession.sessionPreset = .hd1280x720
        let output = AVCaptureVideoDataOutput()
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(self, queue: cameraQueue)
        if captureSession.canAddInput(input) { captureSession.addInput(input) }
        if captureSession.canAddOutput(output) { captureSession.addOutput(output) }
        output.connection(with: .video)?.videoOrientation = .portrait
        captureSession.commitConfiguration()
    }

    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard Date().timeIntervalSince(lastFrameAt) >= 0.5,
              let pixels = CMSampleBufferGetImageBuffer(sampleBuffer),
              let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
              let jpeg = ciContext.jpegRepresentation(
                  of: CIImage(cvPixelBuffer: pixels),
                  colorSpace: colorSpace,
                  options: [kCGImageDestinationLossyCompressionQuality as CIImageRepresentationOption: 0.7]
              )
        else { return }
        lastFrameAt = Date()
        let encoded = jpeg.base64EncodedString()
        frameLock.lock()
        latestFrame = encoded
        frameLock.unlock()
    }

    func latestFrameJpegBase64() -> String? {
        frameLock.lock()
        defer { frameLock.unlock() }
        return latestFrame
    }

    func startMicrophone(onChunk: @escaping (String) -> Void) {
        guard !micRunning else { return }
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker])
            try audioSession.setActive(true)
            try engine.inputNode.setVoiceProcessingEnabled(true) // Echo cancellation.

            engine.attach(player)
            engine.connect(player, to: engine.mainMixerNode, format: speakerFormat)

            let inputFormat = engine.inputNode.outputFormat(forBus: 0)
            guard let converter = AVAudioConverter(from: inputFormat, to: micFormat) else { return }
            let target = micFormat
            engine.inputNode.installTap(onBus: 0, bufferSize: 2_048, format: inputFormat) { buffer, _ in
                if let encoded = Self.convert(buffer, with: converter, to: target) { onChunk(encoded) }
            }
            try engine.start()
            player.play()
            micRunning = true
        } catch {
            NSLog("KitchenDevice: microphone failed: \(error)")
            engine.inputNode.removeTap(onBus: 0)
        }
    }

    private static func convert(
        _ buffer: AVAudioPCMBuffer,
        with converter: AVAudioConverter,
        to format: AVAudioFormat
    ) -> String? {
        let ratio = format.sampleRate / buffer.format.sampleRate
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 16
        guard let output = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: capacity) else { return nil }
        var consumed = false
        var error: NSError?
        converter.convert(to: output, error: &error) { _, status in
            if consumed {
                status.pointee = .noDataNow
                return nil
            }
            consumed = true
            status.pointee = .haveData
            return buffer
        }
        guard error == nil, output.frameLength > 0, let samples = output.int16ChannelData else { return nil }
        return Data(bytes: samples[0], count: Int(output.frameLength) * 2).base64EncodedString()
    }

    func stopMicrophone() {
        guard micRunning else { return }
        micRunning = false
        engine.inputNode.removeTap(onBus: 0)
        player.stop()
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func playAudio(pcmBase64: String) {
        guard micRunning, let data = Data(base64Encoded: pcmBase64) else { return }
        let frames = AVAudioFrameCount(data.count / 2)
        guard frames > 0,
              let buffer = AVAudioPCMBuffer(pcmFormat: speakerFormat, frameCapacity: frames),
              let channel = buffer.floatChannelData?[0]
        else { return }
        buffer.frameLength = frames
        data.withUnsafeBytes { raw in
            let samples = raw.bindMemory(to: Int16.self)
            for index in 0..<Int(frames) {
                channel[index] = Float(Int16(littleEndian: samples[index])) / 32_768
            }
        }
        player.scheduleBuffer(buffer, completionHandler: nil)
    }

    func stopPlayback() {
        guard micRunning else { return }
        player.stop()
        player.play()
    }

    func shutdown() {
        stopMicrophone()
        stopCamera()
    }
}
