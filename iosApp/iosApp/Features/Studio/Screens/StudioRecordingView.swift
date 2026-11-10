import SwiftUI
import Shared

struct StudioRecordingView: View {
    @ObservedObject var model: StudioModel
    let onCompile: () -> Void
    var body: some View {
        StudioPage(title: "Quay thao tác · demo", model: model) {
            VStack(spacing: 20) {
                StudioInstructionIllustration()
                Text(model.state.recording ? "Đang mô phỏng thao tác" : "Đoạn thao tác mẫu đã sẵn sàng").font(.headline)
                Text("Không có video hoặc âm thanh được ghi.").font(.caption)
            }.frame(maxWidth: .infinity, minHeight: 240).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 28))
            Text("Lời giải thích mẫu").font(.headline)
            TextField("Nội dung giải thích", text: Binding(get: { model.state.transcript }, set: { model.send(StudioActionTranscript(value: $0)) }), axis: .vertical)
                .lineLimit(3...6).textFieldStyle(.roundedBorder)
            Text("Nội dung này dùng duyệt flow; các bước tiếp theo là dữ liệu mẫu cố định.").font(.caption).foregroundStyle(.secondary)
            if model.state.recording {
                LookPrimaryButton(title: "Dừng thao tác mẫu") { model.send(StudioActionStopRecording.shared) }
            } else {
                LookPrimaryButton(title: "Xem AI chia bước · demo", disabled: !model.state.recorded, action: onCompile)
                Button("Làm mẫu lại") { model.send(StudioActionStartRecording.shared) }.frame(minHeight: 44)
            }
        }
    }
}
