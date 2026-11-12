import SwiftUI
import Shared

struct StudioSuggestionsView: View {
    @EnvironmentObject private var language: AppLanguage
    @ObservedObject var model: StudioModel
    let onSelect: (String) -> Void
    let onAdd: () -> Void
    var body: some View {
        StudioPage(title: language.text("ui_097"), model: model) {
            Text(language.text("ui_150")).font(.largeTitle.bold())
            if model.state.suggestions.isEmpty {
                Text(language.text("ui_036"))
                LookPrimaryButton(title: language.text("add_garment"), action: onAdd)
            }
            ForEach(model.state.suggestions, id: \.itemId) { choice in
                VStack(alignment: .leading, spacing: 16) {
                    LookMannequin(preset: choice.preset).frame(height: 240).frame(maxWidth: .infinity)
                    Text(choice.title).font(.title2.bold())
                    Text(choice.reason).font(.subheadline).foregroundStyle(.secondary)
                    LookPrimaryButton(title: language.text("ui_194")) { onSelect(choice.preset) }
                }.padding(18).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 28))
            }
        }
    }
}
