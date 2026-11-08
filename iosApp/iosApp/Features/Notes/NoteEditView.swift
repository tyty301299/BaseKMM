import SwiftUI
import Shared

/// Bridges the Kotlin store to SwiftUI. Kotlin calls back on the main thread.
final class NoteEditModel: ObservableObject {
    @Published private(set) var state: NoteEditState
    var onFinished: () -> Void = {}
    private let store: NoteEditStore
    private var subscriptions: [StoreSubscription] = []

    init(store: NoteEditStore) {
        self.store = store
        state = store.currentState
        subscriptions = [
            store.observeState { [weak self] in self?.state = $0 },
            store.observeEffects { [weak self] effect in
                if effect is NoteEditEffectFinished { self?.onFinished() }
            },
        ]
    }

    func dispatch(_ action: NoteEditAction) {
        store.dispatch(action: action)
    }

    deinit {
        subscriptions.forEach { $0.cancel() }
        store.close()
    }
}

struct NoteEditView: View {
    @StateObject private var model: NoteEditModel
    let onDone: () -> Void

    init(store: @autoclosure @escaping () -> NoteEditStore, onDone: @escaping () -> Void) {
        _model = StateObject(wrappedValue: NoteEditModel(store: store()))
        self.onDone = onDone
    }

    private var isMissing: Bool { model.state.error == AppError.notFound }

    var body: some View {
        content
            .navigationTitle(model.state.isExisting ? "Sửa ghi chú" : "Ghi chú mới")
            .toolbar {
                Button("Lưu") { model.dispatch(NoteEditActionSave.shared) }
                    .disabled(!model.state.canSave)
            }
            .onAppear { model.onFinished = onDone }
    }

    @ViewBuilder private var content: some View {
        if model.state.isLoading {
            ProgressView()
        } else {
            Form {
                if let error = model.state.error {
                    Section {
                        Text(error.message).foregroundStyle(.red)
                        if !isMissing && model.state.isExisting {
                            Button("Thử lại") { model.dispatch(NoteEditActionRetry.shared) }
                        }
                    }
                }
                if !isMissing {
                    Section("Tiêu đề") {
                        TextField("Tiêu đề", text: Binding(
                            get: { model.state.title },
                            set: { model.dispatch(NoteEditActionTitleChanged(value: $0)) }
                        ))
                    }
                    Section("Nội dung") {
                        TextEditor(text: Binding(
                            get: { model.state.body },
                            set: { model.dispatch(NoteEditActionBodyChanged(value: $0)) }
                        ))
                        .frame(minHeight: 120)
                    }
                    if model.state.isExisting {
                        Button("Xóa ghi chú", role: .destructive) {
                            model.dispatch(NoteEditActionDelete.shared)
                        }
                        .disabled(model.state.isSaving)
                    }
                }
            }
        }
    }
}
