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

    var captureSession: AVCaptureSession { session }

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

    private func configure(front: Bool) {
        session.beginConfiguration()
        session.sessionPreset = .high
        let position: AVCaptureDevice.Position = front ? .front : .back
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
struct RemoteImage: View {
    @EnvironmentObject private var language: AppLanguage
    let url: String
    let headers: [String: String]
    var height: CGFloat = 120
    var fit = false

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                if fit { Image(uiImage: image).resizable().scaledToFit() }
                else { Image(uiImage: image).resizable().scaledToFill() }
            } else {
                Color.white.opacity(0.08)
            }
        }
        .frame(height: height)
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .task(id: url) { image = nil; await load() }
    }

    private func load() async {
        guard image == nil, let target = URL(string: url) else { return }
        var request = URLRequest(url: target)
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        guard let (data, _) = try? await URLSession.shared.data(for: request),
              let decoded = UIImage(data: data) else { return }
        image = decoded
    }
}

/// Live preview for a `ShadowCamera`.
struct CameraPreview: UIViewRepresentable {
    let camera: ShadowCamera

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.layer.session = camera.captureSession
        view.layer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        // swiftlint:disable:next force_cast
        override var layer: AVCaptureVideoPreviewLayer { super.layer as! AVCaptureVideoPreviewLayer }
    }
}
