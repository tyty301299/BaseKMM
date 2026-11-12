import SwiftUI
import Shared

/// Bridges `TeachStore` to SwiftUI, driving the camera and Vietnamese dictation.
final class TeachModel: ObservableObject {
    @Published var videoError: String?
    @Published private(set) var importingVideo = false
    @Published private(set) var state: TeachState

    let camera = NativeServices.makeCamera()
    private let shadow: ShadowStore
    private var closed = false
    private let store: TeachStore
    private var subscription: StoreSubscription?
    private var listener: AuthorizedSpeechListener?

    init(container: AppContainer, shadow: ShadowStore) {
        self.shadow = shadow
        let bridge = CameraFrameBridge(camera: camera)
        store = container.teachStoreWithVideo(shadow: shadow, camera: bridge, persistence: IOSTeachJobPersistence(endpoint: shadow.currentState.endpoint))
        state = store.currentState
        subscription = store.observeState { [weak self] value in self?.state = value }
        listener = NativeServices.makeListener(
            elapsedSeconds: { [weak self] in Double(self?.store.currentState.elapsedMs ?? 0) / 1000 },
            onSentence: { [weak self] t0, t1, text in
                self?.store.dispatch(action: TeachActionSentence(t0: t0, t1: t1, text: text))
            },
            onPartial: { [weak self] text in
                self?.store.dispatch(action: TeachActionPartialSentence(text: text))
            },
            onUnavailable: { [weak self] in
                self?.store.dispatch(action: TeachActionSpeechAvailable(value: false))
            })
    }

    func uploadVideo(_ source: URL) {
        guard !closed, !importingVideo else { return }
        importingVideo = true
        videoError = nil
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let outcome: Result<URL, Error> = Result {
                let bytes = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                guard bytes > 0 && bytes <= 32 * 1024 * 1024 else {
                    throw NSError(domain: "TeachVideo", code: 1, userInfo: [NSLocalizedDescriptionKey: "Video trống hoặc vượt 32 MB."])
                }
                let folder = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("TeachVideos")
                try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                let destination = folder.appendingPathComponent(UUID().uuidString).appendingPathExtension(source.pathExtension)
                try FileManager.default.copyItem(at: source, to: destination)
                return destination
            }
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.importingVideo = false
                guard !self.closed else { return }
                do {
                    let destination = try outcome.get()
                    self.store.dispatch(action: TeachActionUploadVideo(source: try IOSTeachVideoSource(url: destination)))
                } catch {
                    self.videoError = (error as NSError).domain == "TeachVideo" ? error.localizedDescription : "Không đọc được video. Hãy chọn clip lưu trên thiết bị."
                }
            }
        }
    }

    func prepareMicrophone() {
        listener?.requestAccess { [weak self] granted in
            self?.store.dispatch(action: TeachActionSpeechAvailable(
                value: granted && (self?.listener?.available ?? false)))
        }
    }

    func send(_ action: TeachAction) { store.dispatch(action: action) }

    func imageHeaders(_ url: String) -> [String: String] { shadow.mediaHeadersFor(url: url) }

    func startRecording() {
        guard !closed else { return }
        camera.start()
        store.dispatch(action: TeachActionStartRecording.shared)
        listener?.start()
    }

    func stopRecording() {
        listener?.stop()
        store.dispatch(action: TeachActionStopRecording.shared)
    }

    func stop() {
        listener?.stop()
        camera.stop()
    }

    func close() {
        guard !closed else { return }
        closed = true
        listener?.shutdown()
        subscription?.cancel()
        camera.stop()
        store.close()
    }
}
