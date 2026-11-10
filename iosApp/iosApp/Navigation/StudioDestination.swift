import SwiftUI
import Shared

struct StudioDestination: View {
    let route: StudioRoute
    @ObservedObject var model: StudioModel
    @ObservedObject var looks: LookModel
    let navigate: (StudioRoute) -> Void
    let openCamera: () -> Void
    let homeTab: (Int) -> Void
    let service: (Int) -> Void

    @ViewBuilder var body: some View {
        switch route {
        case .addItem:
            StudioAddItemView(model: model, onPermission: { navigate(.wardrobePermission) })
        case .wardrobePermission:
            StudioCameraIntroView(model: model, forWardrobe: true, onContinue: {})
        case .item(let id):
            StudioItemView(model: model, itemID: id, onTry: { preset in
                looks.send(LookActionSelectPreset(preset: preset)); navigate(.cameraIntro)
            })
        case .occasion:
            StudioOccasionView(model: model, looks: looks, onGenerate: {
                model.send(StudioActionSuggest(occasion: looks.state.occasion)); navigate(.generating)
            }, onAdd: { navigate(.addItem) })
        case .generating:
            StudioGeneratingView(model: model, onResults: { navigate(.suggestions) }, onRetry: {
                model.send(StudioActionSuggest(occasion: looks.state.occasion))
            })
        case .suggestions:
            StudioSuggestionsView(model: model, onSelect: { preset in
                looks.send(LookActionSelectPreset(preset: preset)); navigate(.outfit)
            }, onAdd: { navigate(.addItem) })
        case .outfit:
            StudioPage(title: "Chi tiết bộ phối", model: model) {
                LookOutfitView(model: looks, onCamera: { navigate(.cameraIntro) }, onService: { service(1) })
            }
        case .cameraIntro:
            StudioCameraIntroView(model: model, forWardrobe: false, onContinue: openCamera)
        case .teach:
            StudioTeachView(model: model, onRecord: {
                model.send(StudioActionStartRecording.shared); navigate(.recording)
            })
        case .recording:
            StudioRecordingView(model: model, onCompile: {
                model.send(StudioActionCompileSample.shared); navigate(.compiling)
            })
        case .compiling:
            StudioCompileView(model: model, onReview: { navigate(.review) })
        case .review:
            StudioReviewView(model: model)
        case .saved(let id):
            StudioSavedSkillView(model: model, skillID: id, onGuide: {
                model.send(StudioActionOpenSkill(id: id)); navigate(.guide)
            }, onHistory: { homeTab(3) })
        case .skill(let id):
            StudioSkillDetailView(model: model, skillID: id, onGuide: {
                model.send(StudioActionOpenSkill(id: id)); navigate(.guide)
            })
        case .guide:
            StudioGuideView(model: model, onAsk: { navigate(.ask) }, onDone: { homeTab(2) })
        case .ask:
            StudioAskView(model: model, onService: { service(0) })
        case .settings:
            StudioSettingsView(model: model, onService: { service(0) }, onComponents: { navigate(.components) })
        case .components:
            StudioComponentsView(model: model, onCamera: { navigate(.cameraIntro) })
        }
    }
}
