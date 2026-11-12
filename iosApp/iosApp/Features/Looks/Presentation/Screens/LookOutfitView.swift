import SwiftUI
import Shared

struct LookOutfitView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: LookModel
    let onCamera: () -> Void
    let onService: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("AI OUTFITS · CONCEPT").font(.caption).foregroundStyle(.secondary)
            Text(language.text("outfit_heading")).font(.largeTitle.bold())
            Text(language.text("ui_022"))
                .font(.subheadline).foregroundStyle(.secondary)
            LookMannequin(preset: model.state.preset).frame(height: 290)
                .frame(maxWidth: .infinity).background(Color(red: 0.81, green: 0.83, blue: 0.77), in: RoundedRectangle(cornerRadius: 28))
            LookPresetPicker(model: model)
            Picker(language.text("occasion"), selection: Binding(get: { model.state.occasion }, set: { model.send(LookActionSelectOccasion(occasion: $0)) })) {
                ForEach(["Đi làm", "Đi chơi", "Hẹn hò"], id: \.self) { Text($0).tag($0) }
            }.pickerStyle(.menu)
            LookPrimaryButton(title: language.text("ui_131"), action: onCamera)
            LookPrimaryButton(title: !model.state.outfitSaved ? language.text("ui_110") : language.text("ui_246"), disabled: model.state.saving || model.state.outfitSaved) {
                model.send(LookActionSaveOutfit.shared)
            }
            if let error = model.state.error { Text(language.systemText(error)).foregroundStyle(.red) }
            Button(language.text("ui_096"), action: onService).frame(minHeight: 44)
        }
    }

}
