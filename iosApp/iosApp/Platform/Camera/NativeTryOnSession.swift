import SwiftUI
import CoreImage
import CoreMedia
import OSLog
import XmaxSDK

/// A video-only fitting session. Gemini Live keeps ownership of microphone and speaker.
@MainActor
final class NativeTryOnSession: ObservableObject {
    @Published private(set) var status = "idle"
    @Published private(set) var message = ""
    @Published private(set) var track: RealtimeVideoTrack?
    @Published var showsOriginal = false
    private(set) var itemIDs: [String] = []
    var onChange: (() -> Void)?
    var notify: (([String: Any]) -> Void)?
    private var manager: (any XmaxRealtimeManaging)?
    private var source: FittingCameraSource?
    private var task: Task<Void, Never>?
    private var cleanup: Task<Void, Never>?
    private var deadline: Task<Void, Never>?
    private var expiry: Task<Void, Never>?
    private var generation = UUID()
    private var token: String?
    private var origin: URL?
    private var headers: [String: String] = [:]
    private var receivedFrame = false
    private let log = Logger(subsystem: "com.trioxstudios.shadow", category: "NativeTryOn")
    var active: Bool { status == "loading" || status == "live" }
    var execution: [String: Any] { ["mode": "tryon", "status": status, "message": message, "selected_ids": itemIDs] }

    @discardableResult
    func start(ids: [String], camera: ShadowCamera, origin: URL, headers: [String: String]) -> [String: Any] {
        let ids = Array(Set(ids)).sorted()
        guard !ids.isEmpty, ids.count <= 3 else {
            return ["mode": "tryon", "status": "selecting", "message": "Chọn áo và quần bạn muốn thử trong tủ đồ."]
        }
        if active && ids == itemIDs { showsOriginal = false; return execution }
        stop()
        let epoch = generation
        self.origin = origin; self.headers = headers; itemIDs = ids; showsOriginal = false
        update("loading", "Chúng tôi đang thay đồ…")
        let previousCleanup = cleanup
        // One bounded attempt. A new outfit waits for the old Agora engine to close.
        task = Task { [weak self] in
            await previousCleanup?.value
            guard let self, self.generation == epoch, !Task.isCancelled else { return }
            do { try await self.connect(ids: ids, camera: camera, origin: origin, headers: headers, epoch: epoch) }
            catch is CancellationError { }
            catch {
                guard self.generation == epoch else { return }
                let code = (error as? XmaxError)?.code.rawValue ?? "transport"
                self.log.error("fitting_failure_code=\(code, privacy: .public)")
                self.fail(error is FittingError ? (error as! FittingError).message : "Kết nối thay đồ bị gián đoạn. Bạn bấm Thử đồ để thử lại nhé.")
            }
        }
        deadline = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 60_000_000_000)
            guard !Task.isCancelled, let self, self.generation == epoch, self.status == "loading" else { return }
            self.fail("Chưa nhận được video thay đồ. Bạn kiểm tra mạng rồi thử lại nhé.")
        }
        return execution
    }

    private func connect(ids: [String], camera: ShadowCamera, origin: URL, headers: [String: String], epoch: UUID) async throws {
        var request = URLRequest(url: origin.appendingPathComponent("api/tryon/xmax/session"), timeoutInterval: 30)
        request.httpMethod = "POST"; request.httpBody = try JSONSerialization.data(withJSONObject: ["item_ids": ids])
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        let (data, response) = try await URLSession.shared.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard code == 200, let descriptor = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let token = descriptor["token"] as? String, token.hasPrefix("tk-"),
              let prompt = descriptor["prompt"] as? String,
              let imagePath = descriptor["reference_image_url"] as? String,
              let imageURL = URL(string: imagePath, relativeTo: origin)?.absoluteURL,
              imageURL.scheme == origin.scheme, imageURL.host == origin.host, imageURL.port == origin.port else {
            throw FittingError(message: code == 422 ? "Chọn một áo, một quần và tối đa một áo khoác; hoặc một váy. Các món cần có ảnh mẫu." : code == 429 ? "Phiên thay đồ đang bận. Bạn thử lại sau một chút nhé." : "Chưa tạo được phiên thay đồ. Bạn thử lại nhé.")
        }
        guard generation == epoch, !Task.isCancelled else {
            await Self.closeToken(token, origin: origin, headers: headers); throw CancellationError()
        }
        self.token = token
        log.info("fitting_session_created items=\(ids.count)")
        expiry = Task { [weak self] in
            let seconds = min(120, max(1, descriptor["maxSeconds"] as? Int ?? 120))
            try? await Task.sleep(nanoseconds: UInt64(seconds) * 1_000_000_000)
            guard !Task.isCancelled, let self, self.generation == epoch else { return }
            self.stop(message: "Phiên thay đồ đã kết thúc. Bấm Thử đồ để tiếp tục.")
            self.notify?(self.execution)
        }
        let client = XmaxClient(configuration: XmaxConfiguration(apiKey: token, environment: descriptor["environment"] as? String == "china" ? .china : .global))
        let manager = client.createRealtimeManager(options: RealtimeConfiguration(model: .x2_1, isFrameInterpolationEnabled: false))
        self.manager = manager
        await manager.setStateListener { [weak self] value in
            guard let self, self.generation == epoch else { return }
            if case .failure(let error) = value.reason {
                self.log.error("fitting_runtime_code=\(error.code.rawValue, privacy: .public)")
                self.fail("Kết nối thay đồ bị gián đoạn. Bạn bấm Thử đồ để thử lại nhé.")
            }
        }
        var imageRequest = URLRequest(url: imageURL, timeoutInterval: 20)
        headers.forEach { imageRequest.setValue($1, forHTTPHeaderField: $0) }
        let (image, imageResponse) = try await URLSession.shared.data(for: imageRequest)
        guard (imageResponse as? HTTPURLResponse)?.statusCode == 200, image.count <= 12_000_000 else {
            throw FittingError(message: "Chưa tải được ảnh quần áo. Bạn chọn lại món đồ nhé.")
        }
        try check(epoch)
        let uploaded = try await client.createStorageManager().uploadImage(image, fileName: "ski-outfit.jpg", contentType: "image/jpeg", progress: nil)
        try check(epoch)
        let source = FittingCameraSource(camera: camera)
        self.source = source
        let local = try await manager.createExternalVideoStream(source: source)
        try check(epoch)
        let firstFrame = FittingFirstFrame()
        try await manager.setRemoteMediaFrameListener { [weak self] frame in
            guard case .video = frame.content, firstFrame.take() else { return }
            Task { @MainActor [weak self] in
                guard let self, self.generation == epoch else { return }
                self.receivedFrame = true; self.markLiveIfReady()
            }
        }
        let remote = try await manager.startGeneration(localStream: local, context: RealtimeContext(prompt: prompt, referencePath: uploaded.url.absoluteString), faceRestoration: true)
        try check(epoch)
        track = remote.videoTrack
        markLiveIfReady()
    }

    private func check(_ epoch: UUID) throws { if generation != epoch || Task.isCancelled { throw CancellationError() } }
    private func markLiveIfReady() {
        guard status == "loading", receivedFrame, track != nil else { return }
        deadline?.cancel(); deadline = nil
        update("live", "Đang hiển thị bộ đồ bạn chọn.")
        log.info("fitting_first_video_frame")
        notify?(execution)
    }
    private func update(_ status: String, _ message: String) {
        self.status = status; self.message = message; onChange?()
    }
    private func fail(_ message: String) {
        log.error("fitting_failed phase=\(self.status, privacy: .public)")
        stop(); update("error", message); notify?(execution)
    }
    func stop(message: String = "") {
        generation = UUID(); task?.cancel(); task = nil
        deadline?.cancel(); deadline = nil; expiry?.cancel(); expiry = nil
        let oldManager = manager, oldSource = source, oldToken = token, oldOrigin = origin, oldHeaders = headers, previous = cleanup
        manager = nil; source = nil; token = nil; track = nil; receivedFrame = false; showsOriginal = false
        itemIDs = []; update("stopped", message)
        cleanup = Task {
            await previous?.value
            await oldSource?.stop()
            await oldManager?.setStateListener(nil)
            try? await oldManager?.setRemoteMediaFrameListener(nil)
            await oldManager?.close()
            if let oldToken, let oldOrigin { await Self.closeToken(oldToken, origin: oldOrigin, headers: oldHeaders) }
        }
    }
    private static func closeToken(_ token: String, origin: URL, headers: [String: String]) async {
        var request = URLRequest(url: origin.appendingPathComponent("api/tryon/xmax/close"), timeoutInterval: 10)
        request.httpMethod = "POST"; request.httpBody = try? JSONSerialization.data(withJSONObject: ["token": token])
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        _ = try? await URLSession.shared.data(for: request)
    }
}

private struct FittingError: Error { let message: String }
private final class FittingFirstFrame: @unchecked Sendable {
    private let lock = NSLock()
    private var used = false
    func take() -> Bool { lock.lock(); defer { lock.unlock() }; if used { return false }; used = true; return true }
}

@MainActor private final class FittingCameraSource: RealtimeExternalVideoSource {
    let videoFormat: RealtimeVideoFormat
    private let geometry: FittingVideoGeometry
    private let camera: ShadowCamera
    init(camera: ShadowCamera) {
        self.camera = camera
        geometry = FittingVideoGeometry(sensorSize: camera.frameSize)
        videoFormat = RealtimeVideoFormat(width: geometry.width, height: geometry.height, fps: 20)
    }
    func start(sink: any RealtimeExternalFrameSink) async throws {
        let converter = try FittingFrameConverter(geometry: geometry)
        camera.setFrameHandler { buffer, front in converter.push(buffer, front: front, sink: sink) }
    }
    func stop() async { camera.setFrameHandler(nil) }
}

/// Runs only on ShadowCamera's serial capture queue; no JPEG encoding on the video path.
private final class FittingFrameConverter {
    private let context = CIContext(options: [.cacheIntermediates: false])
    private let geometry: FittingVideoGeometry
    private var pool: CVPixelBufferPool?
    private var lastTime = -Double.infinity
    init(geometry: FittingVideoGeometry) throws {
        self.geometry = geometry
        let attributes: [String: Any] = [kCVPixelBufferWidthKey as String: geometry.width, kCVPixelBufferHeightKey as String: geometry.height,
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA, kCVPixelBufferIOSurfacePropertiesKey as String: [:]]
        guard CVPixelBufferPoolCreate(nil, nil, attributes as CFDictionary, &pool) == kCVReturnSuccess else {
            throw FittingError(message: "Chưa chuẩn bị được camera để thử đồ.")
        }
    }
    func push(_ buffer: CVPixelBuffer, front: Bool, sink: any RealtimeExternalFrameSink) {
        let timestamp = CMClockGetTime(CMClockGetHostTimeClock()), now = CMTimeGetSeconds(timestamp)
        guard sink.isRequestingFrames, now - lastTime >= 0.05, let pool else { return }
        lastTime = now
        autoreleasepool {
            var output: CVPixelBuffer?
            guard CVPixelBufferPoolCreatePixelBuffer(nil, pool, &output) == kCVReturnSuccess, let output else { return }
            var image = CIImage(cvPixelBuffer: buffer).oriented(.right)
            if front { image = image.oriented(.upMirrored) }
            image = image.transformed(by: geometry.transform(for: image.extent))
            context.render(image, to: output, bounds: CGRect(origin: .zero, size: geometry.size), colorSpace: CGColorSpaceCreateDeviceRGB())
            sink.pushVideo(output, timestamp: timestamp)
        }
    }
}

struct NativeTryOnVideo: View {
    @ObservedObject var session: NativeTryOnSession
    var body: some View {
        if let track = session.track {
            XmaxRealtimeVideo(track: track, videoContentMode: .fill, isInteractionEnabled: false)
                .opacity(session.status == "live" && !session.showsOriginal ? 1 : 0)
                .allowsHitTesting(false)
        }
    }
}

struct NativeTryOnControls: View {
    @ObservedObject var session: NativeTryOnSession
    var body: some View {
        if session.active {
            HStack(spacing: 8) {
                Button(session.showsOriginal ? "Xem bộ đang thử" : "Xem camera gốc") { session.showsOriginal.toggle() }
                    .disabled(session.status != "live")
                Button("Dừng thử") { session.stop() }
            }.font(.caption).buttonStyle(.bordered).controlSize(.large)
                .tint(Color(red: 1, green: 210/255, blue: 63/255)).padding(.bottom, 10)
        }
    }
}
