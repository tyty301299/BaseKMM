import SwiftUI
import AVFoundation
import Shared
import XmaxSDK
import OSLog

@MainActor
final class LiveTryOnModel: ObservableObject {
    struct TryOnAlert: Identifiable {
        let id = UUID()
        let title: String
        let message: String
        var retryCamera = false
    }
    @Published var alert: TryOnAlert?
    @Published var selectedId = ""
    @Published private(set) var hasAttempted = false
    @Published private(set) var remainingSeconds = 0
    @Published private(set) var diagnosticCode = ""
    private let logger = Logger(subsystem: "com.trioxstudios.ski", category: "TryOn")
    @Published private(set) var cameraPosition: CameraPosition = .front
    @Published private(set) var switchingCamera = false
    @Published private(set) var videoFPS = 0
    @Published private(set) var transportDelayMS: Int?
    private var cameraSwitch: Task<Void, Never>?
    @Published private(set) var appliedId = ""
    @Published private(set) var busy = false
    @Published private(set) var live = false
    @Published private(set) var message = "Chọn trang phục để thử trực tiếp."
    @Published private(set) var localTrack: RealtimeVideoTrack?
    @Published private(set) var remoteTrack: RealtimeVideoTrack?
    let garments: [WardrobeItem]
    private let origin: URL?
    private let shadow: ShadowStore
    private var manager: (any XmaxRealtimeManaging)?
    private var token: String?
    private var operation: Task<Void, Never>?
    private var deadline: Task<Void, Never>?
    private var cleanup: Task<Void, Never>?
    private var generation = UUID()

    init(container: AppContainer, shadow: ShadowStore) {
        self.shadow = shadow
        origin = URL(string: shadow.currentState.endpoint)
        garments = shadow.currentState.wardrobe.filter { !$0.imageUrl.isEmpty }
    }

    private struct Session: Decodable {
        let token: String
        let environment: String
        let prompt: String
        let reference_image_url: String?
        let item: Item?
        let maxSeconds: Int?
        struct Item: Decodable { let image_url: String? }
    }
    private struct Failure: Error { var status: Int? = nil }

    private func request(_ path: String, body: [String: Any]? = nil) async throws -> Data {
        guard let origin, let url = URL(string: path, relativeTo: origin)?.absoluteURL else { throw Failure() }
        var request = URLRequest(url: url)
        request.timeoutInterval = 45
        shadow.mediaHeadersFor(url: url.absoluteString).forEach { request.setValue($1, forHTTPHeaderField: $0) }
        if let body {
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let response = response as? HTTPURLResponse else { throw Failure() }
        guard (200..<300).contains(response.statusCode) else { throw Failure(status: response.statusCode) }
        return data
    }

    func start() {
        guard !busy, !switchingCamera, garments.contains(where: { $0.id == selectedId }) else { return }
        stop()
        let epoch = generation
        let id = selectedId
        busy = true
        hasAttempted = true
        diagnosticCode = ""
        message = "Đang chuẩn bị camera và trang phục…"
        operation = Task {
            await cleanup?.value
            do {
                try Task.checkCancellation()
                guard await AVCaptureDevice.requestAccess(for: .video) else {
                    throw Failure()
                }
                try Task.checkCancellation()
                message = "Đang tạo phiên thử đồ…"
                let data = try await request("/api/tryon/xmax/session", body: ["item_ids": [id]])
                let session = try JSONDecoder().decode(Session.self, from: data)
                // A cancelled request may still have created a server session.
                guard epoch == generation, !Task.isCancelled else {
                    Task { _ = try? await self.request("/api/tryon/xmax/close", body: ["token": session.token]) }
                    return
                }
                token = session.token
                guard let environment = XmaxEnvironment(rawValue: session.environment), !session.token.isEmpty else { throw Failure() }
                let client = XmaxClient(configuration: XmaxConfiguration(apiKey: session.token, environment: environment))
                let realtime = client.createRealtimeManager(options: RealtimeConfiguration(model: .x2_1, isFrameInterpolationEnabled: false))
                manager = realtime
                await realtime.setStateListener { [weak self] state in
                    guard let self, self.generation == epoch else { return }
                    self.logger.info("SDK state: \(state.connectionState.rawValue, privacy: .public)")
                    if case .failure(let error) = state.reason {
                        self.fail(error)
                    }
                }
                await realtime.setDownlinkVideoStatisticsListener { [weak self] stats in
                    guard let self, self.generation == epoch else { return }
                    self.videoFPS = stats?.renderFrameRate ?? 0
                    self.transportDelayMS = stats?.endToEndDelayMs
                    guard !self.live, self.videoFPS > 0 else { return }
                    self.live = true
                    self.busy = false
                    self.appliedId = id
                    self.message = "Đang thử đồ trực tiếp"
                    self.deadline?.cancel()
                    self.remainingSeconds = max(1, min(session.maxSeconds ?? 120, 120))
                    self.deadline = Task { [weak self] in
                        while let self, self.generation == epoch, self.remainingSeconds > 0 {
                            do { try await Task.sleep(nanoseconds: 1_000_000_000) } catch { return }
                            guard self.generation == epoch else { return }
                            self.remainingSeconds -= 1
                        }
                        guard let self, self.generation == epoch else { return }
                        self.stop()
                        self.message = "Phiên thử đồ đã hết thời gian. Bấm Thử lại để mở phiên mới với trang phục đã chọn."
                        self.alert = TryOnAlert(title: "Phiên thử đồ đã kết thúc", message: self.message)
                    }
                }
                deadline = Task { [weak self] in
                    do { try await Task.sleep(nanoseconds: 90_000_000_000) } catch { return }
                    guard let self, self.generation == epoch else { return }
                    self.fail(XmaxError(code: .timeout, message: "Startup timed out"))
                }
                message = "Đang mở camera…"
                let stream = try await realtime.createLocalCameraStream(
                    videoFormat: RealtimeVideoFormat(width: 1080, height: 1920, fps: 24,
                                                    encoderPreference: .maintainFramerate),
                    position: cameraPosition)
                try Task.checkCancellation()
                localTrack = stream.videoTrack
                guard let reference = session.reference_image_url ?? session.item?.image_url else { throw Failure() }
                message = "Đang tải ảnh trang phục…"
                let image = try await request(reference)
                guard let jpeg = UIImage(data: image)?.jpegData(compressionQuality: 0.9) else { throw Failure() }
                message = "Đang gửi trang phục để thử…"
                let uploaded = try await client.createStorageManager().uploadImage(jpeg, fileName: "ski-outfit.jpg", contentType: "image/jpeg")
                try Task.checkCancellation()
                message = "Đang kết nối video thử đồ…"
                let remote = try await realtime.connect(localStream: stream)
                try Task.checkCancellation()
                remoteTrack = remote.videoTrack
                message = "Đang thay trang phục…"
                try await realtime.startGeneration(context: RealtimeContext(prompt: session.prompt, referencePath: uploaded.url.absoluteString))
                try Task.checkCancellation()
                guard generation == epoch else { return }

            } catch {
                guard generation == epoch else { return }
                fail(error)
            }
        }
    }

    private func fail(_ error: Error) {
        let stage = message
        let code: String
        if let sdk = error as? XmaxError {
            code = sdk.code.rawValue + (sdk.httpStatus.map { " HTTP \($0)" } ?? "")
                + (sdk.apiCode.map { " API \($0)" } ?? "")
        } else if let http = error as? Failure, let status = http.status {
            code = "HTTP \(status)"
        } else if error is DecodingError {
            code = "SESSION_RESPONSE_INVALID"
        } else {
            code = "CLIENT_\((error as NSError).code)"
        }
        logger.error("Stopped at \(stage, privacy: .public), code \(code, privacy: .public)")
        stop()
        diagnosticCode = code
        message = AVCaptureDevice.authorizationStatus(for: .video) == .denied
            ? "Camera chưa được cấp quyền. Hãy bật quyền camera trong Cài đặt."
            : "Phiên thử đồ bị gián đoạn ở bước: \(stage) Bấm Thử lại để kết nối lại."
        alert = TryOnAlert(title: "Không thể tiếp tục thử đồ", message: "\(message)\n\nMã lỗi: \(code)")
    }

    func retry(_ notice: TryOnAlert) {
        alert = nil
        if notice.retryCamera { flipCamera() } else { start() }
    }

    func flipCamera() {
        guard !busy, !switchingCamera else { return }
        guard let realtime = manager, live else {
            cameraPosition = cameraPosition == .front ? .back : .front
            return
        }
        let epoch = generation
        switchingCamera = true
        message = "Đang đổi camera…"
        cameraSwitch = Task {
            do {
                let stream = try await realtime.switchCamera()
                try Task.checkCancellation()
                guard generation == epoch else { return }
                localTrack = stream.videoTrack
                cameraPosition = cameraPosition == .front ? .back : .front
                message = "Đang thử đồ trực tiếp"
            } catch {
                guard generation == epoch else { return }
                message = "Chưa đổi được camera. Hãy thử lại."
                let code = (error as? XmaxError)?.code.rawValue ?? "CAMERA_SWITCH_FAILED"
                alert = TryOnAlert(title: "Không đổi được camera", message: "\(message)\n\nMã lỗi: \(code)", retryCamera: true)
            }
            if generation == epoch { switchingCamera = false }
        }
    }

    func close() async {
        CameraHandoffTrace.record("tryon close requested")
        stop()
        await cleanup?.value
        CameraHandoffTrace.record("tryon close completed")
    }

    func stop() {
        alert = nil
        generation = UUID()
        operation?.cancel(); operation = nil
        cameraSwitch?.cancel(); cameraSwitch = nil
        switchingCamera = false
        videoFPS = 0; transportDelayMS = nil
        remainingSeconds = 0
        deadline?.cancel(); deadline = nil
        let previous = cleanup
        let realtime = manager
        let sessionToken = token
        manager = nil; token = nil
        localTrack = nil; remoteTrack = nil
        busy = false; live = false; appliedId = ""
        message = "Đã dừng phiên. Chọn trang phục để thử tiếp."
        cleanup = Task {
            await previous?.value
            if realtime != nil { CameraHandoffTrace.record("sdk close begin") }
            await realtime?.close()
            if realtime != nil { CameraHandoffTrace.record("sdk close end") }
            if let sessionToken {
                // Release capture before waiting on backend cleanup so closing restores
                // the Home camera even when the network is slow.
                Task { _ = try? await self.request("/api/tryon/xmax/close", body: ["token": sessionToken]) }
            }
        }
    }
}

enum LiveTryOnBuilder {
    static func build(container: AppContainer, shadow: ShadowStore, onClose: @escaping () -> Void) -> LiveTryOnView {
        LiveTryOnView(container: container, shadow: shadow, onClose: onClose)
    }
}
