import SwiftUI
import Shared

struct StudioItemView: View {
    @ObservedObject var model: StudioModel
    let itemID: String
    let onTry: (String) -> Void
    @State private var confirm = false
    var body: some View {
        StudioPage(title: "Chi tiết trang phục", model: model) {
            if let item = model.state.itemDraft, item.id == itemID,
               model.state.data.items.contains(where: { $0.id == itemID }) {
                LookMannequin(preset: item.preset).frame(height: 300).frame(maxWidth: .infinity)
                    .background(Color.gray.opacity(0.3), in: RoundedRectangle(cornerRadius: 28))
                TextField("Tên trang phục", text: Binding(get: { model.state.itemDraft?.name ?? "" }, set: { model.send(StudioActionItemName(value: $0)) }))
                    .textFieldStyle(.roundedBorder).accessibilityLabel("Tên trang phục")
                Picker("Nhóm trang phục", selection: Binding(get: { model.state.itemDraft?.category ?? "Áo" }, set: { model.send(StudioActionItemCategory(value: $0)) })) {
                    Text("Áo").tag("Áo"); Text("Áo khoác").tag("Áo khoác")
                }.pickerStyle(.segmented)
                Text("Trang phục mẫu").foregroundStyle(.secondary)
                LookPrimaryButton(title: "Lưu thay đổi", disabled: item.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) { model.send(StudioActionSaveItem.shared) }
                LookPrimaryButton(title: "Mở camera thử mẫu") { onTry(item.preset) }
                Button("Xóa trang phục", role: .destructive) { confirm = true }.frame(minHeight: 44)
            } else if !model.state.loading { Text("Không tìm thấy trang phục này.") }
        }
        .onAppear {
            if model.state.itemDraft?.id != itemID { model.send(StudioActionOpenItem(id: itemID)) }
        }
        .onChange(of: model.state.data.items.map(\.id)) { _ in
            if model.state.itemDraft?.id != itemID { model.send(StudioActionOpenItem(id: itemID)) }
        }
        .confirmationDialog("Xóa trang phục khỏi tủ đồ?", isPresented: $confirm, titleVisibility: .visible) {
            Button("Xóa", role: .destructive) { model.send(StudioActionDeleteItem(id: itemID)) }
            Button("Hủy", role: .cancel) {}
        }
    }
}
