import SwiftUI
import Shared

struct StudioGeneratingView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onResults: () -> Void
    let onRetry: () -> Void
    var body: some View {
        StudioPage(title: language.text("ui_033"), model: model) {
            Image(systemName: "sparkles").font(.system(size: 64)).foregroundStyle(LookStyle.accent).padding(.vertical, 40)
            Text(model.busy ? language.text("ui_213") : language.text("ui_024")).font(.largeTitle.bold())
            Text(language.text("ui_052")).foregroundStyle(.secondary)
            if model.state.suggested { LookPrimaryButton(title: language.text("ui_194"), action: onResults) }
            else if !model.busy { LookPrimaryButton(title: language.text("retry"), action: onRetry) }
        }
    }
}
