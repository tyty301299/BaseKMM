import SwiftUI
import Shared

struct StudioAddItemView: View {
    @ObservedObject var model: StudioModel
    let onPermission: () -> Void
    var body: some View {
        StudioPage(title: "Thêm trang phục", model: model) {
            Text("Một món đồ.\nMột bức ảnh rõ.").font(.largeTitle.bold())
            Image(systemName: "tshirt").font(.system(size: 74)).frame(maxWidth: .infinity, minHeight: 160)
                .background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 28))
            Text("Bản này chỉ thêm mẫu có sẵn, chưa truy cập camera hoặc thư viện ảnh thật.").foregroundStyle(.secondary)
            LookPrimaryButton(title: "Xem luồng chụp ảnh", action: onPermission)
            Text("Hoặc chọn mẫu để lưu vào tủ đồ").font(.headline)
            LookPrimaryButton(title: "Thêm áo xanh rêu") { model.send(StudioActionAddSample(preset: "coat")) }
            LookPrimaryButton(title: "Thêm blouse cam đào") { model.send(StudioActionAddSample(preset: "blouse")) }
        }
    }
}
