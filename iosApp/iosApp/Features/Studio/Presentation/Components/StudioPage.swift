import SwiftUI
import Shared

struct StudioPage<Content: View>: View {
    @EnvironmentObject private var language: AppLanguage
    @Environment(\.lookOperationBusy) private var lookBusy
    let title: String
    @ObservedObject var model: StudioModel
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                if model.state.loading { ProgressView(language.text("ui_235")) }
                if model.busy { ProgressView(language.systemText(model.state.busy)).accessibilityLabel(language.systemText(model.state.busy)) }
                if let error = model.state.error {
                    Text(language.systemText(error)).foregroundStyle(.red).accessibilityLabel(language.format("error_detail", String(error)))
                    Button(language.text("ui_180")) { model.send(StudioActionRetry.shared) }.disabled(model.busy)
                }
                content().disabled(model.busy || lookBusy)
            }.padding(24).frame(maxWidth: 640).frame(maxWidth: .infinity)
        }
        .background(LookStyle.background.ignoresSafeArea()).navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
        .navigationBarBackButtonHidden(model.busy || lookBusy).interactiveDismissDisabled(model.busy || lookBusy)
    }
}
