import SwiftUI
import Shared

/// Bridges the Kotlin store to SwiftUI. Kotlin calls back on the main thread.
final class NoteListModel: ObservableObject {
    @Published private(set) var state: NoteListState
    private let store: NoteListStore
    private var subscription: StoreSubscription?

    init(store: NoteListStore) {
        self.store = store
        state = store.currentState
        subscription = store.observeState { [weak self] in self?.state = $0 }
    }

    func dispatch(_ action: NoteListAction) {
        store.dispatch(action: action)
    }

    deinit {
        subscription?.cancel()
        store.close()
    }
}

struct NoteListView: View {
    @EnvironmentObject private var language: AppLanguage
    @StateObject private var model: NoteListModel
    let onOpenNote: (String?) -> Void

    init(store: @autoclosure @escaping () -> NoteListStore, onOpenNote: @escaping (String?) -> Void) {
        _model = StateObject(wrappedValue: NoteListModel(store: store()))
        self.onOpenNote = onOpenNote
    }

    var body: some View {
        content
            .navigationTitle(language.text("ui_066"))
            .toolbar {
                Button(language.text("ui_160")) { onOpenNote(nil) }
            }
    }

    @ViewBuilder private var content: some View {
        if model.state.isLoading {
            ProgressView()
        } else if let error = model.state.error {
            VStack(spacing: 12) {
                Text(language.systemText(error.message))
                Button(language.text("retry")) { model.dispatch(NoteListActionRetry.shared) }
            }
        } else if model.state.notes.isEmpty {
            Text(language.text("ui_037")).foregroundStyle(.secondary)
        } else {
            List(model.state.notes, id: \.id) { note in
                Button { onOpenNote(note.id) } label: {
                    VStack(alignment: .leading) {
                        Text(note.title)
                        Text(note.body).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                .swipeActions {
                    Button(language.text("ui_197"), role: .destructive) {
                        model.dispatch(NoteListActionDelete(id: note.id))
                    }
                }
            }
        }
    }
}
