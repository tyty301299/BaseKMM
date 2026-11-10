import SwiftUI
import Shared

struct StudioCompileView: View {
    @ObservedObject var model: StudioModel
    let onReview: () -> Void
    var body: some View {
        StudioPage(title: "AI chia bước · demo", model: model) {
            Image(systemName: "sparkles").font(.system(size: 68)).foregroundStyle(LookStyle.accent).padding(.vertical, 35)
            Text(model.busy ? "Đang chuẩn bị bài mẫu…" : "Từng bước đã rõ hơn.").font(.largeTitle.bold())
            Text("Các bước minh họa cho bài lắp giá đỡ điện thoại. Chưa phân tích video bằng AI.").foregroundStyle(.secondary)
            if !model.state.draftSteps.isEmpty { LookPrimaryButton(title: "Duyệt các bước", action: onReview) }
            else if !model.busy { LookPrimaryButton(title: "Chuẩn bị lại bài mẫu") { model.send(StudioActionCompileSample.shared) } }
        }
    }
}
