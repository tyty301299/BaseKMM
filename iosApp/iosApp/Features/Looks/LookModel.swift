import SwiftUI
import Shared

/// Swift bridge only. Domain actions and durable state belong to the shared Store.
final class LookModel: ObservableObject {
    @Published private(set) var state: LookState
    private var pendingMessages: [UiMessage] = []
    var onMessage: ((UiMessage) -> Void)? {
        didSet {
            guard let onMessage else { return }
            let buffered = pendingMessages
            pendingMessages.removeAll()
            buffered.forEach(onMessage)
        }
    }
    private var messageSubscription: StoreSubscription?
    private let store: LookStore
    private var subscription: StoreSubscription?

    init(store: LookStore) {
        self.store = store
        state = store.currentState
        messageSubscription = store.observeMessages { [weak self] in self?.receive($0) }
        subscription = store.observeState { [weak self] in self?.state = $0 }
    }

    private func receive(_ message: UiMessage) {
        if let onMessage { onMessage(message) }
        else { pendingMessages.append(message) }
    }

    func send(_ action: LookAction) { store.dispatch(action: action) }

    deinit {
        messageSubscription?.cancel()
        subscription?.cancel()
        store.close()
    }
}
