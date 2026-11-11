import SwiftUI
import Shared

struct StudioItemView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let itemID: String
    let onTry: (String) -> Void
    @State private var confirm = false
    var body: some View {
        StudioPage(title: language.text("ui_030"), model: model) {
            if let item = model.state.itemDraft, item.id == itemID,
               model.state.data.items.contains(where: { $0.id == itemID }) {
                StudioGarmentImage(preset: item.preset).frame(height: 300).frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 28))
                TextField(language.text("ui_177"), text: Binding(get: { model.state.itemDraft?.name ?? "" }, set: { model.send(StudioActionItemName(value: $0)) }))
                    .textFieldStyle(.roundedBorder).accessibilityLabel(language.text("ui_177"))
                Picker(language.text("ui_136"), selection: Binding(get: { model.state.itemDraft?.category ?? "Áo" }, set: { model.send(StudioActionItemCategory(value: $0)) })) {
                    Text(language.text("filter_tops")).tag("Áo"); Text(language.text("filter_jackets")).tag("Áo khoác")
                }.pickerStyle(.segmented)
                Text(language.text("ui_173")).foregroundStyle(.secondary)
                LookPrimaryButton(title: language.text("ui_114"), disabled: item.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) { model.send(StudioActionSaveItem.shared) }
                LookPrimaryButton(title: language.text("ui_130")) { onTry(item.preset) }
                Button(language.text("ui_205"), role: .destructive) { confirm = true }.frame(minHeight: 44)
            } else if !model.state.loading { Text(language.text("ui_094")) }
        }
        .onAppear {
            if model.state.itemDraft?.id != itemID { model.send(StudioActionOpenItem(id: itemID)) }
        }
        .onChange(of: model.state.data.items.map(\.id)) { _ in
            if model.state.itemDraft?.id != itemID { model.send(StudioActionOpenItem(id: itemID)) }
        }
        .confirmationDialog(language.text("ui_206"), isPresented: $confirm, titleVisibility: .visible) {
            Button(language.text("ui_197"), role: .destructive) { model.send(StudioActionDeleteItem(id: itemID)) }
            Button(language.text("cancel"), role: .cancel) {}
        }
    }
}
