import SwiftUI
import Shared

/// Bridges `GuideStore` to SwiftUI. The store itself is built in Kotlin (`AppContainer`), so this
/// only supplies the camera and Vietnamese voice.
final class GuideModel: ObservableObject {
    @Published private(set) var state: GuideState

    let camera = NativeServices.makeCamera()
    private let shadow: ShadowStore
    private var closed = false
    private let speaker: Speaker
    private let store: GuideStore
    private var subscription: StoreSubscription?

    init(container: AppContainer, shadow: ShadowStore, speaker: Speaker = NativeServices.makeSpeaker()) {
        self.shadow = shadow
        self.speaker = speaker
        let bridge = CameraFrameBridge(camera: camera)
        store = container.guideStore(shadow: shadow, camera: bridge, speaker: speaker)
        store.dispatch(action: GuideActionSpeechAvailable(value: speaker.available))
        state = store.currentState
        subscription = store.observeState { [weak self] value in
            self?.state = value
        }
    }

    func imageHeaders(_ url: String) -> [String: String] { shadow.mediaHeadersFor(url: url) }

    func start(skillId: String) {
        guard !closed else { return }
        camera.start()
        store.dispatch(action: GuideActionStart(skillId: skillId))
    }

    func stop() {
        store.dispatch(action: GuideActionStop.shared)
        camera.stop()
    }

    func step(forward: Bool) {
        store.dispatch(action: GuideActionStep(forward: forward))
    }

    func repeatInstruction() {
        store.dispatch(action: GuideActionRepeatInstruction.shared)
    }

    func toggleDebugControls() {
        store.dispatch(action: GuideActionShowDebugControls(visible: !state.debugControls))
    }

    func close() {
        guard !closed else { return }
        closed = true
        subscription?.cancel()
        camera.stop()
        store.close()
        speaker.shutdown()
    }
}
