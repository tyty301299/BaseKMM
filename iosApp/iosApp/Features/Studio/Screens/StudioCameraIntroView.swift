import SwiftUI
import Shared

struct StudioCameraIntroView: View {
    @ObservedObject var model: StudioModel
    let forWardrobe: Bool
    let onContinue: () -> Void
    var body: some View {
        StudioPage(title: "Camera", model: model) {
            Image(systemName: "camera").font(.system(size: 72)).foregroundStyle(LookStyle.accent).padding(.vertical, 35)
            Text(forWardrobe ? "Cho SHADOW thấy món đồ." : "Thử trước. Lưu điều bạn thích.").font(.largeTitle.bold())
            Text("Camera trong bản UI này dùng hình nhân hoặc trang phục mẫu. Không xin quyền thiết bị, không chụp ảnh thật và không gửi ảnh lên máy chủ.")
            if forWardrobe {
                LookPrimaryButton(title: "Tiếp tục với áo mẫu") { model.send(StudioActionAddSample(preset: "coat")) }
            } else { LookPrimaryButton(title: "Mở camera demo", action: onContinue) }
        }
    }
}
