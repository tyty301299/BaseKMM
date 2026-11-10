import SwiftUI
import Shared

enum Route: Hashable {
    case studio(StudioRoute)
    case notes
    case noteEdit(noteId: String?)
}

/// The only place that decides where each screen goes.
struct RootView: View {
    let container: AppContainer
    @StateObject private var looks: LookModel
    @StateObject private var studio: StudioModel
    @StateObject private var feedback = StudioFeedback()
    @State private var serviceTab = 0
    @State private var tab = 0
    @State private var showCamera = false
    @State private var showService = false
    @State private var selectedRecord: LookRecordRoute?
    @State private var retryAfterDismiss = false

    init(container: AppContainer) {
        self.container = container
        _studio = StateObject(wrappedValue: StudioModel(store: container.studioStore()))
        _looks = StateObject(wrappedValue: LookModel(store: container.lookStore()))
    }
    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            LookHomeView(model: looks, studio: studio, tab: $tab, onCamera: { navigate(.cameraIntro) },
                onRecord: { selectedRecord = LookRecordRoute(id: $0) },
                onService: { openService(tab == 2 ? 0 : 1) },
                onAdd: { navigate(.addItem) }, onItem: { id in
                    studio.send(StudioActionOpenItem(id: id)); navigate(.item(id))
                }, onOccasion: { navigate(.occasion) }, onTeach: {
                    studio.send(StudioActionNewLesson.shared); navigate(.teach)
                }, onSkill: { navigate(.skill($0)) }, onSettings: { if !looks.state.saving && !studio.busy { navigate(.settings) } })
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .studio(let destination):
                        StudioDestination(route: destination, model: studio, looks: looks,
                            navigate: navigate, openCamera: openCamera,
                            homeTab: { tab = $0; path = [] }, service: openService)
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
        .fullScreenCover(isPresented: $showCamera) {
            LookCameraView(model: looks, onClose: { showCamera = false }, onHistory: {
                showCamera = false
                tab = 3
                path = []
            }).modifier(StudioFeedbackOverlay())
        }
        .sheet(isPresented: $showService) {
            NavigationStack {
                HomeView(container: container, initialTab: serviceTab)
                    .toolbar { ToolbarItem(placement: .navigationBarLeading) { Button("Đóng") { showService = false } } }
            }
        }
        .sheet(item: $selectedRecord, onDismiss: {
            if retryAfterDismiss { retryAfterDismiss = false; showCamera = true }
        }) { record in
            LookDetailView(model: looks, recordID: record.id, onRetry: {
                retryAfterDismiss = true
                selectedRecord = nil
            }).modifier(StudioFeedbackOverlay())
        }
        .modifier(StudioFeedbackOverlay())
        .preferredColorScheme(studio.state.data.settings.dark ? .dark : .light)
        .environment(\.lookGlassEnabled, studio.state.data.settings.glass)
        .environment(\.lookOperationBusy, looks.state.saving)
        .environmentObject(feedback)
        .onAppear {
            looks.onMessage = feedback.show
            studio.onMessage = feedback.show
            let routes = $path
            studio.onEffect = { [weak looks] effect in
                if let event = effect as? StudioEffectItemAdded { routes.wrappedValue.append(.studio(.item(event.id))) }
                else if let event = effect as? StudioEffectSkillSaved { routes.wrappedValue.append(.studio(.saved(event.id))) }
                else if effect is StudioEffectItemDeleted || effect is StudioEffectSkillDeleted {
                    if !routes.wrappedValue.isEmpty { routes.wrappedValue.removeLast() }
                } else if effect is StudioEffectDataCleared { looks?.send(LookActionRetake.shared) }
            }
        }
    }

    private func navigate(_ route: StudioRoute) { path.append(.studio(route)) }
    private func openService(_ section: Int) { serviceTab = section; showService = true }

    private func openCamera() {
        guard !looks.state.saving else { return }
        looks.send(LookActionRetake.shared)
        showCamera = true
    }
}

private struct LookRecordRoute: Identifiable { let id: String }
