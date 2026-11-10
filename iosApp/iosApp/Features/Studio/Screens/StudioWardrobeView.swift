import SwiftUI
import Shared

struct StudioWardrobeView: View {
    @ObservedObject var model: StudioModel
    let onAdd: () -> Void
    let onItem: (String) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Tủ đồ của bạn.").font(.largeTitle.bold())
            Text("Trang phục mẫu lưu trên máy").foregroundStyle(.secondary)
            if model.state.loading { ProgressView("Đang mở tủ đồ…") }
            if let error = model.state.error {
                Text(error).foregroundStyle(.red)
                Button("Tải lại") { model.send(StudioActionRetry.shared) }
            }
            if !model.state.loading && model.state.data.items.isEmpty {
                Image(systemName: "tshirt").font(.system(size: 64)).foregroundStyle(.secondary).padding(.vertical, 28)
                Text("Tủ đồ đang chờ bạn.").font(.title2.bold())
                Text("Thêm món đầu tiên để thử luồng phối đồ.")
            }
            Picker("Nhóm trang phục", selection: Binding(get: { model.state.wardrobeFilter }, set: { model.send(StudioActionWardrobeFilter(value: $0)) })) {
                Text("Tất cả").tag("all"); Text("Áo").tag("Áo"); Text("Áo khoác").tag("Áo khoác")
            }.pickerStyle(.segmented).disabled(model.busy)
            if !model.state.data.items.isEmpty && model.state.filteredItems.isEmpty {
                Text("Chưa có trang phục trong nhóm này.").foregroundStyle(.secondary)
            }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 140))], spacing: 20) {
                ForEach(model.state.filteredItems, id: \.id) { item in
                    Button { onItem(item.id) } label: {
                        VStack(alignment: .leading, spacing: 10) {
                            LookMannequin(preset: item.preset).frame(height: 180).frame(maxWidth: .infinity)
                                .background(Color.gray.opacity(0.35), in: RoundedRectangle(cornerRadius: 24))
                            Text(item.name).font(.headline)
                            Text("\(item.category) · mẫu").font(.caption).foregroundStyle(.secondary)
                        }
                    }.buttonStyle(.plain)
                }
            }
            LookPrimaryButton(title: "Thêm trang phục", disabled: model.busy, action: onAdd)
        }
    }
}
