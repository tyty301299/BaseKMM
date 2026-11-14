import AVFoundation
import SwiftUI
import UIKit

/// Holds the newest camera frame so the guide loop can encode one on demand. The capture session
/// delivers frames continuously; only the requested ones are ever compressed.
final class ShadowCamera: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let session = AVCaptureSession()
    private let output = AVCaptureVideoDataOutput()
    private let queue = DispatchQueue(label: "shadow.camera")
    private let lock = NSLock()
    private var latest: CVPixelBuffer?
    private var configured = false
    private var frameHandler: ((CVPixelBuffer, Bool) -> Void)?
    private var isFront = false

    var captureSession: AVCaptureSession { session }

    var frameSize: CGSize? {
        lock.lock()
        defer { lock.unlock() }
        guard let latest else { return nil }
        return CGSize(width: CVPixelBufferGetWidth(latest), height: CVPixelBufferGetHeight(latest))
    }

    func start(front: Bool = false) {
        queue.async { [weak self] in
            guard let self else { return }
            if !self.configured { self.configure(front: front) }
            if !self.session.isRunning { self.session.startRunning() }
        }
    }

    func stop() {
        queue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
        }
    }

    func setFrameHandler(_ handler: ((CVPixelBuffer, Bool) -> Void)?) {
        queue.async { [weak self] in self?.frameHandler = handler }
    }

    func flip(front: Bool) {
        queue.async { [weak self] in
            guard let self else { return }
            let position: AVCaptureDevice.Position = front ? .front : .back
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
                  let input = try? AVCaptureDeviceInput(device: device) else { return }
            self.session.beginConfiguration()
            let old = self.session.inputs
            old.forEach { self.session.removeInput($0) }
            if self.session.canAddInput(input) { self.session.addInput(input) }
            else { old.forEach { if self.session.canAddInput($0) { self.session.addInput($0) } } }
            self.session.commitConfiguration()
            self.isFront = (self.session.inputs.first as? AVCaptureDeviceInput)?.device.position == .front
        }
    }

    func focus(at point: CGPoint) {
        queue.async { [weak self] in
            guard let device = (self?.session.inputs.first as? AVCaptureDeviceInput)?.device,
                  (try? device.lockForConfiguration()) != nil else { return }
            if device.isFocusPointOfInterestSupported && device.isFocusModeSupported(.autoFocus) {
                device.focusPointOfInterest = point; device.focusMode = .autoFocus
            }
            if device.isExposurePointOfInterestSupported && device.isExposureModeSupported(.autoExpose) {
                device.exposurePointOfInterest = point; device.exposureMode = .autoExpose
            }
            device.unlockForConfiguration()
        }
    }

    func setTorch(_ enabled: Bool) {
        queue.async { [weak self] in
            guard let device = (self?.session.inputs.first as? AVCaptureDeviceInput)?.device,
                  device.hasTorch, (try? device.lockForConfiguration()) != nil else { return }
            device.torchMode = enabled ? .on : .off
            device.unlockForConfiguration()
        }
    }

    private func configure(front: Bool) {
        session.beginConfiguration()
        // This capture session only uses video. LiveAudioEngine owns the shared audio session.
        session.automaticallyConfiguresApplicationAudioSession = false
        session.sessionPreset = .high
        let position: AVCaptureDevice.Position = front ? .front : .back
        isFront = front
        if let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
           let input = try? AVCaptureDeviceInput(device: device),
           session.canAddInput(input) {
            session.addInput(input)
        }
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(self, queue: queue)
        if session.canAddOutput(output) { session.addOutput(output) }
        session.commitConfiguration()
        configured = true
    }

    func captureOutput(_ output: AVCaptureOutput,
                       didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        guard let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lock.lock()
        latest = buffer
        lock.unlock()
        frameHandler?(buffer, isFront)
    }

    /// Returns the newest frame as a `data:image/jpeg;base64,...` URL, downscaled to `maxSide`.
    func capture(maxSide: Int) -> String? {
        lock.lock()
        let buffer = latest
        lock.unlock()
        guard let buffer else { return nil }

        let image = CIImage(cvPixelBuffer: buffer)
        let context = CIContext()
        guard let cgImage = context.createCGImage(image, from: image.extent) else { return nil }
        // The sensor is landscape while the phone is held upright, so rotate to match what the user sees.
        let upright = UIImage(cgImage: cgImage, scale: 1, orientation: .right)
        guard let scaled = upright.scaled(maxSide: CGFloat(maxSide)),
              let data = scaled.jpegData(compressionQuality: 0.7) else { return nil }
        return "data:image/jpeg;base64," + data.base64EncodedString()
    }

    static func requestAccess(_ completion: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: completion(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { completion(granted) }
            }
        default: completion(false)
        }
    }
}

private extension UIImage {
    func scaled(maxSide: CGFloat) -> UIImage? {
        let longest = max(size.width, size.height)
        guard longest > maxSide else { return self }
        let ratio = maxSide / longest
        let target = CGSize(width: size.width * ratio, height: size.height * ratio)
        let renderer = UIGraphicsImageRenderer(size: target)
        return renderer.image { _ in draw(in: CGRect(origin: .zero, size: target)) }
    }
}

/// Loads an image that sits behind the access gate. `AsyncImage` cannot send headers, so the
/// request is made directly with the headers the repository supplies.
