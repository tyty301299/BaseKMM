import SwiftUI
import Shared

struct StudioComponentsView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onCamera: () -> Void
    var body: some View {
        StudioPage(title: language.text("ui_159"), model: model) {
            Text("SHADOW / Mobile system").font(.largeTitle.bold())
            Text(language.text("ui_147")).foregroundStyle(.secondary)
            HStack { Image(systemName: "sparkles"); Text("Liquid Glass / Material") }
                .padding(24).frame(maxWidth: .infinity).modifier(LookGlass())
            LookPrimaryButton(title: language.text("ui_166"), action: onCamera)
            ProgressView(language.text("ui_186"))
            Label(language.text("ui_064"), systemImage: "info.circle")
            Text(language.text("ui_048")).font(.caption)
        }
    }
}
