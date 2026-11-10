import SwiftUI
import Shared

struct StudioSettingsView: View {
    @ObservedObject var model: StudioModel
    let onService: () -> Void
    let onComponents: () -> Void
    @State private var confirm = false
    var body: some View {
        StudioPage(title: "Tùy chọn", model: model) {
            Text("Ít chi tiết. Đúng điểm nhấn.").font(.largeTitle.bold())
            Toggle("Giao diện tối", isOn: Binding(get: { model.state.data.settings.dark }, set: { model.send(StudioActionAppearance(dark: $0, glass: model.state.data.settings.glass)) }))
            Toggle("Hiệu ứng kính", isOn: Binding(get: { model.state.data.settings.glass }, set: { model.send(StudioActionAppearance(dark: model.state.data.settings.dark, glass: $0)) }))
            Text("Liquid Glass trên iOS 26+. iOS cũ dùng Material. Reduce Transparency ưu tiên nền đặc.").font(.caption).foregroundStyle(.secondary)
            LookPrimaryButton(title: "Kết nối dịch vụ SHADOW", action: onService)
            Button("Xem thành phần UI", action: onComponents).frame(minHeight: 44)
            Text("Dữ liệu local").font(.title2.bold())
            Text("Tủ mẫu, kỹ năng và lịch sử được lưu trên máy. Xóa dữ liệu không thể hoàn tác.")
            Button("Xóa toàn bộ dữ liệu mẫu", role: .destructive) { confirm = true }.frame(minHeight: 44)
        }
        .confirmationDialog("Xóa tủ mẫu, kỹ năng và lịch sử?", isPresented: $confirm, titleVisibility: .visible) {
            Button("Xóa dữ liệu mẫu", role: .destructive) { model.send(StudioActionClearData.shared) }
            Button("Hủy", role: .cancel) {}
        }
    }
}
