import SwiftUI
import AVFoundation
import Shared

final class LiveTryOnModel: ObservableObject {
    @Published private(set) var state: LiveTryOnState
    @Published private(set) var permissionDenied = false
    @Published private(set) var memoryMB: Double = 0
    let engine: NativeLiveVideoEngine
    let store: LiveTryOnStore
    private var subscription: StoreSubscription?
    private var permissionGeneration = 0
    private var memoryTimer: Timer?

    init(container: AppContainer, shadow: ShadowStore) {
        let engine = NativeServices.makeLiveVideoEngine()
        self.engine = engine
        store = container.liveTryOnStore(shadow: shadow, engine: engine)
        state = store.currentState
        subscription = store.observeState { [weak self] in self?.state = $0 }
        memoryTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            self?.memoryMB = LiveMemoryReader.megabytes()
        }
    }

    func start() {
        permissionGeneration += 1
        let epoch = permissionGeneration
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            DispatchQueue.main.async {
                guard let self, epoch == self.permissionGeneration, UIApplication.shared.applicationState == .active else { return }
                self.permissionDenied = !granted
                if granted { self.store.start() }
            }
        }
    }

    func stop() {
        permissionGeneration += 1
        store.stop(message: "Đã dừng phiên.")
    }

    deinit {
        memoryTimer?.invalidate()
        subscription?.cancel()
        store.close()
    }
}

enum LiveTryOnBuilder {
    static func build(container: AppContainer, shadow: ShadowStore, onClose: @escaping () -> Void) -> LiveTryOnView {
        LiveTryOnView(container: container, shadow: shadow, onClose: onClose)
    }
}
