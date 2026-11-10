import SwiftUI
import Shared

struct StudioGeneratingView: View {
    @ObservedObject var model: StudioModel
    let onResults: () -> Void
    let onRetry: () -> Void
    var body: some View {
        StudioPage(title: "Chuẩn bị bộ phối", model: model) {
            Image(systemName: "sparkles").font(.system(size: 64)).foregroundStyle(LookStyle.accent).padding(.vertical, 40)
            Text(model.busy ? "Đang chuẩn bị…" : "Bộ phối mẫu đã sẵn sàng.").font(.largeTitle.bold())
            Text("Demo từ những món đang có trong tủ. Không phải kết quả AI.").foregroundStyle(.secondary)
            if model.state.suggested { LookPrimaryButton(title: "Xem bộ phối", action: onResults) }
            else if !model.busy { LookPrimaryButton(title: "Thử lại", action: onRetry) }
        }
    }
}
