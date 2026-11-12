import SwiftUI
import Shared

struct StudioCompileView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onReview: () -> Void
    var body: some View {
        StudioPage(title: language.text("ui_003"), model: model) {
            Image(systemName: "sparkles").font(.system(size: 68)).foregroundStyle(LookStyle.accent).padding(.vertical, 35)
            Text(model.busy ? language.text("ui_210") : language.text("ui_185")).font(.largeTitle.bold())
            Text(language.text("ui_047")).foregroundStyle(.secondary)
            if !model.state.draftSteps.isEmpty { LookPrimaryButton(title: language.text("ui_054"), action: onReview) }
            else if !model.busy { LookPrimaryButton(title: language.text("ui_035")) { model.send(StudioActionCompileSample.shared) } }
        }
    }
}
