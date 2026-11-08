import SwiftUI
import Shared

enum Route: Hashable {
    case notes
    case noteEdit(noteId: String?)
}

/// The only place that decides where each screen goes.
struct RootView: View {
    let container: AppContainer
    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            HomeView(container: container) { path.append(.notes) }
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .notes:
                        NoteListView(store: container.noteListStore()) { id in
                            path.append(.noteEdit(noteId: id))
                        }
                    case .noteEdit(let noteId):
                        NoteEditView(store: container.noteEditStore(noteId: noteId)) {
                            path.removeLast()
                        }
                    }
                }
        }
    }
}
