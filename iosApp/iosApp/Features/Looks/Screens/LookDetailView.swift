import SwiftUI
import Shared

struct LookDetailView: View {
    @ObservedObject var model: LookModel
    let recordID: String
    let onRetry: () -> Void
    @State private var confirmDelete = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if let record = model.state.records.first(where: { $0.id == recordID }) {
                    Text(record.title).font(.largeTitle.bold())
                    LookMannequin(preset: record.preset, before: record.before, flipped: record.flipped)
                        .frame(height: 360).frame(maxWidth: .infinity)
                        .background(Color(red: 0.81, green: 0.83, blue: 0.77), in: RoundedRectangle(cornerRadius: 28))
                    Text("\(record.occasion) · hình nhân mẫu lưu trên máy")
                    LookPrimaryButton(title: "Thử lại", disabled: model.state.saving) {
                        model.send(LookActionReuse(id: recordID)); onRetry()
                    }
                    Button("Xóa khỏi Lịch sử", role: .destructive) { confirmDelete = true }
                        .frame(minHeight: 44).disabled(model.state.saving)
                } else if model.state.loading { ProgressView() }
                else { Text("Mục này không còn trong Lịch sử.") }
                if let error = model.state.error { Text(error).foregroundStyle(.red) }
                Button("Đóng") { dismiss() }.disabled(model.state.saving)
            }.padding(24).frame(maxWidth: 640).frame(maxWidth: .infinity)
        }
        .background(LookStyle.background.ignoresSafeArea()).tint(LookStyle.accent)
        .confirmationDialog("Xóa mục này? Không thể hoàn tác.", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Xóa", role: .destructive) { model.send(LookActionDelete(id: recordID)) }
            Button("Hủy", role: .cancel) {}
        }
        .interactiveDismissDisabled(model.state.saving)
    }
}
