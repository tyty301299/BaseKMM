import SwiftUI
import Shared

enum Route: Hashable {
    case studio(StudioRoute)
    case history
    case notes
    case noteEdit(noteId: String?)
}

/// The only place that decides where each screen goes.
struct RootView: View {
    @EnvironmentObject private var language: AppLanguage
    let container: AppContainer
    @StateObject private var shadow: ShadowModel
    @StateObject private var looks: LookModel
    @StateObject private var studio: StudioModel
    @StateObject private var feedback = StudioFeedback()
    @State private var serviceTab = 0
    @State private var serviceSettings = false
    @State private var tab = 0
    @State private var showCamera = false
    @State private var showService = false
    @State private var selectedRecord: LookRecordRoute?
    @State private var retryAfterDismiss = false

    init(container: AppContainer) {
        self.container = container
        _shadow = StateObject(wrappedValue: ShadowModel(store: container.shadowStore()))
        _studio = StateObject(wrappedValue: StudioModel(store: container.studioStore()))
        _looks = StateObject(wrappedValue: LookModel(store: container.lookStore()))
    }
    @State private var path: [Route] = []
    /// Connect once without presenting a modal over the four primary tabs.
    @State private var showedServiceOnLaunch = false

    var body: some View {
        NavigationStack(path: $path) {
            ShadowCameraScreen(container: container, shadow: shadow, cameraVisible: !showService,
                onWardrobe: { openService(1) }, onLessons: { openService(0) })
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case .studio(let destination):
                        StudioDestination(route: destination, model: studio, looks: looks, shadow: shadow,
                            navigate: navigate, openCamera: openCamera,
                            homeTab: { tab = $0; path = $0 == 3 ? [.history] : [] }, service: openService,
                            connectionSettings: { serviceTab = 0; serviceSettings = true; showService = true })
                    case .history:
                        LookHistoryView(model: looks, studio: studio,
                            onRecord: { selectedRecord = LookRecordRoute(id: $0) },
                            onCamera: { path = [] }, onSkill: { navigate(.skill($0)) })
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
                path = [.history]
            }, onSettings: { showCamera = false; navigate(.settings) }, onNetworkRestored: { shadow.send(ShadowActionReconnect.shared) }, container: container, shadow: shadow).modifier(StudioFeedbackOverlay())
        }
        .sheet(isPresented: $showService) {
            NavigationStack {
                HomeView(container: container, initialTab: serviceTab, initialSettings: serviceSettings, existingModel: shadow,
                         onGuideCompleted: { session, skill, title, manual in
                             looks.send(LookActionSaveGuideCompletion(sessionId: session, skillId: skill, title: title,
                                 source: manual ? "manual" : (title.hasSuffix(" · Demo") ? "demo" : "ai")))
                         })
                    .toolbar { ToolbarItem(placement: .navigationBarLeading) { Button(language.text("close")) { showService = false } } }
            }
        }
        .onAppear {
            guard !showedServiceOnLaunch else { return }
            showedServiceOnLaunch = true
            let endpoint = UserDefaults.standard.string(forKey: "shadow.endpoint") ?? ShadowDefaultsKt.DEFAULT_SHADOW_ORIGIN
            let code = UserDefaults.standard.string(forKey: "shadow.access_code") ?? ""
            shadow.send(ShadowActionConnect(endpoint: endpoint, code: code))
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
            shadow.onMessage = feedback.show
            feedback.localize = language.systemText
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
    private func openService(_ section: Int) { serviceTab = section; serviceSettings = false; showService = true }

    private func openCamera() {
        guard !looks.state.saving else { return }
        looks.send(LookActionRetake.shared)
        showCamera = true
    }
}

private struct LookRecordRoute: Identifiable { let id: String }
