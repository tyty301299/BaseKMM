import SwiftUI
import Shared

struct StudioOccasionView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    @ObservedObject var looks: LookModel
    let onGenerate: () -> Void
    let onAdd: () -> Void
    var body: some View {
        StudioPage(title: language.text("occasion_prompt"), model: model) {
            Text(language.text("ui_255")).font(.largeTitle.bold())
            Picker(language.text("occasion"), selection: Binding(get: { looks.state.occasion }, set: { looks.send(LookActionSelectOccasion(occasion: $0)) })) {
                ForEach(["Đi làm", "Đi chơi", "Hẹn hò"], id: \.self) { Text($0).tag($0) }
            }.pickerStyle(.segmented)
            TextField(language.text("ui_121"), text: Binding(get: { model.state.outfitNotes }, set: { model.send(StudioActionOutfitNotes(value: $0)) }), axis: .vertical)
                .lineLimit(3...5).textFieldStyle(.roundedBorder)
            Text(language.text("ui_072")).foregroundStyle(.secondary)
            if model.state.data.items.isEmpty {
                Text(language.text("ui_184"))
                LookPrimaryButton(title: language.text("add_garment"), action: onAdd)
            } else { LookPrimaryButton(title: language.text("ui_179"), action: onGenerate) }
        }
    }
}
