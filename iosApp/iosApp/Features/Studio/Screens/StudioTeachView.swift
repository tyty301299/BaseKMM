import SwiftUI
import Shared

struct StudioTeachView: View {
    @ObservedObject var model: StudioModel
    let onRecord: () -> Void
    var body: some View {
        StudioPage(title: "Chuẩn bị dạy", model: model) {
            Text("Cho AI thấy cách bạn làm.").font(.largeTitle.bold())
            StudioInstructionIllustration()
            Label("Đặt điện thoại cố định", systemImage: "1.circle")
            Label("Vừa làm vừa giải thích", systemImage: "2.circle")
            Label("Duyệt các bước trước khi lưu", systemImage: "3.circle")
            Text("Bài mẫu: lắp giá đỡ điện thoại. Chế độ demo không mở camera hoặc micro.").foregroundStyle(.secondary)
            LookPrimaryButton(title: "Bắt đầu thao tác mẫu", action: onRecord)
        }
    }
}
