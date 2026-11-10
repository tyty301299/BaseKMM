import SwiftUI
import Shared

struct StudioAskView: View {
    @ObservedObject var model: StudioModel
    let onService: () -> Void
    var body: some View {
        StudioPage(title: "Hỏi SHADOW", model: model) {
            Text("Bạn đang vướng ở đâu?").font(.largeTitle.bold())
            Text(model.state.currentStep).foregroundStyle(.secondary)
            TextField("Nhập câu hỏi", text: Binding(get: { model.state.question }, set: { model.send(StudioActionQuestion(value: $0)) }), axis: .vertical)
                .lineLimit(3...6).textFieldStyle(.roundedBorder)
            Text("\(model.state.question.count)/500").font(.caption).foregroundStyle(.secondary)
            LookPrimaryButton(title: "Xem gợi ý mẫu", disabled: model.state.question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.state.currentStep.isEmpty) { model.send(StudioActionAskSample.shared) }
            if !model.state.answer.isEmpty {
                Text(model.state.answer).padding(20).background(LookStyle.panel, in: RoundedRectangle(cornerRadius: 24))
            }
            Button("Mở dịch vụ AI thật", action: onService).frame(minHeight: 44)
        }
    }
}
