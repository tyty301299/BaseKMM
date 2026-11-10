import SwiftUI
import Shared

struct StudioComponentsView: View {
    @ObservedObject var model: StudioModel
    let onCamera: () -> Void
    var body: some View {
        StudioPage(title: "Thành phần UI", model: model) {
            Text("SHADOW / Mobile system").font(.largeTitle.bold())
            Text("Nội dung rõ ràng, kính ở lớp điều hướng.").foregroundStyle(.secondary)
            HStack { Image(systemName: "sparkles"); Text("Liquid Glass / Material") }
                .padding(24).frame(maxWidth: .infinity).modifier(LookGlass())
            LookPrimaryButton(title: "Thử màn camera", action: onCamera)
            ProgressView("Ví dụ trạng thái đang xử lý")
            Label("Dữ liệu mẫu, không phải AI", systemImage: "info.circle")
            Text("Các thành phần hỗ trợ chữ hệ thống và chế độ giảm độ trong suốt.").font(.caption)
        }
    }
}
