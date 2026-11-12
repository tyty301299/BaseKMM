import SwiftUI
import Shared

final class StudioModel: ObservableObject {
    @Published private(set) var state: StudioState
    private var pendingMessages: [UiMessage] = []
    var onMessage: ((UiMessage) -> Void)? {
        didSet {
            guard let onMessage else { return }
            let buffered = pendingMessages
            pendingMessages.removeAll()
            buffered.forEach(onMessage)
        }
    }
    var onEffect: ((StudioEffect) -> Void)?
    private let store: StudioStore
    private var subscriptions: [StoreSubscription] = []

    init(store: StudioStore) {
        self.store = store
        state = store.currentState
        subscriptions = [
            store.observeState { [weak self] in self?.state = $0 },
            store.observeMessages { [weak self] in self?.receive($0) },
            store.observeEffects { [weak self] in self?.onEffect?($0) }
        ]
    }
    private func receive(_ message: UiMessage) {
        if let onMessage { onMessage(message) }
        else { pendingMessages.append(message) }
    }

    func send(_ action: StudioAction) { store.dispatch(action: action) }
    var busy: Bool { !state.busy.isEmpty }
    deinit { subscriptions.forEach { $0.cancel() }; store.close() }
}
