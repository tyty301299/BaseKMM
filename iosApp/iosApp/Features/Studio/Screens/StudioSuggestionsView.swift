import SwiftUI
import Shared

struct StudioSuggestionsView: View {
    @ObservedObject var model: StudioModel
    let onSelect: (String) -> Void
    let onAdd: () -> Void
    var body: some View {
        StudioPage(title: "Kết quả phối đồ", model: model) {
            Text("Quen gu. Mới cách mặc.").font(.largeTitle.bold())
            if model.state.suggestions.isEmpty {
                Text("Chưa có bộ phối. Hãy thêm trang phục vào tủ.")
                LookPrimaryButton(title: "Thêm trang phục", action: onAdd)
            }
            ForEach(model.state.suggestions, id: \.itemId) { choice in
                VStack(alignment: .leading, spacing: 16) {
                    LookMannequin(preset: choice.preset).frame(height: 240).frame(maxWidth: .infinity)
                    Text(choice.title).font(.title2.bold())
                    Text(choice.reason).font(.subheadline).foregroundStyle(.secondary)
                    LookPrimaryButton(title: "Xem bộ phối") { onSelect(choice.preset) }
                }.padding(18).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 28))
            }
        }
    }
}
