import SwiftUI
import Shared

struct LookCaptureView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: LookModel
    let capture: SavedLook
    let onClose: () -> Void
    let onHistory: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack {
                    Text(language.text("ui_098")).font(.title2.bold())
                    Spacer()
                    Button(language.text("close"), action: onClose).disabled(model.state.saving)
                }
                LookMannequin(preset: capture.preset, before: capture.before, flipped: capture.flipped)
                    .frame(height: 380).frame(maxWidth: .infinity)
                    .background(Color(red: 0.81, green: 0.83, blue: 0.77), in: RoundedRectangle(cornerRadius: 28))
                Text(capture.title).font(.title.bold())
                Text(language.format("sample_result_occasion", String(capture.occasion))).foregroundStyle(.secondary)
                Text(language.text("ui_113")).font(.subheadline)
                if model.state.saving { ProgressView(language.text("ui_219")) }
                if let error = model.state.error { Text(language.systemText(error)).foregroundStyle(.red) }
                if model.state.savedId == capture.id {
                    Label(language.text("ui_246"), systemImage: "checkmark.circle")
                    LookPrimaryButton(title: language.text("ui_193"), action: onHistory)
                } else {
                    LookPrimaryButton(title: model.state.saving ? language.text("ui_220") : language.text("ui_115"), disabled: model.state.saving) {
                        model.send(LookActionSaveCapture.shared)
                    }
                }
                Button(language.text("ui_045")) { model.send(LookActionRetake.shared) }.frame(minHeight: 44).disabled(model.state.saving)
            }.padding(24).frame(maxWidth: 640).frame(maxWidth: .infinity)
        }.background(LookStyle.background.ignoresSafeArea())
    }
}
