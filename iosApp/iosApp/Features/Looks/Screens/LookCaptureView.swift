import SwiftUI
import Shared

struct LookCaptureView: View {
    @ObservedObject var model: LookModel
    let capture: SavedLook
    let onClose: () -> Void
    let onHistory: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack {
                    Text("Kết quả vừa chụp").font(.title2.bold())
                    Spacer()
                    Button("Đóng", action: onClose).disabled(model.state.saving)
                }
                LookMannequin(preset: capture.preset, before: capture.before, flipped: capture.flipped)
                    .frame(height: 380).frame(maxWidth: .infinity)
                    .background(Color(red: 0.81, green: 0.83, blue: 0.77), in: RoundedRectangle(cornerRadius: 28))
                Text(capture.title).font(.title.bold())
                Text("Kết quả mẫu · \(capture.occasion)").foregroundStyle(.secondary)
                Text("Lưu lựa chọn trên máy để xem lại. Đây không phải ảnh chụp hoặc ảnh AI.").font(.subheadline)
                if model.state.saving { ProgressView("Đang lưu trên máy…") }
                if let error = model.state.error { Text(error).foregroundStyle(.red) }
                if model.state.savedId == capture.id {
                    Label("Đã lưu trên máy", systemImage: "checkmark.circle")
                    LookPrimaryButton(title: "Xem Lịch sử", action: onHistory)
                } else {
                    LookPrimaryButton(title: model.state.saving ? "Đang lưu…" : "Lưu trên máy", disabled: model.state.saving) {
                        model.send(LookActionSaveCapture.shared)
                    }
                }
                Button("Chụp lại") { model.send(LookActionRetake.shared) }.frame(minHeight: 44).disabled(model.state.saving)
            }.padding(24).frame(maxWidth: 640).frame(maxWidth: .infinity)
        }.background(LookStyle.background.ignoresSafeArea())
    }
}
